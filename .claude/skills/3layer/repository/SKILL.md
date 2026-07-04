---
name: persistence
description: |
  Spring Data JPA / QueryDSL 기반 영속 레이어(Repository, 쿼리 메서드, 엔티티 매핑)를
  새로 만들거나 수정할 때 적용한다. 쿼리를 이름 메서드로 둘지 `@Query`로 둘지,
  언제 QueryDSL Custom Repository로 분리할지, N+1을 어떻게 막을지, 엔티티의
  연관관계·FetchType·인덱스를 어떻게 매핑할지를 다룬다.

  순수 Java 가독성(이름, 메서드 길이 등)은 readable-java 스킬을 함께 따른다.
  엔티티 안에 들어가는 비즈니스 로직(상태 변경 메서드 등)은 oop-java 스킬을 따른다.

  단, `src/test/java` 하위 테스트 코드에서는 repository-test 스킬이 우선한다.
---

# 영속 레이어 작성 규칙

이 스킬은 **DB와 도메인 사이의 경계**를 다룰 때의 규칙을 정의한다. 대상은 세 가지다:
Repository 인터페이스, 그 안의 쿼리(이름 메서드 / `@Query` / QueryDSL), 그리고 엔티티 매핑.

핵심 질문은 하나다: **"이 쿼리를 6개월 뒤의 내가 한 번에 읽을 수 있는가?"**
읽다가 멈칫하면 표현 방식을 바꿀 신호다(이름 메서드 → `@Query` → QueryDSL).

> 코드 예제는 모두 같은 패키지의 **`reference.md`** 에 있다. 각 규칙 옆의
> "→ reference.md §N" 표시를 따라 해당 예제를 확인한다.

---

## 0. Repository의 책임 경계 — "무엇만 하는가"

Repository는 **영속성 접근만** 담당한다. 즉 "어떻게 저장하고 어떻게 조회하는가"까지다.

### 0-1. Repository가 절대 하지 않는 것

- **비즈니스 판단** — 조회한 결과로 "재고가 충분한가", "할인 대상인가"를 따지지 않는다.
  이는 Service/도메인의 몫이다. Repository는 조건에 맞는 데이터를 돌려줄 뿐이다.
- **DTO 가공/조립** — 여러 조회 결과를 조합해 화면용 응답을 만드는 일은 Service가 한다.
  (단, 조회 성능을 위한 **DTO Projection 쿼리**는 예외 — §4-3.)
- **트랜잭션 경계 정의** — `@Transactional`의 주된 경계는 Service에 둔다. Repository는
  스프링이 제공하는 기본 트랜잭션 위에서 동작한다.

### 0-2. 판단 기준

> Repository 메서드 본문(또는 Custom 구현)에서 **비즈니스 `if` 분기**나
> **여러 쿼리 결과를 엮는 조립 로직**이 보이면 Service로 올려보낼 신호다.

---

## 1. 쿼리 표현 방식 선택 — 이 스킬의 핵심

조회 메서드를 만들 때 **아래 순서로 판단**한다. 위에서 걸리면 거기서 멈춘다.

```
조건 1~2개 & 단일 엔티티 범위 & 이름 20자 미만   → 이름 메서드        (§2)
       ↓ (걸리면)
정적 조건이지만 위 한도를 넘음                    → @Query (JPQL)      (§3)
       ↓ (걸리면)
동적 조건 / 복잡한 조인·집계 / 타입 안전성 필요    → QueryDSL Custom    (§4)
```

이 순서의 원칙은 **"가장 단순한 표현으로 의도가 드러나면 거기서 멈춘다"** 이다.
QueryDSL이 강력하다고 1~2개짜리 조회까지 QueryDSL로 짜면 오히려 읽기 어려워진다.

---

## 2. 이름 메서드(Query Method)

### 2-1. 사용 조건

다음을 **모두** 만족할 때만 이름 메서드로 둔다.

- **where 조건이 1~2개**다. (`And`/`Or`로 묶이는 절이 2개 이하)
- **단일 엔티티 범위**다. 연관 엔티티를 타고 들어가는 경로 표현(`findByOrderProductName`)이
  없다. 조인이 필요하면 이름 메서드를 쓰지 않는다(§2-3).
