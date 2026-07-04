# service — reference

`SKILL.md`의 규칙 번호와 같은 번호로 묶은 실제 코드 예제다.
예제는 `cafekiosk` 프로젝트(`api/service` 패키지)의 서비스 코드를 기준으로 한다.

---

## §1. 클래스 구조 — `@Service` + 생성자 주입

```java
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;       // private final
    private final ProductNumberFactory productNumberFactory; // 협력 컴포넌트 주입
    // @RequiredArgsConstructor 가 위 final 필드로 생성자를 만든다 (생성자 주입)
}
```

## §1-2. 서비스가 다른 서비스에 의존

통계 서비스가 **메일 발송 서비스**를 주입받아 사용한다.

```java
@RequiredArgsConstructor
@Service
public class OrderStatisticsService {

    private final OrderRepository orderRepository;
    private final MailService mailService;   // 다른 @Service 주입

    public boolean sendOrderStatisticsMail(LocalDate orderDate, String email) {
        // ... 조회 → 합계 계산은 도메인에 위임 → 메일 서비스에 발송 위임
        boolean result = mailService.sendMail(/* ... */);
        if (!result) {
            throw new IllegalArgumentException("매출 통계 메일 전송에 실패했습니다.");
        }
        return true;
    }
}
```

## §1-3. 응집된 생성 책임은 `@Component`로 추출

"다음 상품 번호를 만든다"는 책임을 서비스에서 떼어내 `Factory`로 분리했다.
도메인 규칙이 아니라 **생성/조립 책임**이므로 `@Component`로 둔다.

```java
@RequiredArgsConstructor
@Component                       // @Service 가 아니라 @Component
public class ProductNumberFactory {

    private final ProductRepository productRepository;

    public String createNextProductNumber() {
        String latestProductNumber = productRepository.findLatestProductNumber();
        if (latestProductNumber == null) {
            return "001";
        }
        int next = Integer.parseInt(latestProductNumber) + 1;
        return String.format("%03d", next);
    }
}
```

---

## §2. 트랜잭션 경계 — readOnly 기본 + 쓰기만 재정의

```java
@Transactional(readOnly = true)   // 클래스 기본: 읽기 전용
@RequiredArgsConstructor
@Service
public class ProductService {

    @Transactional                // 쓰기 메서드만 재정의
    public ProductResponse createProduct(ProductCreateServiceRequest request) {
        // ... 저장
    }

    public List<ProductResponse> getSellingProducts() {
        // 클래스 레벨 readOnly = true 를 그대로 따른다 (조회)
    }
}
```

쓰기 위주 서비스는 클래스 레벨에 `@Transactional`만 둔다.

```java
@Transactional
@RequiredArgsConstructor
@Service
public class OrderService { /* 모든 메서드가 쓰기 트랜잭션 */ }
```

---

## §3. Response는 서비스가 완성한다

서비스가 엔티티를 `Response.of(...)`로 변환해 반환한다. 컬렉션은 스트림 매핑.

```java
public ProductResponse createProduct(ProductCreateServiceRequest request) {
    String nextProductNumber = productNumberFactory.createNextProductNumber();
    Product product = request.toEntity(nextProductNumber);
    Product savedProduct = productRepository.save(product);
    return ProductResponse.of(savedProduct);          // 엔티티 → Response (서비스가 완성)
}

public List<ProductResponse> getSellingProducts() {
    List<Product> products =
        productRepository.findAllBySellingStatusIn(ProductSellingStatus.forDisplay());
    return products.stream()
        .map(ProductResponse::of)                     // 컬렉션 매핑
        .collect(Collectors.toList());
}
```

Response DTO는 순수 데이터 + `of(entity)` 정적 팩토리.

```java
@Getter
public class ProductResponse {
    private Long id;
    private String productNumber;
    private ProductType type;
    // ... HTTP 메타(code/status/message) 없음 — 순수 데이터만

    @Builder
    private ProductResponse(/* ... */) { /* ... */ }

    public static ProductResponse of(Product product) {   // 엔티티 → Response 캡슐화
        return ProductResponse.builder()
            .id(product.getId())
            .productNumber(product.getProductNumber())
            // ...
            .build();
    }
}
```

중첩 변환도 `of` 안에서 다른 Response의 `of`를 호출해 조립한다.

```java
public static OrderResponse of(Order order) {
    return OrderResponse.builder()
        .id(order.getId())
        .totalPrice(order.getTotalPrice())
        .products(order.getOrderProducts().stream()
            .map(op -> ProductResponse.of(op.getProduct()))   // Response 조합
            .collect(Collectors.toList()))
        .build();
}
```

---

