# Repository 테스트 코드 예시

이 문서는 SKILL.md의 규칙이 어떻게 적용되는지를 보여주는 정본 예시(canonical examples)
모음이다.

---

## 0. 통합 테스트 상위 클래스

모든 Repository 테스트는 이 클래스를 상속한다.

```java
package sample.cafekiosk.spring;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import sample.cafekiosk.spring.client.mail.MailSendClient;

@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {

    @MockBean
    protected MailSendClient mailSendClient;

}
```

**포인트**

- `@SpringBootTest`로 전체 컨텍스트를 띄운다. `@DataJpaTest`를 별도로 쓰지 않는다.
- `@ActiveProfiles("test")`로 test 프로파일 활성화.
- 외부 시스템 의존성(메일 발송 등)은 여기서 `@MockBean`으로 한 번만 선언한다.

---

## 1. 기본 패턴 — `findAllBySellingStatusIn`

판매 상태로 필터링하는 메서드 네이밍 쿼리를 검증한다.

### 검증 대상 (Repository)

```java
package sample.cafekiosk.spring.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * select *
     * from product
     * where selling_status in ('SELLING', 'HOLD');
     */
    List<Product> findAllBySellingStatusIn(List<ProductSellingStatus> sellingStatuses);

    List<Product> findAllByProductNumberIn(List<String> productNumbers);

    @Query(value = "select p.product_number from product p order by id desc limit 1", nativeQuery = true)
    String findLatestProductNumber();

}
```

### 테스트 코드

```java
package sample.cafekiosk.spring.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import sample.cafekiosk.spring.IntegrationTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sample.cafekiosk.spring.domain.product.ProductSellingStatus.*;
import static sample.cafekiosk.spring.domain.product.ProductType.HANDMADE;

@Transactional
class ProductRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ProductRepository productRepository;

    @DisplayName("원하는 판매상태를 가진 상품들을 조회한다.")
    @Test
    void findAllBySellingStatusIn() {
        // given
        Product product1 = createProduct("001", HANDMADE, SELLING, "아메리카노", 4000);
        Product product2 = createProduct("002", HANDMADE, HOLD, "카페라떼", 4500);
        Product product3 = createProduct("003", HANDMADE, STOP_SELLING, "팥빙수", 7000);
        productRepository.saveAll(List.of(product1, product2, product3));

        // when
        List<Product> products = productRepository.findAllBySellingStatusIn(List.of(SELLING, HOLD));

        // then
        assertThat(products).hasSize(2)
                .extracting("productNumber", "name", "sellingStatus")
                .containsExactlyInAnyOrder(
                        tuple("001", "아메리카노", SELLING),
                        tuple("002", "카페라떼", HOLD)
                );
    }

    private Product createProduct(String productNumber, ProductType type, ProductSellingStatus sellingStatus, String name, int price) {
        return Product.builder()
                .productNumber(productNumber)
                .type(type)
                .sellingStatus(sellingStatus)
                .name(name)
                .price(price)
                .build();
    }

}
```

### 무엇을 보여주는가

- `extends IntegrationTestSupport`로 통합 테스트 컨텍스트 재사용
- 클래스 레벨 `@Transactional`로 테스트 간 격리
- enum static import (`SELLING`, `HOLD`, `STOP_SELLING`, `HANDMADE`)
- 픽스처 3개 중 **2개만 매칭되도록** 의도적으로 구성 (`STOP_SELLING`은 비매칭) →
  필터링이 실제 작동하는지 검증할 수 있다
- `hasSize(2)` → `extracting(...)` → `containsExactlyInAnyOrder(...)` 패턴
- 헬퍼 메서드 `createProduct`를 클래스 맨 아래에 배치

---

## 2. 단순 IN 절 쿼리 — `findAllByProductNumberIn`

```java
@DisplayName("상품번호 리스트로 상품들을 조회한다.")
@Test
void findAllByProductNumberIn() {
    // given
    Product product1 = createProduct("001", HANDMADE, SELLING, "아메리카노", 4000);
    Product product2 = createProduct("002", HANDMADE, HOLD, "카페라떼", 4500);
    Product product3 = createProduct("003", HANDMADE, STOP_SELLING, "팥빙수", 7000);
    productRepository.saveAll(List.of(product1, product2, product3));

    // when
    List<Product> products = productRepository.findAllByProductNumberIn(List.of("001", "002"));

    // then
    assertThat(products).hasSize(2)
            .extracting("productNumber", "name", "sellingStatus")
            .containsExactlyInAnyOrder(
                    tuple("001", "아메리카노", SELLING),
                    tuple("002", "카페라떼", HOLD)
            );
}
```

### 무엇을 보여주는가

- 1번 예시와 픽스처 구조가 동일 — **같은 도메인의 Repository 테스트는 픽스처 구성을 일관되게**
  가져가면 가독성이 올라간다.
