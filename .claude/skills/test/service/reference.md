# Service 테스트 코드 예시

이 문서는 SKILL.md의 규칙이 어떻게 적용되는지를 보여주는 정본 예시(canonical examples)
모음이다.

---

## 0. 통합 테스트 상위 클래스 (Repository 스킬과 공유)

```java
package sample.cafekiosk.spring;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sample.cafekiosk.spring.client.mail.MailSendClient;

@ActiveProfiles("test")
@Transactional
@SpringBootTest
public abstract class IntegrationTestSupport {

    @MockBean
    protected MailSendClient mailSendClient;

}
```

**포인트**

- 외부 시스템(`MailSendClient`)은 여기서 한 번 `@MockBean`으로 격리.
- 통합 테스트 자식 클래스에서는 `mailSendClient`를 그대로 stubbing해서 사용.
- **격리용 `@Transactional`도 여기 한 번**. 상속하는 모든 통합 테스트가 메서드 종료 시
  롤백되어 데이터가 격리된다 → 자식 테스트는 `@AfterEach` 정리가 불필요하다.

---

## 1. 통합 테스트 — 가장 단순한 케이스 (ProductService)

`ProductService.createProduct`는 **기존 상품 중 가장 최근 상품번호 + 1**로 채번한다.
DB 상태에 의존하므로 통합 테스트가 적합하다.

### 검증 대상 (Service)

```java
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductNumberFactory productNumberFactory;

    @Transactional
    public ProductResponse createProduct(ProductCreateServiceRequest request) {
        String nextProductNumber = productNumberFactory.createNextProductNumber();
        Product product = request.toEntity(nextProductNumber);
        Product savedProduct = productRepository.save(product);
        return ProductResponse.of(savedProduct);
    }
}
```

### 테스트 코드

```java
package sample.cafekiosk.spring.api.service.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sample.cafekiosk.spring.IntegrationTestSupport;
import sample.cafekiosk.spring.api.service.product.request.ProductCreateServiceRequest;
import sample.cafekiosk.spring.api.service.product.response.ProductResponse;
import sample.cafekiosk.spring.domain.product.Product;
import sample.cafekiosk.spring.domain.product.ProductRepository;
import sample.cafekiosk.spring.domain.product.ProductSellingStatus;
import sample.cafekiosk.spring.domain.product.ProductType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static sample.cafekiosk.spring.domain.product.ProductSellingStatus.SELLING;
import static sample.cafekiosk.spring.domain.product.ProductType.HANDMADE;

class ProductServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @DisplayName("신규 상품을 등록한다. 상품번호는 가장 최근 상품의 상품번호에서 1 증가한 값이다.")
    @Test
    void createProduct() {
        // given
        Product product = createProduct("001", HANDMADE, SELLING, "아메리카노", 4000);
        productRepository.save(product);

        ProductCreateServiceRequest request = ProductCreateServiceRequest.builder()
                .type(HANDMADE)
                .sellingStatus(SELLING)
                .name("카푸치노")
                .price(5000)
                .build();

        // when
        ProductResponse productResponse = productService.createProduct(request);

        // then
        assertThat(productResponse)
                .extracting("productNumber", "type", "sellingStatus", "name", "price")
                .contains("002", HANDMADE, SELLING, "카푸치노", 5000);

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(2)
                .extracting("productNumber", "type", "sellingStatus", "name", "price")
                .containsExactlyInAnyOrder(
                        tuple("001", HANDMADE, SELLING, "아메리카노", 4000),
                        tuple("002", HANDMADE, SELLING, "카푸치노", 5000)
                );
    }

    @DisplayName("상품이 하나도 없는 경우 신규 상품을 등록하면 상품번호는 001이다.")
    @Test
    void createProductWhenProductsIsEmpty() {
        // given
        ProductCreateServiceRequest request = ProductCreateServiceRequest.builder()
                .type(HANDMADE)
                .sellingStatus(SELLING)
                .name("카푸치노")
                .price(5000)
                .build();

        // when
        ProductResponse productResponse = productService.createProduct(request);

        // then
        assertThat(productResponse)
                .extracting("productNumber", "type", "sellingStatus", "name", "price")
                .contains("001", HANDMADE, SELLING, "카푸치노", 5000);

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(1)
                .extracting("productNumber", "type", "sellingStatus", "name", "price")
                .contains(
                        tuple("001", HANDMADE, SELLING, "카푸치노", 5000)
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
- **격리는 베이스의 `@Transactional` 롤백이 담당** — 자식 테스트엔 `@AfterEach`가 없다
- `ServiceRequest`(Controller DTO가 아님)를 사용
- 정상 케이스(`createProduct`)와 엣지 케이스(`createProductWhenProductsIsEmpty`)를
  별도 테스트로 분리
- **응답 검증 + DB 상태 검증을 둘 다** 수행

---

## 2. 통합 테스트 — 여러 도메인이 협력 (OrderService)

`OrderService.createOrder`는 상품 조회 + 재고 차감 + 주문 저장이라는 **여러 도메인의
협력**을 수행한다. 분기와 예외 케이스가 많다.

### 검증 대상 (요약)

```java
public OrderResponse createOrder(OrderCreateServiceRequest request, LocalDateTime registeredDateTime) {
    List<String> productNumbers = request.getProductNumbers();
    List<Product> products = findProductsBy(productNumbers);

    deductStockQuantities(products);  // 재고 부족 시 IllegalArgumentException

    Order order = Order.create(products, registeredDateTime);
    Order savedOrder = orderRepository.save(order);
    return OrderResponse.of(savedOrder);
}
```

### 테스트 코드 (발췌)

```java
class OrderServiceTest extends IntegrationTestSupport {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderProductRepository orderProductRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private OrderService orderService;