- **메서드 이름 전체가 20자 미만**이다. ← 글자 수는 `findAllBy`를 **포함한 전체 이름**으로
  센다. 예: `findAllBySellingStatusIn`은 24자이므로 한도 초과 → `@Query`로 간다.

→ 허용/초과 경계 예제: reference.md §2-1

> 20자는 **딱딱한 컷오프가 아니라 "한눈에 안 읽히기 시작하는 지점"의 기준선**이다.
> 글자 수가 19자여도 의도가 안 읽히면 `@Query`로, 21자여도 누가 봐도 자명하면
> 이름 메서드로 둘 수 있다. 다툼이 생기면 "읽히는가"가 최종 판단 기준이다.

### 2-2. 네이밍 규칙

- 단건은 `findBy...`, 다건은 `findAllBy...`, 존재 여부는 `existsBy...`, 개수는 `countBy...`.
- `Optional<T>`로 받을 수 있는 단건 조회는 `Optional`을 반환해 null 처리를 호출부로 미루지 않는다.
- 키워드 조합은 최소화한다. `GreaterThanEqual`, `Between`, `In`, `OrderBy...Desc` 정도까지만
  이름에 담고, 그 이상 길어지면 §3으로 넘어간다.

→ 반환 타입별 네이밍 예제: reference.md §2-2

### 2-3. 이름 메서드로 두지 말아야 할 신호

- 조인이 필요하다 (연관 엔티티 조건).
- 조건이 3개 이상이다.
- 정렬·그룹핑이 이름에 얹혀 이름이 한 줄로 안 읽힌다.
- 같은 엔티티에 비슷한 이름 메서드가 5개 넘게 쌓이기 시작했다.

→ 이런 경우는 §3(@Query) 또는 §4(QueryDSL)로 옮긴다.

---

## 3. `@Query` (JPQL)

### 3-1. 사용 조건

**조건은 정적(컴파일 시점에 고정)인데, 이름 메서드 한도(§2-1)를 넘는** 경우에 쓴다.
대표적으로 조건 3개 이상, 단순 조인, 이름이 20자를 넘어 의도가 안 읽히는 경우다.

→ 이름 메서드 → @Query 전환 예제: reference.md §3-1

### 3-2. 작성 규칙

- **JPQL을 기본**으로 한다. native query는 JPQL로 표현 불가능한 경우(DB 전용 함수,
  대량 upsert 등)에만 쓰고, `nativeQuery = true`를 명시한다.
- 파라미터는 **이름 기반 바인딩**(`:status`)을 쓰고 `@Param`으로 묶는다. 위치 기반(`?1`) 금지
  — 파라미터 순서가 바뀌면 조용히 깨진다.
- 변경 쿼리(update/delete)는 `@Modifying`을 붙이고, 실행 후 영속성 컨텍스트와 DB가
  어긋날 수 있으므로 `clearAutomatically`/`flushAutomatically`를 의식해서 설정한다.
- 쿼리 문자열이 길어지면 줄바꿈으로 `select` / `from` / `where` / `order by`를 분리해
  가독성을 확보한다.

→ 이름 바인딩·@Modifying 예제: reference.md §3-2

### 3-3. @Query로도 버거운 신호

- `where` 절에 **런타임에 따라 들어가고 빠지는 조건**(동적 조건)이 있다 → 문자열 분기나
  여러 개의 비슷한 `@Query`로 풀려 하지 말 것. **QueryDSL로 간다(§4).**
- 조인이 2단 이상으로 깊거나 집계·서브쿼리가 얽힌다 → QueryDSL.

---

## 4. QueryDSL Custom Repository

### 4-1. 사용 조건

다음 중 하나라도 해당하면 QueryDSL로 간다.

- **동적 조건** — 검색 필터처럼 조건이 있을 때만 `where`에 붙어야 하는 경우.
- **복잡한 조인/집계/서브쿼리** — JPQL 문자열로는 의도가 안 읽히는 경우.
- **타입 안전성이 중요한 쿼리** — 컴파일 시점에 필드명·타입 오류를 잡고 싶은 경우.

### 4-2. 구조 — 인터페이스 분리 패턴

QueryDSL 구현은 Spring Data가 생성하는 Repository와 **하나의 빈으로 합쳐지도록** 구성한다.

