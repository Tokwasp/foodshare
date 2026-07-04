# Controller 테스트 코드 예시

이 문서는 SKILL.md의 규칙이 어떻게 적용되는지를 보여주는 정본 예시(canonical examples)
모음이다.

---

## 0. 공통 응답 포맷과 Validation 핸들러

### 0-1. `ApiResponse<T>` — 모든 응답의 포맷

```java
package sample.cafekiosk.spring.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponse<T> {

    private int code;
    private HttpStatus status;
    private String message;
    private T data;

    public ApiResponse(HttpStatus status, String message, T data) {
        this.code = status.value();
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> of(HttpStatus httpStatus, String message, T data) {
        return new ApiResponse<>(httpStatus, message, data);
    }

    public static <T> ApiResponse<T> of(HttpStatus httpStatus, T data) {
        return of(httpStatus, httpStatus.name(), data);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return of(HttpStatus.OK, data);
    }
}
```

직렬화 결과:
```json
{ "code": 200, "status": "OK", "message": "OK", "data": {...} }
```

### 0-2. `ApiControllerAdvice` — Validation 실패 시 에러 응답 변환

```java
@RestControllerAdvice
public class ApiControllerAdvice {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public ApiResponse<Object> bindException(BindException e) {
        return ApiResponse.of(
            HttpStatus.BAD_REQUEST,
            e.getBindingResult().getAllErrors().get(0).getDefaultMessage(),
            null
        );
    }
}
```

**포인트**

- `@RequestBody` validation 실패는 `BindException`으로 처리.
- 첫 번째 에러 메시지(`getDefaultMessage()`)를 그대로 응답의 `message`에 넣음.
- 따라서 테스트는 `Request` DTO의 validation `message` 속성과 정확히 일치하는 문자열을 검증한다.

---

## 1. 공통 상위 클래스 — `ControllerTestSupport`

모든 Controller 테스트가 상속하는 추상 클래스다.

```java
package sample.cafekiosk.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import sample.cafekiosk.spring.api.controller.order.OrderController;
import sample.cafekiosk.spring.api.controller.product.ProductController;
import sample.cafekiosk.spring.api.service.order.OrderService;
import sample.cafekiosk.spring.api.service.product.ProductService;

@WebMvcTest(controllers = {
    OrderController.class,
    ProductController.class
})
public abstract class ControllerTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected OrderService orderService;

    @MockBean
    protected ProductService productService;

}
```

### 무엇을 보여주는가

- `@WebMvcTest`에 모든 Controller를 모아두어 **하나의 슬라이스 컨텍스트 재사용**
- `MockMvc`와 `ObjectMapper`를 `protected` 필드로 노출
- 모든 Service는 `@MockBean`으로 격리 → DB/비즈니스 로직 없이 Controller만 검증
- **새 Controller 추가 시 이 클래스의 `controllers` 배열과 `@MockBean` 모두 수정 필요**

---

## 2. 정상 케이스 — POST 요청 (`createProduct`)

### 검증 대상 (Controller)

```java
@RequiredArgsConstructor
@RestController
public class ProductController {

    private final ProductService productService;

    @PostMapping("/api/v1/products/new")
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.ok(productService.createProduct(request.toServiceRequest()));
    }

    @GetMapping("/api/v1/products/selling")
    public ApiResponse<List<ProductResponse>> getSellingProducts() {
        return ApiResponse.ok(productService.getSellingProducts());
    }
}
```

### 검증 대상 (Request DTO)

```java
@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotNull(message = "상품 타입은 필수입니다.")
    private ProductType type;

    @NotNull(message = "상품 판매상태는 필수입니다.")
    private ProductSellingStatus sellingStatus;

    @NotBlank(message = "상품 이름은 필수입니다.")
    private String name;

    @Positive(message = "상품 가격은 양수여야 합니다.")
    private int price;

    @Builder
    private ProductCreateRequest(ProductType type, ProductSellingStatus sellingStatus, String name, int price) {
        this.type = type;
        this.sellingStatus = sellingStatus;
        this.name = name;
        this.price = price;
    }

    public ProductCreateServiceRequest toServiceRequest() { /* ... */ }
}
```

### 테스트 코드

