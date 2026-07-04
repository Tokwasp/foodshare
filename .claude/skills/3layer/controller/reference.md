# Controller 계층 코드 예제

`SKILL.md`의 각 규칙에서 "→ reference.md §N"으로 가리키는 코드 예제 모음이다.
규칙 설명은 SKILL.md에 있고, 여기에는 예제와 짧은 캡션만 둔다.

---

## §1. Controller 클래스 구조

`@RestController` + `@RequiredArgsConstructor`로 Service를 생성자 주입받는다.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
}
```

---

## §2. 반환 타입 `ResponseEntity<ApiResponse<Response>>`

기본형. Request를 그대로 Service에 넘기고, 받은 Response를 두 겹으로 감싸 반환한다.

```java
@PostMapping
public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
        @Valid @RequestBody ProductCreateRequest request) {

    ProductResponse response = productService.createProduct(request);
    return ResponseEntity.ok(ApiResponse.ok(response));
}
```

상태 코드별 변형. 200/201(+Location 헤더)/204를 `ResponseEntity`로 표현한다.

```java
// 200 OK
return ResponseEntity.ok(ApiResponse.ok(response));

// 201 Created (+ Location 헤더)
        return ResponseEntity
        .created(URI.create("/api/v1/products/" + response.getId()))
        .body(ApiResponse.ok(response));

// 204 No Content (본문 없음)
        return ResponseEntity.noContent().build();
```

---

## §3-1. Request DTO

`@Getter` + 기본 생성자만 둔다. `@Setter`는 없다.

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCreateRequest {
    // 필드 + validation 어노테이션
}
```

---

## §3-2. `toEntity()` 정적 팩토리

Request가 자기 데이터로 엔티티를 만든다. 내부는 빌더 패턴. 호출 주체는 Service다.

```java
public Product toEntity() {
    return Product.builder()
            .type(type)
            .name(name)
            .price(price)
            .build();
}
```

---

## §5. 공통 응답 래퍼 `ApiResponse<T>` 포맷

모든 응답 본문은 이 포맷으로 감싼다. `data`의 타입은 `*Response` DTO다.

```json
{ "code": 200, "status": "OK", "message": "OK", "data": {...} }
```

---

## §7. 단방향 데이터 흐름

요청 → Request DTO → Service(엔티티 생성·처리·Response 완성) → Controller(HTTP 메타·포맷만 씌움) → 응답.

```
[HTTP 요청]
   │  @Valid @RequestBody
   ▼
Request DTO ──(그대로 전달)──▶ Service
                                 │  request.toEntity() (빌더로 엔티티 생성)
                                 │  도메인/비즈니스 처리
                                 │  Response DTO 완성
                                 ▼
Controller ◀──(Response 반환)── Service
   │  ApiResponse.ok(response)        → 본문 포맷
   │  ResponseEntity.ok(...) / .created(...)  → 상태 코드 + 헤더
   ▼
[HTTP 응답: status/headers + { code, status, message, data }]

* 예외 발생 시: Service throw ──▶ @RestControllerAdvice ──▶ ApiResponse 에러 포맷
```

---

## §8. 인프라 자원의 빈 분리 (before / after)

before. Controller가 스레드풀을 직접 만들고 `@PreDestroy`로 생명주기까지 떠안는다. 인프라 설정이 경계 변환 책임과 뒤섞였다.

```java
@RestController
@RequiredArgsConstructor
public class ChatController {

    // 자원을 직접 생성·소유 (안티패턴)
    private final ThreadPoolTaskExecutor streamExecutor = createStreamExecutor();

    @PreDestroy
    void shutdownExecutor() {        // 생명주기 관리도 Controller가 떠안음
        streamExecutor.shutdown();
    }

    private static ThreadPoolTaskExecutor createStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("chat-sse-");
        executor.initialize();
        return executor;
    }
}
```

after-1. 자원 생성·설정·종료를 `config/executor`의 `@Configuration`으로 옮긴다. 종료는 `destroyMethod`로 스프링이 처리한다.

```java
// config/executor/StreamExecutorConfig.java
@Configuration
public class StreamExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("chat-sse-");
        executor.initialize();
        return executor;
    }
}
```

after-2. Controller는 그 빈을 생성자 주입으로 받아 쓰기만 한다. 생성·종료 코드가 사라진다.

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ThreadPoolTaskExecutor streamExecutor;   // 주입받아 사용

    @PostMapping
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter();
        streamExecutor.execute(() -> chatService.stream(emitter, request));
        return emitter;
    }
}
```