```
ProductRepository            (interface) extends JpaRepository, ProductRepositoryCustom
ProductRepositoryCustom      (interface) ← QueryDSL 메서드 시그니처 선언
ProductRepositoryCustomImpl  (class)     ← JPAQueryFactory로 구현 (이름 규칙: Custom + Impl)
```

- 구현 클래스 이름은 **반드시 `{Custom인터페이스}Impl`** 형태여야 Spring Data가 자동으로
  엮어준다. (이 네이밍이 깨지면 빈이 안 만들어진다.)
- `JPAQueryFactory`는 **`config`에서 `@Bean`으로 등록**해 주입받는다. 구현 클래스 안에서
  `new JPAQueryFactory(em)`로 만들지 않는다. (자원 소유 규칙 — controller 스킬 §8과 동일 원칙.)

→ 전체 구조 예제: reference.md §4-2

### 4-3. 작성 규칙

- **동적 조건은 `BooleanExpression`을 반환하는 private 메서드로 쪼갠다.** 조건이 null이면
  `where`에서 자동으로 무시되는 성질을 이용해, 각 조건을 작은 메서드로 분리하고 `where`에
  나열한다. 거대한 `BooleanBuilder` 하나에 if로 쌓지 않는다.
- 조회 전용 응답이 필요하면 **DTO Projection**(`Projections.constructor` 또는 `@QueryProjection`)으로
  필요한 컬럼만 뽑는다. 엔티티 전체를 가져와 Service에서 매핑하는 것보다 낫다.
  (이 DTO는 표현용이 아니라 "조회 결과 묶음"이다 — §0-1의 예외.)
- 페이징은 `fetch()` + 별도 count 쿼리로 직접 구성하거나 `PageableExecutionUtils`를 쓴다.

→ BooleanExpression 분리·DTO Projection 예제: reference.md §4-3

---

## 5. N+1 문제와 fetch join

연관 엔티티를 **반복 접근**하는 순간, JPA는 매 건마다 추가 쿼리를 날린다(N+1).
조회 메서드를 만들 때는 **"이 결과를 받은 쪽이 연관 엔티티를 건드리는가"** 를 먼저 묻는다.

### 5-1. 컬렉션이 아닌 연관(ToOne) — fetch join 또는 @EntityGraph

`@ManyToOne`, `@OneToOne`을 함께 조회해야 하면 `join fetch`(JPQL) 또는 `@EntityGraph`로
한 방에 가져온다.

→ fetch join / @EntityGraph 예제: reference.md §5-1

### 5-2. 컬렉션 연관(ToMany) — 주의

- 컬렉션 fetch join은 **페이징과 함께 쓰면 안 된다.** (메모리에서 페이징하며 경고 로그가
  뜨고 위험하다.) 페이징이 필요하면 `@BatchSize`(또는 `default_batch_fetch_size`)로
  IN 쿼리 묶음 조회를 쓴다.
- 컬렉션 fetch join을 **둘 이상** 동시에 하지 않는다 (카테시안 곱 폭발).

→ BatchSize 전략 예제: reference.md §5-2

### 5-3. 판단 기준

> 조회 후 루프 안에서 `order.getMember().getName()`처럼 연관을 타고 들어가는 코드가
> 보이면 N+1 신호다. fetch join / `@EntityGraph` / `@BatchSize` 중 하나로 막는다.

---

## 6. 엔티티 매핑

### 6-1. 기본 어노테이션

- `@Entity` + `@Table(name = ...)`. 기본 생성자는 `@NoArgsConstructor(access = PROTECTED)`로
  열어 두되 외부 직접 호출은 막는다 (JPA 프록시·리플렉션용).
- `@Setter`는 클래스 레벨에 두지 않는다. 상태 변경은 의미 있는 도메인 메서드로 표현한다
  (oop-java 스킬). 객체 생성은 빌더 또는 정적 팩토리 메서드로 한다.
- 식별자는 `@Id @GeneratedValue`. 전략(IDENTITY/SEQUENCE)은 DB에 맞춰 명시한다.

→ 엔티티 기본 골격 예제: reference.md §6-1

### 6-2. 연관관계 매핑