- 검증 키 컬럼만 바뀐다 (`sellingStatus` → `productNumber` 기준 조회).

---

## 3. Native Query + 정상 케이스 — `findLatestProductNumber`

```java
@DisplayName("가장 마지막으로 저장한 상품의 상품번호를 읽어온다.")
@Test
void findLatestProductNumber() {
    // given
    String targetProductNumber = "003";

    Product product1 = createProduct("001", HANDMADE, SELLING, "아메리카노", 4000);
    Product product2 = createProduct("002", HANDMADE, HOLD, "카페라떼", 4500);
    Product product3 = createProduct(targetProductNumber, HANDMADE, STOP_SELLING, "팥빙수", 7000);
    productRepository.saveAll(List.of(product1, product2, product3));

    // when
    String latestProductNumber = productRepository.findLatestProductNumber();

    // then
    assertThat(latestProductNumber).isEqualTo(targetProductNumber);
}
```

### 무엇을 보여주는가

- 기대값을 **`targetProductNumber` 변수에 담아** given에서 then까지 연결되는 흐름을
  명시적으로 보여준다. 매직 스트링 `"003"`을 then에서 다시 쓰지 않는다.
- native query가 `order by id desc limit 1`을 실제로 따르는지 검증할 수 있도록
  **3개의 데이터를 순서대로** 저장한다.
- 단일 값이므로 `isEqualTo`를 사용한다.

---

## 4. 엣지 케이스 — `findLatestProductNumberWhenProductIsEmpty`

```java
@DisplayName("가장 마지막으로 저장한 상품의 상품번호를 읽어올 때, 상품이 하나도 없는 경우에는 null을 반환한다.")
@Test
void findLatestProductNumberWhenProductIsEmpty() {
    // when
    String latestProductNumber = productRepository.findLatestProductNumber();

    // then
    assertThat(latestProductNumber).isNull();
}
```

### 무엇을 보여주는가

- **빈 결과 케이스를 별도 테스트로 분리**. 정상 케이스와 같은 테스트에 묶지 않는다.
- given이 비어 있으므로 `// given` 주석을 생략하고 `// when`부터 시작한다.
- 메서드명: `findLatestProductNumber` + `WhenProductIsEmpty` — 의도를 덧붙인다.
- `@DisplayName`은 **"~할 때, ~한다"** 패턴.
- `isNull()`로 null 검증.

---

## 5. 정적 팩토리 메서드를 사용하는 도메인 — `StockRepositoryTest`

`Stock`은 `static Stock.create(...)` 정적 팩토리 메서드를 제공한다. 이 경우
테스트 클래스에 별도의 헬퍼 메서드를 두지 않고 정적 팩토리 메서드를 그대로 사용한다.

### 도메인 (참고)

```java
public static Stock create(String productNumber, int quantity) {
    return Stock.builder()
            .productNumber(productNumber)
            .quantity(quantity)
            .build();
}
```

### 테스트 코드

```java
package sample.cafekiosk.spring.domain.stock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import sample.cafekiosk.spring.IntegrationTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Transactional
class StockRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private StockRepository stockRepository;

    @DisplayName("상품번호 리스트로 재고를 조회한다.")
    @Test
    void findAllByProductNumberIn() {
        // given
        Stock stock1 = Stock.create("001", 1);
        Stock stock2 = Stock.create("002", 2);
        Stock stock3 = Stock.create("003", 3);
        stockRepository.saveAll(List.of(stock1, stock2, stock3));

        // when
        List<Stock> stocks = stockRepository.findAllByProductNumberIn(List.of("001", "002"));

        // then
        assertThat(stocks).hasSize(2)
            .extracting("productNumber", "quantity")
            .containsExactlyInAnyOrder(
                tuple("001", 1),
                tuple("002", 2)
            );
    }

}
```

### 무엇을 보여주는가

- 도메인이 정적 팩토리 메서드를 제공하면 **그것을 우선 사용**한다 (`Stock.create(...)`).
- 헬퍼 메서드가 없으므로 테스트 클래스가 더 짧고 가독성이 높다.
- 픽스처 3개 중 2개 매칭 패턴은 동일하다.

---

## 6. 패턴 요약

| 상황 | 패턴 |
|---|---|
| 컬렉션 반환, 순서 무관 | `hasSize(N).extracting(...).containsExactlyInAnyOrder(tuple(...), ...)` |
| 컬렉션 반환, 순서 중요 (ORDER BY) | `hasSize(N).extracting(...).containsExactly(tuple(...), ...)` |
| 단일 값 반환 (정상) | `isEqualTo(expected)` (기대값은 변수로) |
| 단일 값 반환 (빈 결과) | `isNull()` (별도 메서드로 분리) |
| 도메인에 `static create(...)` 있음 | 정적 팩토리 메서드 직접 사용 |
| 도메인에 빌더만 있음 | 클래스 하단에 `private` 헬퍼 메서드 |
