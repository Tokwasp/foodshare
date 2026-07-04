# 영속 레이어 코드 예제 (reference)

SKILL.md의 각 규칙에서 "→ reference.md §N"으로 안내하는 코드 예제 모음이다.
패키지·클래스명은 예시이며, 실제 프로젝트 구조에 맞춰 바꿔 쓴다.

---

## §2-1. 이름 메서드 — 허용/초과 경계

글자 수는 `findAllBy`를 **포함한 전체 이름**으로 센다.

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ✅ 허용 — 전체 이름 16자, 조건 1개, 단일 엔티티
    Optional<Product> findByProductNumber(String productNumber);

    // ✅ 허용 — 전체 이름 13자, 조건 1개
    List<Product> findByType(ProductType type);

    // ⚠️ 경계 — findAllBySellingStatusIn = 24자 → 한도 초과
    //    의도(IN 조회)는 자명하지만 20자 컷오프를 넘으므로 @Query로 옮긴다. (§3-1 참고)
    // List<Product> findAllBySellingStatusIn(List<ProductSellingStatus> statuses);

    // ❌ 금지 — 조인이 끼는 경로 표현 (단일 엔티티 범위를 벗어남)
    // List<Product> findByOrderProductProductNumber(String productNumber);
}
```

판단 순서: **조건 개수(1~2) → 단일 엔티티 여부 → 이름 전체 20자** 를 차례로 본다.
하나라도 걸리면 §3 또는 §4로 간다.

---

## §2-2. 반환 타입별 네이밍

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // 단건 — null 가능성을 호출부로 미루지 않도록 Optional
    Optional<Product> findByProductNumber(String productNumber);

    // 다건 — findAllBy
    List<Product> findAllByType(ProductType type);

    // 존재 여부 — existsBy (count보다 가볍다)
    boolean existsByProductNumber(String productNumber);

    // 개수 — countBy
    long countByType(ProductType type);
}
```

---

## §3-1. 이름 메서드 → @Query 전환

§2-1에서 한도를 넘은 `findAllBySellingStatusIn`을 JPQL로 옮긴 모습이다.

```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // before(금지): List<Product> findAllBySellingStatusIn(List<ProductSellingStatus> statuses);

    // after: 메서드 이름은 짧게, 조건은 JPQL로
    @Query("select p from Product p where p.sellingStatus in :statuses")
    List<Product> findAllBySellingStatusesIn(@Param("statuses") List<ProductSellingStatus> statuses);
}
```

조건이 3개로 늘어난 정적 쿼리 예:

```java
@Query("""
        select p
        from Product p
        where p.type = :type
          and p.sellingStatus = :status
          and p.price <= :maxPrice
        order by p.price asc
        """)
List<Product> findSellingProducts(@Param("type") ProductType type,
                                  @Param("status") ProductSellingStatus status,
                                  @Param("maxPrice") int maxPrice);
```

---

## §3-2. 이름 바인딩 · @Modifying

```java
public interface StockRepository extends JpaRepository<Stock, Long> {

    // ✅ 이름 기반 바인딩 (:numbers) — 위치 기반 ?1 금지
    @Query("select s from Stock s where s.productNumber in :numbers")
    List<Stock> findAllByProductNumberIn(@Param("numbers") List<String> numbers);

    // 변경 쿼리는 @Modifying. 실행 후 1차 캐시와 DB가 어긋나지 않도록 clear 옵션을 의식한다.
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.sellingStatus = :status where p.type = :type")
    int updateSellingStatusByType(@Param("type") ProductType type,
                                  @Param("status") ProductSellingStatus status);
}
```

---

## §4-2. QueryDSL Custom Repository — 인터페이스 분리 구조

```java
// 1) Spring Data Repository — 기본 메서드 + Custom 인터페이스를 함께 상속
public interface ProductRepository
        extends JpaRepository<Product, Long>, ProductRepositoryCustom {
}

// 2) Custom 인터페이스 — QueryDSL로 구현할 메서드 시그니처만 선언
public interface ProductRepositoryCustom {
    List<Product> search(ProductSearchCondition condition);
}

// 3) 구현 클래스 — 이름은 반드시 {Custom인터페이스}Impl
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;  // config에서 @Bean으로 주입 (new 금지)

    @Override
    public List<Product> search(ProductSearchCondition condition) {
        return queryFactory
                .selectFrom(product)
                .where(
                        typeEq(condition.getType()),
                        statusEq(condition.getStatus())
                )
                .fetch();
    }
    // ... 동적 조건 메서드는 §4-3
}
```