## §4. 엔티티 생성·매핑의 위치 (서비스 안)

```java
public ProductResponse createProduct(ProductCreateServiceRequest request) {
    String nextProductNumber = productNumberFactory.createNextProductNumber(); // 부가 값 먼저 확보
    Product product = request.toEntity(nextProductNumber);  // 들어올 때: 요청 → 엔티티
    Product savedProduct = productRepository.save(product);
    return ProductResponse.of(savedProduct);                // 나갈 때: 엔티티 → Response.of
}
```

엔티티(`product`)는 이 메서드(서비스) 안에서만 존재하고, 밖으로는 `Response`만 나간다.

---

## §5. 비결정적 외부값은 파라미터로 주입

서비스는 `now()`를 직접 부르지 않고 시각을 **파라미터로** 받는다.

```java
// Service: 시각을 받는다 (안에서 now() 호출하지 않음)
public OrderResponse createOrder(OrderCreateServiceRequest request,
                                 LocalDateTime registeredDateTime) {
    List<Product> products = findProductsBy(request.getProductNumbers());
    deductStockQuantities(products);
    Order order = Order.create(products, registeredDateTime);   // 받은 시각을 그대로 사용
    Order savedOrder = orderRepository.save(order);
    return OrderResponse.of(savedOrder);
}
```

```java
// Controller: 호출 시점에 now() 를 만들어 넘긴다 (비결정적 값은 경계에서 생성)
LocalDateTime registeredDateTime = LocalDateTime.now();
orderService.createOrder(/* 요청 */, registeredDateTime);
```

테스트는 고정 시각을 넘겨 결과를 정확히 단정할 수 있다.

```java
LocalDateTime registeredDateTime = LocalDateTime.of(2025, 1, 1, 0, 0);
OrderResponse response = orderService.createOrder(request, registeredDateTime);
// response.getRegisteredDateTime() 을 고정값으로 검증 가능
```

---

## §6. 흐름은 의도가 드러나는 private 헬퍼로 분리

`createOrder`의 조율 흐름을 단계별 private 메서드로 쪼갰다.
public 메서드가 목차처럼 읽힌다. (판단/계산은 도메인 `stock`에 위임 — Tell Don't Ask)

```java
public OrderResponse createOrder(OrderCreateServiceRequest request, LocalDateTime registeredDateTime) {
    List<Product> products = findProductsBy(request.getProductNumbers()); // 조회
    deductStockQuantities(products);                                      // 시킴 (재고 차감)
    Order order = Order.create(products, registeredDateTime);             // 시킴 (주문 생성)
    Order savedOrder = orderRepository.save(order);                       // 저장
    return OrderResponse.of(savedOrder);                                  // 변환
}

private void deductStockQuantities(List<Product> products) {
    List<String> stockProductNumbers = extractStockProductNumbers(products);
    Map<String, Stock> stockMap = createStockMapBy(stockProductNumbers);
    Map<String, Long> productCountingMap = createCountingMapBy(stockProductNumbers);

    for (String stockProductNumber : new HashSet<>(stockProductNumbers)) {
        Stock stock = stockMap.get(stockProductNumber);
        int quantity = productCountingMap.get(stockProductNumber).intValue();

        if (stock.isQuantityLessThan(quantity)) {        // 판단은 도메인에게 물음 (oop-java §2)
            throw new IllegalArgumentException("재고가 부족한 상품이 있습니다.");
        }
        stock.deductQuantity(quantity);                  // 상태 변경도 도메인이 수행
    }
}

// 인스턴스 상태를 쓰지 않는 순수 변환 헬퍼는 static
private static List<String> extractStockProductNumbers(List<Product> products) {
    return products.stream()
        .filter(p -> ProductType.containsStockType(p.getType()))
        .map(Product::getProductNumber)
        .collect(Collectors.toList());
}
```

> 메서드 이름·길이·분리 기준의 세부 규칙은 readable-java가 책임진다.

---

## §7. 예외는 의미 있게 던진다

`null` 반환 대신 의미 있는 예외를 던진다. 서비스는 잡지 않고 던지기만 한다.

```java
if (stock.isQuantityLessThan(quantity)) {
    throw new IllegalArgumentException("재고가 부족한 상품이 있습니다.");  // null 반환 금지
}
```

```java
boolean result = mailService.sendMail(/* ... */);
if (!result) {
    throw new IllegalArgumentException("매출 통계 메일 전송에 실패했습니다.");
}
```

처리(에러 응답 변환)는 `@RestControllerAdvice`의 `ExceptionHandler`가 담당한다
(→ controller 스킬). 서비스에 `try/catch`는 두지 않는다.