    @DisplayName("주문번호 리스트를 받아 주문을 생성한다.")
    @Test
    void createOrder() {
        // given
        LocalDateTime registeredDateTime = LocalDateTime.now();

        Product product1 = createProduct(HANDMADE, "001", 1000);
        Product product2 = createProduct(HANDMADE, "002", 3000);
        Product product3 = createProduct(HANDMADE, "003", 5000);
        productRepository.saveAll(List.of(product1, product2, product3));

        OrderCreateServiceRequest request = OrderCreateServiceRequest.builder()
                .productNumbers(List.of("001", "002"))
                .build();

        // when
        OrderResponse orderResponse = orderService.createOrder(request, registeredDateTime);

        // then
        assertThat(orderResponse.getId()).isNotNull();
        assertThat(orderResponse)
                .extracting("registeredDateTime", "totalPrice")
                .contains(registeredDateTime, 4000);
        assertThat(orderResponse.getProducts()).hasSize(2)
                .extracting("productNumber", "price")
                .containsExactlyInAnyOrder(
                        tuple("001", 1000),
                        tuple("002", 3000)
                );
    }

    @DisplayName("재고와 관련된 상품이 포함되어 있는 주문번호 리스트를 받아 주문을 생성한다.")
    @Test
    void createOrderWithStock() {
        // given
        LocalDateTime registeredDateTime = LocalDateTime.now();

        Product product1 = createProduct(BOTTLE, "001", 1000);
        Product product2 = createProduct(BAKERY, "002", 3000);
        Product product3 = createProduct(HANDMADE, "003", 5000);
        productRepository.saveAll(List.of(product1, product2, product3));

        Stock stock1 = Stock.create("001", 2);
        Stock stock2 = Stock.create("002", 2);
        stockRepository.saveAll(List.of(stock1, stock2));

        OrderCreateServiceRequest request = OrderCreateServiceRequest.builder()
                .productNumbers(List.of("001", "001", "002", "003"))
                .build();

        // when
        OrderResponse orderResponse = orderService.createOrder(request, registeredDateTime);

        // then
        assertThat(orderResponse.getId()).isNotNull();
        assertThat(orderResponse)
                .extracting("registeredDateTime", "totalPrice")
                .contains(registeredDateTime, 10000);
        assertThat(orderResponse.getProducts()).hasSize(4)
                .extracting("productNumber", "price")
                .containsExactlyInAnyOrder(
                        tuple("001", 1000),
                        tuple("001", 1000),
                        tuple("002", 3000),
                        tuple("003", 5000)
                );

        List<Stock> stocks = stockRepository.findAll();
        assertThat(stocks).hasSize(2)
                .extracting("productNumber", "quantity")
                .containsExactlyInAnyOrder(
                        tuple("001", 0),
                        tuple("002", 1)
                );
    }