```java
@DisplayName("신규 상품을 등록한다.")
@Test
void createProduct() throws Exception {
    // given
    ProductCreateRequest request = ProductCreateRequest.builder()
        .type(ProductType.HANDMADE)
        .sellingStatus(ProductSellingStatus.SELLING)
        .name("아메리카노")
        .price(4000)
        .build();

    // when // then
    mockMvc.perform(
            post("/api/v1/products/new")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isOk());
}
```

### 무엇을 보여주는가

- `throws Exception` 선언
- given 절에서 `Request` DTO를 빌더로 구성 (모든 필드를 채움)
- `// when // then` 한 줄로 묶음 (MockMvc는 perform과 andExpect가 체이닝됨)
- `objectMapper.writeValueAsString(request)` + `contentType(MediaType.APPLICATION_JSON)` 패턴
- `.andDo(print())`로 디버깅 가능
- 정상 케이스는 `status().isOk()`만 검증해도 충분 (이 케이스는 Service 응답 검증이 핵심이 아님)

---

## 3. Validation 실패 — `@NotNull` (`createProductWithoutType`)

### 테스트 코드

```java
@DisplayName("신규 상품을 등록할 때 상품 타입은 필수값이다.")
@Test
void createProductWithoutType() throws Exception {
    // given
    ProductCreateRequest request = ProductCreateRequest.builder()
        .sellingStatus(ProductSellingStatus.SELLING)
        .name("아메리카노")
        .price(4000)
        .build();

    // when // then
    mockMvc.perform(
            post("/api/v1/products/new")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("400"))
        .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("상품 타입은 필수입니다."))
        .andExpect(jsonPath("$.data").isEmpty())
    ;
}
```

### 무엇을 보여주는가

- 빌더에서 `type`만 빼서 `@NotNull` 위반 유도
- `status().isBadRequest()` — `ApiControllerAdvice`가 400으로 변환
- **에러 응답 4개 필드 모두 검증**: `code`, `status`, `message`, `data`
- `$.message` 값은 DTO의 `@NotNull(message = "...")` 속성과 정확히 일치
- `$.data`는 null이므로 `.isEmpty()`로 검증

---

## 4. Validation 실패 — `@NotBlank` (`createProductWithoutName`)

```java
@DisplayName("신규 상품을 등록할 때 상품 이름은 필수값이다.")
@Test
void createProductWithoutName() throws Exception {
    // given
    ProductCreateRequest request = ProductCreateRequest.builder()
        .type(ProductType.HANDMADE)
        .sellingStatus(ProductSellingStatus.SELLING)
        .price(4000)
        .build();

    // when // then
    mockMvc.perform(
            post("/api/v1/products/new")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("400"))
        .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("상품 이름은 필수입니다."))
        .andExpect(jsonPath("$.data").isEmpty())
    ;
}
```

### 무엇을 보여주는가

- 3번 테스트와 구조가 동일 — **각 validation 어노테이션마다 독립적인 테스트**
- 같은 패턴이지만 같은 테스트로 합치지 않는다 (어떤 검증이 깨졌는지 즉시 파악 가능)

---

## 5. Validation 실패 — `@Positive` 경계값 (`createProductWithZeroPrice`)

```java
@DisplayName("신규 상품을 등록할 때 상품 가격은 양수이다.")
@Test
void createProductWithZeroPrice() throws Exception {
    // given
    ProductCreateRequest request = ProductCreateRequest.builder()
        .type(ProductType.HANDMADE)
        .sellingStatus(ProductSellingStatus.SELLING)
        .name("아메리카노")
        .price(0)
        .build();

    // when // then
    mockMvc.perform(
            post("/api/v1/products/new")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("400"))
        .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("상품 가격은 양수여야 합니다."))
        .andExpect(jsonPath("$.data").isEmpty())
    ;
}
```

### 무엇을 보여주는가

- `@Positive`는 `0`을 양수로 인정하지 않음 → **경계값으로 `0`을 선택**
- 음수(`-1` 등)를 따로 테스트하지 않는다 (대표 케이스 하나로 충분)
- 테스트명도 `WithZeroPrice`로 어떤 값이 위반인지 즉시 알 수 있음

---

## 6. GET + Service Mocking (`getSellingProducts`)

### 테스트 코드

```java
@DisplayName("판매 상품을 조회한다.")
@Test
void getSellingProducts() throws Exception {
    // given
    List<ProductResponse> result = List.of();

    when(productService.getSellingProducts()).thenReturn(result);

    // when // then
    mockMvc.perform(
            get("/api/v1/products/selling")
        )
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("200"))
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.message").value("OK"))
        .andExpect(jsonPath("$.data").isArray());
}
```