- **연관관계의 주인은 외래 키를 가진 쪽**(보통 `@ManyToOne`)이다. 반대편 `@OneToMany`에는
  `mappedBy`를 두고, 가능하면 단방향으로 시작해 필요할 때만 양방향으로 넓힌다.
- **`fetch`는 모든 연관에서 `LAZY`를 기본**으로 한다. `@ManyToOne`/`@OneToOne`의 기본값은
  EAGER이므로 **명시적으로 `fetch = LAZY`를 적는다.** 즉시 로딩이 필요한 자리는
  조회 시점의 fetch join(§5)으로 해결한다.
- 양방향이면 연관관계 편의 메서드를 두되, 거기 비즈니스 판단을 넣지 않는다.

→ 연관관계 매핑·LAZY 명시 예제: reference.md §6-2

### 6-3. 인덱스와 제약조건

- 자주 조회 조건이 되는 컬럼(외래 키, 검색 키)은 `@Table(indexes = @Index(...))`로
  인덱스를 명시한다. 인덱스는 **쿼리 패턴을 따라** 만든다 — 조회하지도 않는 컬럼에
  인덱스를 거는 건 쓰기 비용만 늘린다.
- 유니크 제약은 `@Table(uniqueConstraints = ...)` 또는 컬럼의 `unique = true`로 건다.
  비즈니스 "중복 여부"는 이 제약 + Service의 사전 검증을 함께 둔다.

→ 인덱스·유니크 제약 예제: reference.md §6-3

### 6-4. 공통 매핑은 베이스 엔티티로

- 생성/수정 시각 같은 공통 컬럼은 `@MappedSuperclass` + JPA Auditing(`@CreatedDate`,
  `@LastModifiedDate`)으로 베이스 엔티티에 모은다. 엔티티마다 반복하지 않는다.

→ BaseEntity + Auditing 예제: reference.md §6-4

---

## 7. 작성 체크리스트

영속 레이어 코드를 작성/수정한 뒤 점검한다.

### 쿼리 표현 선택

- [ ] 조건 1~2개 & 단일 엔티티 & 이름 전체 20자 미만일 때만 이름 메서드를 썼는가?
- [ ] 이름 메서드 한도를 넘는데 정적 조건이면 `@Query`(JPQL)로 옮겼는가?
- [ ] 동적 조건 / 복잡 조인 / 타입 안전성이 필요하면 QueryDSL Custom으로 갔는가?
- [ ] "QueryDSL이 강력하니까" 1~2개짜리 단순 조회까지 QueryDSL로 짜지 않았는가?

### 이름 메서드 / @Query

- [ ] 단건은 `Optional`, 다건은 `findAllBy`, 존재는 `existsBy`로 네이밍했는가?
- [ ] `@Query` 파라미터를 이름 기반(`:name` + `@Param`)으로 바인딩했는가? (위치 기반 금지)
- [ ] 변경 쿼리에 `@Modifying`을 붙였는가?

### QueryDSL

- [ ] Custom 구현 클래스 이름이 `{Custom인터페이스}Impl` 규칙을 따르는가?
- [ ] `JPAQueryFactory`를 `config`의 빈으로 주입받는가? (`new`로 만들지 않음)
- [ ] 동적 조건을 `BooleanExpression` 반환 메서드로 쪼갰는가? (거대 `BooleanBuilder` 금지)

### N+1

- [ ] 조회 결과에서 연관을 타고 들어가는데 fetch join / `@EntityGraph` / `@BatchSize`로 막았는가?
- [ ] 컬렉션 fetch join을 페이징과 함께 쓰거나, 둘 이상 동시에 하지 않았는가?

### 엔티티 매핑

- [ ] 기본 생성자가 `@NoArgsConstructor(access = PROTECTED)`인가?
- [ ] 모든 연관에 `fetch = LAZY`를 명시했는가? (특히 ToOne)
- [ ] 클래스 레벨 `@Setter` 없이 도메인 메서드로 상태를 바꾸는가?
- [ ] 조회 패턴에 맞는 인덱스/유니크 제약을 명시했는가?
- [ ] 생성·수정 시각 등 공통 컬럼을 BaseEntity + Auditing으로 모았는가?

### 책임 경계

- [ ] Repository에 비즈니스 분기나 DTO 조립 로직이 없는가? (Service로 이동)