    @DisplayName("재고가 부족한 상품으로 주문을 생성하려는 경우 예외가 발생한다.")
    @Test
    void createOrderWithNoStock() {
        // given
        LocalDateTime registeredDateTime = LocalDateTime.now();

        Product product1 = createProduct(BOTTLE, "001", 1000);
        Product product2 = createProduct(BAKERY, "002", 3000);
        Product product3 = createProduct(HANDMADE, "003", 5000);
        productRepository.saveAll(List.of(product1, product2, product3));

        Stock stock1 = Stock.create("001", 2);
        Stock stock2 = Stock.create("002", 2);
        stock1.deductQuantity(1);
        stockRepository.saveAll(List.of(stock1, stock2));

        OrderCreateServiceRequest request = OrderCreateServiceRequest.builder()
                .productNumbers(List.of("001", "001", "002", "003"))
                .build();

        // when // then
        assertThatThrownBy(() -> orderService.createOrder(request, registeredDateTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재고가 부족한 상품이 있습니다.");
    }

    private Product createProduct(ProductType type, String productNumber, int price) {
        return Product.builder()
                .type(type)
                .productNumber(productNumber)
                .price(price)
                .sellingStatus(SELLING)
                .name("메뉴 이름")
                .build();
    }

}
```

### 무엇을 보여주는가

- **시간을 외부에서 주입** (`LocalDateTime.now()`를 테스트 메서드에서 만들어 Service로 전달)
- 헬퍼 메서드 시그니처를 **테스트 관심사에 맞게 좁힘**:
  `createProduct(ProductType, String, int)` — `sellingStatus`, `name`은 내부 고정값
- **여러 시나리오를 별도 테스트로 분리**:
  - `createOrder` — 일반 주문
  - `createOrderWithDuplicateProductNumbers` — 같은 상품번호 중복
  - `createOrderWithStock` — 재고 관리되는 상품 타입 (`BOTTLE`, `BAKERY`)
  - `createOrderWithNoStock` — 재고 부족 예외
- **예외 검증**: `// when // then` 주석 + `assertThatThrownBy` + 타입 + 메시지
- **부수효과 검증**: 주문 생성 후 `stockRepository.findAll()`로 재고가 실제로
  차감되었는지 확인 (응답에는 재고 정보가 없으므로 DB로 직접 확인)
- 격리는 베이스 `@Transactional` 롤백이 담당 → 여러 Repository를 정리하는 `@AfterEach`가
  불필요해진다 (외래키 삭제 순서 고민도 사라진다)

---

## 3. 통합 테스트 — 외부 의존성 stubbing (OrderStatisticsService)

`OrderStatisticsService.sendOrderStatisticsMail`은 주문을 조회한 뒤 **메일을 발송**한다.
메일은 외부 시스템이므로 `@MockBean`으로 격리한 뒤 stubbing한다.

### 테스트 코드

```java
class OrderStatisticsServiceTest extends IntegrationTestSupport {

    @Autowired
    private OrderStatisticsService orderStatisticsService;

    @Autowired
    private OrderProductRepository orderProductRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private MailSendHistoryRepository mailSendHistoryRepository;

    @DisplayName("결제완료 주문들을 조회하여 매출 통계 메일을 전송한다.")
    @Test
    void sendOrderStatisticsMail() {
        // given
        LocalDateTime now = LocalDateTime.of(2023, 3, 5, 0, 0);

        Product product1 = createProduct(HANDMADE, "001", 1000);
        Product product2 = createProduct(HANDMADE, "002", 2000);
        Product product3 = createProduct(HANDMADE, "003", 3000);
        List<Product> products = List.of(product1, product2, product3);
        productRepository.saveAll(products);

        Order order1 = createPaymentCompletedOrder(LocalDateTime.of(2023, 3, 4, 23, 59, 59), products);
        Order order2 = createPaymentCompletedOrder(now, products);
        Order order3 = createPaymentCompletedOrder(LocalDateTime.of(2023, 3, 5, 23, 59, 59), products);
        Order order4 = createPaymentCompletedOrder(LocalDateTime.of(2023, 3, 6, 0, 0), products);

        // stubbing
        when(mailSendClient.sendEmail(any(String.class), any(String.class), any(String.class), any(String.class)))
            .thenReturn(true);

        // when
        boolean result = orderStatisticsService.sendOrderStatisticsMail(LocalDate.of(2023, 3, 5), "test@test.com");

        // then
        assertThat(result).isTrue();

        List<MailSendHistory> histories = mailSendHistoryRepository.findAll();
        assertThat(histories).hasSize(1)
            .extracting("content")
            .contains("총 매출 합계는 12000원입니다.");
    }

    private Order createPaymentCompletedOrder(LocalDateTime now, List<Product> products) {
        Order order = Order.builder()
            .products(products)
            .orderStatus(OrderStatus.PAYMENT_COMPLETED)
            .registeredDateTime(now)
            .build();
        return orderRepository.save(order);
    }

    private Product createProduct(ProductType type, String productNumber, int price) {
        return Product.builder()
            .type(type)
            .productNumber(productNumber)
            .price(price)
            .sellingStatus(SELLING)
            .name("메뉴 이름")
            .build();
    }

}
```

### 무엇을 보여주는가

- **`@MockBean`은 상위 클래스(IntegrationTestSupport)에 선언**, 테스트에서는
  `mailSendClient`를 그대로 stubbing
- **시간 경계 테스트**: 4개의 주문이 `2023-03-04 23:59:59`, `2023-03-05 00:00`,
  `2023-03-05 23:59:59`, `2023-03-06 00:00`에 분포 → 당일(`2023-03-05`)에 해당하는
  주문 2건만 통계에 포함되는지 검증 (총합 12000 = 2건 × (1000+2000+3000))
- **고정 시간 사용**: `LocalDateTime.of(...)`로 명시적인 시각을 지정 — 테스트가
  실행 시점에 따라 결과가 달라지지 않도록
- `// stubbing` 주석으로 stubbing 절을 명시적으로 표시
- **부수효과 검증**: 메일 발송 자체가 아니라 `MailSendHistory`가 DB에 저장되었는지
  확인 (메일 발송은 Mock이므로 실제로 일어나지 않음)

---

## 4. 단위 테스트 — Mockito (MailService)

`MailService.sendMail`은 외부 시스템(`MailSendClient`) 호출과 이력 저장이라는
**얇은 흐름**이다. DB 상태에 의존하지 않으므로 Mock으로 전부 채워도 검증 가치가
유지된다 → 단위 테스트로 작성한다.

### 검증 대상 (Service)

```java
@RequiredArgsConstructor
@Service
public class MailService {

    private final MailSendClient mailSendClient;
    private final MailSendHistoryRepository mailSendHistoryRepository;

    public boolean sendMail(String fromEmail, String toEmail, String subject, String content) {
        boolean result = mailSendClient.sendEmail(fromEmail, toEmail, subject, content);
        if (result) {
            mailSendHistoryRepository.save(MailSendHistory.builder()
                .fromEmail(fromEmail)
                .toEmail(toEmail)
                .subject(subject)
                .content(content)
                .build()
            );
            return true;
        }
        return false;
    }
}
```

### 테스트 코드

```java
package sample.cafekiosk.spring.api.service.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import sample.cafekiosk.spring.client.mail.MailSendClient;
import sample.cafekiosk.spring.domain.history.mail.MailSendHistory;
import sample.cafekiosk.spring.domain.history.mail.MailSendHistoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private MailSendClient mailSendClient;

    @Mock
    private MailSendHistoryRepository mailSendHistoryRepository;

    @InjectMocks
    private MailService mailService;

    @DisplayName("메일 전송 테스트")
    @Test
    void sendMail() {
        // given
        given(mailSendClient.sendEmail(anyString(), anyString(), anyString(), anyString()))
            .willReturn(true);

        // when
        boolean result = mailService.sendMail("", "", "", "");

        // then
        assertThat(result).isTrue();
        verify(mailSendHistoryRepository, times(1)).save(any(MailSendHistory.class));
    }

}
```

### 무엇을 보여주는가

- **`@SpringBootTest` 없음, `extends IntegrationTestSupport` 없음** → 스프링 컨텍스트
  로딩 비용 0
- `@ExtendWith(MockitoExtension.class)`로 Mockito만 활성화
- 모든 의존성은 `@Mock`, 테스트 대상은 `@InjectMocks`
- **BDD 스타일 stubbing**: `given().willReturn()`
- **호출 검증**: `verify(mailSendHistoryRepository, times(1)).save(...)` —
  이력 저장이 정확히 1번 일어났는지 확인 (이 검증이 빠지면 단위 테스트의 가치가 줄어든다)

---

## 5. 패턴 요약

| 상황 | 스타일 | 핵심 요소 |
|---|---|---|
| DB 상태에 의존하는 비즈니스 로직 | 통합 | `extends IntegrationTestSupport`, 격리는 베이스 `@Transactional` 롤백 |
| 여러 도메인 협력 + 부수효과 (재고/주문 등) | 통합 | 응답 검증 + DB 상태 검증 둘 다 |
| 외부 시스템(메일/SMS 등) 호출 포함 | 통합 | `@MockBean`(상위 클래스) + stubbing |
| 외부 시스템 어댑터 (얇은 흐름) | 단위 | `@Mock` + `@InjectMocks` + `verify` |
| 예외 발생 | 공통 | `// when // then` + `assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)` |
| 시간 의존 로직 | 공통 | 시간은 파라미터로 주입, `LocalDateTime.of(...)`로 고정값 사용 |

---

## 6. 자주 보이는 안티패턴

| ❌ 안티패턴 | ✅ 대안 |
|---|---|
| 저장→재조회로 DB 상태를 검증하는데 1차 캐시 거짓 통과를 방치 | `flush()`+`clear()` 또는 그 테스트만 `@Transactional(propagation = NOT_SUPPORTED)` + `@AfterEach` |
| 자식 테스트마다 `@AfterEach`로 수동 정리 | 격리는 베이스 `@Transactional` 롤백에 맡김 (롤백을 끈 테스트만 수동 정리) |
| `deleteAll()` (1건씩 삭제 — N+1) | `deleteAllInBatch()` (단일 DELETE) |
| `LocalDateTime.now()`를 Service 내부 호출 | 파라미터로 시간 주입 |
| Controller의 Request DTO를 Service 테스트에 직접 사용 | Service 전용 `*ServiceRequest` 사용 |
| 모든 의존성을 Mock으로 채운 ProductService 테스트 | 통합 테스트 (DB 의존 비즈니스 로직) |
| 응답만 검증하고 DB 상태 검증 누락 | 응답 + DB 둘 다 검증 |
| 예외 타입만 검증 (`isInstanceOf`만) | 타입 + 메시지 둘 다 (`hasMessage` 추가) |