### 무엇을 보여주는가

- GET 요청은 본문 없음 → `contentType` 불필요
- **Service stubbing은 응답 본문 검증에 필요할 때만**: `data` 필드를 `.isArray()`로 검증하므로
  Service가 빈 리스트라도 반환하도록 stubbing 필요
- 성공 응답도 4개 필드(`code`, `status`, `message`, `data`)를 검증 → 일관된 패턴
- `when().thenReturn()` 사용 (Controller 테스트의 기본 stubbing 스타일)

---

## 7. `@NotEmpty` 리스트 검증 (`createOrderWithEmptyProductNumbers`)

### 검증 대상 (Request DTO)

```java
@Getter
@NoArgsConstructor
public class OrderCreateRequest {

    @NotEmpty(message = "상품 번호 리스트는 필수입니다.")
    private List<String> productNumbers;

    @Builder
    private OrderCreateRequest(List<String> productNumbers) {
        this.productNumbers = productNumbers;
    }

    public OrderCreateServiceRequest toServiceRequest() { /* ... */ }
}
```

### 테스트 코드

```java
class OrderControllerTest extends ControllerTestSupport {

    @DisplayName("신규 주문을 등록한다.")
    @Test
    void createOrder() throws Exception {
        // given
        OrderCreateRequest request = OrderCreateRequest.builder()
            .productNumbers(List.of("001"))
            .build();

        // when // then
        mockMvc.perform(
                post("/api/v1/orders/new")
                    .content(objectMapper.writeValueAsString(request))
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.message").value("OK"));
        ;
    }

    @DisplayName("신규 주문을 등록할 때 상품번호는 1개 이상이어야 한다.")
    @Test
    void createOrderWithEmptyProductNumbers() throws Exception {
        // given
        OrderCreateRequest request = OrderCreateRequest.builder()
            .productNumbers(List.of())
            .build();

        // when // then
        mockMvc.perform(
                post("/api/v1/orders/new")
                    .content(objectMapper.writeValueAsString(request))
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("400"))
            .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("상품 번호 리스트는 필수입니다."))
            .andExpect(jsonPath("$.data").isEmpty())
        ;
    }
}
```

### 무엇을 보여주는가

- `@NotEmpty`는 빈 리스트(`List.of()`)에서 실패 → 경계 케이스로 빈 리스트 선택
- 정상 케이스는 1개짜리 리스트로 통과시킴
- 같은 클래스 내에 정상/실패 케이스를 인접하게 배치하여 비교가 쉽다

---

## 8. 패턴 요약

| 상황 | 패턴 |
|---|---|
| 정상 케이스 (POST) | `objectMapper.writeValueAsString` + `contentType(JSON)` + `status().isOk()` |
| 정상 케이스 (GET) | `contentType` 불필요, 필요시 Service stubbing |
| Validation 실패 | 빌더에서 해당 필드 제외/위반값 → `status().isBadRequest()` + 4필드 검증 |
| 응답 데이터 검증 | Service stubbing → `jsonPath("$.data")` 로 검증 |
| 경계값 | `@Positive` → `0`, `@NotEmpty` → `List.of()`, `@Min(1)` → `0` |

---

## 9. 자주 보이는 안티패턴

| ❌ 안티패턴 | ✅ 대안 |
|---|---|
| 각 테스트마다 `@WebMvcTest` 따로 선언 | `ControllerTestSupport`에 한 번만 |
| 각 테스트마다 `@MockBean OrderService` 따로 선언 | `ControllerTestSupport`의 `protected` 필드 사용 |
| `contentType` 누락 | `MediaType.APPLICATION_JSON` 명시 |
| `.andDo(print())` 누락 | 항상 추가 (실패 디버깅 쉬워짐) |
| 한 테스트에서 여러 validation 위반 동시 검증 | 어노테이션 1개당 테스트 1개 |
| validation 메시지를 변경했는데 테스트의 기대값을 안 바꿈 | 메시지는 DTO와 테스트가 강하게 결합 — 함께 수정 |
| Controller 테스트에서 DB 상태 검증 시도 | Service/Repository 테스트의 몫 |
| `@WebMvcTest`에 Service를 `@MockBean`으로 안 채우고 실행 | 컨텍스트 로딩 실패 — 반드시 채울 것 |