`JPAQueryFactory` 빈 등록 (config):

```java
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager em) {
        return new JPAQueryFactory(em);
    }
}
```

---

## §4-3. 동적 조건 분리 · DTO Projection

**거대한 BooleanBuilder에 if로 쌓지 말고**, 조건마다 `BooleanExpression`을 반환하는
private 메서드로 쪼갠다. null을 반환하면 `where`에서 자동으로 무시된다.

```java
@Override
public List<Product> search(ProductSearchCondition condition) {
    return queryFactory
            .selectFrom(product)
            .where(
                    typeEq(condition.getType()),
                    statusEq(condition.getStatus()),
                    priceLoe(condition.getMaxPrice())
            )
            .fetch();
}

private BooleanExpression typeEq(ProductType type) {
    return type != null ? product.type.eq(type) : null;       // null → where에서 무시
}

private BooleanExpression statusEq(ProductSellingStatus status) {
    return status != null ? product.sellingStatus.eq(status) : null;
}

private BooleanExpression priceLoe(Integer maxPrice) {
    return maxPrice != null ? product.price.loe(maxPrice) : null;
}
```

DTO Projection — 필요한 컬럼만 뽑아 조회 전용 객체로 받는다.

```java
public List<ProductView> searchView(ProductSearchCondition condition) {
    return queryFactory
            .select(Projections.constructor(ProductView.class,
                    product.productNumber,
                    product.name,
                    product.price))
            .from(product)
            .where(typeEq(condition.getType()))
            .fetch();
}
```

---

## §5-1. ToOne 연관 — fetch join / @EntityGraph

JPQL fetch join:

```java
@Query("select o from Order o join fetch o.member where o.status = :status")
List<Order> findAllWithMemberByStatus(@Param("status") OrderStatus status);
```

`@EntityGraph` — 이름 메서드에 즉시 로딩을 얹고 싶을 때:

```java
@EntityGraph(attributePaths = {"member"})
List<Order> findAllByStatus(OrderStatus status);
```

둘 다 결과를 받은 쪽에서 `order.getMember().getName()`을 호출해도 추가 쿼리가 안 나간다.

---

## §5-2. 컬렉션 연관 — BatchSize 전략

컬렉션 fetch join은 페이징과 함께 쓰면 안 되므로, IN 묶음 조회로 N+1을 줄인다.

```java
// application.yml — 전역 설정
// spring:
//   jpa:
//     properties:
//       hibernate:
//         default_batch_fetch_size: 100
```

특정 연관에만 적용:

```java
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@BatchSize(size = 100)
private List<OrderProduct> orderProducts = new ArrayList<>();
```

이렇게 하면 컬렉션 초기화 시 건별 쿼리 대신 `in (...)` 한 방으로 묶여 나간다.

---

## §6-1. 엔티티 기본 골격

```java
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // 외부 직접 생성 차단, JPA용으로만 개방
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String productNumber;

    @Enumerated(EnumType.STRING)                      // ordinal 금지 — 순서 바뀌면 데이터 깨짐
    private ProductType type;

    private int price;

    @Builder
    private Product(String productNumber, ProductType type, int price) {
        this.productNumber = productNumber;
        this.type = type;
        this.price = price;
    }
    // 상태 변경은 클래스 @Setter가 아니라 의미 있는 도메인 메서드로 (oop-java)
}
```

---

## §6-2. 연관관계 매핑 · LAZY 명시

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderProduct {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ToOne 기본값은 EAGER → 반드시 LAZY를 명시
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 주인은 OrderProduct.order. 반대편은 mappedBy로 읽기 전용
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderProduct> orderProducts = new ArrayList<>();
}
```

---

## §6-3. 인덱스 · 유니크 제약

```java
@Entity
@Table(
        name = "product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_number",
                columnNames = "productNumber"),
        indexes = {
                @Index(name = "idx_product_type", columnList = "type"),
                @Index(name = "idx_product_status", columnList = "sellingStatus")
        }
)
public class Product {
    // 인덱스는 실제 조회 조건(type, sellingStatus)을 따라 만든다.
    // 조회하지 않는 컬럼에 인덱스를 거는 것은 쓰기 비용만 늘린다.
}
```

---

## §6-4. BaseEntity + JPA Auditing

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;
}

// 엔티티는 extends BaseEntity 만 하면 공통 컬럼을 물려받는다.
@Entity
public class Product extends BaseEntity {
    // ...
}
```

Auditing 활성화 (메인 설정 클래스 또는 별도 config):

```java
@EnableJpaAuditing
@Configuration
public class JpaAuditingConfig {
}
```
