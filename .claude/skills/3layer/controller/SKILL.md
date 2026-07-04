---
name: controller
description: |
  Spring `@RestController`(API 컨트롤러)와 그에 딸린 Request/Response DTO를
  새로 만들거나 수정할 때 적용한다.

  Controller는 비즈니스 규칙을 담지 않으므로 oop-java 스킬은 적용하지 않는다.
  순수 Java 가독성(이름, 메서드 길이 등)은 readable-java 스킬을 함께 따른다.

  단, `src/test/java` 하위 테스트 코드에서는 controller-test 스킬이 우선한다.
---

# Controller 계층 작성 규칙

이 스킬은 HTTP 경계(boundary)에서 **요청을 받아 Service에 넘기고, Service가 만든
응답을 그대로 반환**하는 코드를 다룰 때의 규칙을 정의한다.

핵심 질문은 단 하나다: **"이 코드는 경계 변환만 하는가, 아니면 판단을 하는가?"**
판단(비즈니스 분기)이 보이면 그건 Service로 내려보내야 할 코드다.

> 코드 예제는 모두 같은 패키지의 **`reference.md`** 에 있다. 각 규칙 옆의
> "→ reference.md §N" 표시를 따라 해당 예제를 확인한다.

---

## 0. Controller의 책임 경계 — "무엇만 하는가"

Controller는 **HTTP 입출력 변환만** 담당한다. 한 메서드는 다음 4단계만 수행한다.

1. HTTP 요청을 `Request` DTO로 받는다 (`@RequestBody`, `@Valid`)
2. `Request`를 **그대로** Service에 넘긴다
3. Service가 완성한 `Response`를 받는다
4. `Response`를 공통 래퍼(`ApiResponse`)로 감싸고, 다시 `ResponseEntity`로 감싸 반환한다

### 0-1. Controller가 절대 하지 않는 것

- **비즈니스 분기/판단** — `if (request.getPrice() > 0)` 같은 검증·분기 금지.
  값 검증은 Bean Validation, 비즈니스 판단은 Service의 몫이다.
- **엔티티 직접 노출** — 엔티티를 응답으로 반환하지 않는다. 응답은 항상 `Response` DTO다.
  (엔티티가 컨트롤러로 새면 지연로딩 예외·무한순환·민감필드 노출 사고로 이어진다.)
- **엔티티 변환** — `request.toEntity()` 호출은 Service 안에서 한다. Controller는 호출하지 않는다.
- **try/catch** — 예외는 Service가 의미 있는 예외로 던지고 `ExceptionHandler`가 처리한다.
- **DB/도메인 접근** — Repository나 도메인 객체를 직접 다루지 않는다.
- **인프라/라이브러리 객체 직접 생성·소유** — `Executor`, `WebClient`, AWS/HTTP 클라이언트 등
  라이브러리 자원을 Controller 안에서 `new`로 만들거나 필드로 들고 있지 않는다. 생명주기 관리
  (`@PreDestroy` 등)도 Controller의 일이 아니다. 이런 자원은 `config/` 하위에서 `@Bean`으로
  등록하고 생성자 주입으로 받는다. (§9)

### 0-2. 판단 기준

> Controller 메서드 본문에서 **`if`, `for`, 계산, 예외 처리**가 보이면
> 거의 다 Service로 내려가야 할 코드다.

정상적인 Controller 메서드는 보통 **1~2줄**이다.

---

## 1. Controller 클래스 구조

### 1-1. `@RestController` + `@RequiredArgsConstructor`

클래스 구조 예제 → reference.md §1

규칙:

- `@RestController`를 사용한다 (`@Controller` + `@ResponseBody` 조합 금지).
- 의존 Service는 **생성자 주입**으로 받는다. `@RequiredArgsConstructor`로 생성자를 생성한다.
- 필드는 `private final`로 선언한다. 필드 주입(`@Autowired` 필드)·세터 주입 금지.
- 클래스 레벨 `@RequestMapping`으로 공통 경로 prefix를 둔다.

### 1-2. HTTP 매핑

- HTTP 메서드별 축약 어노테이션을 쓴다: `@PostMapping`, `@GetMapping`, `@PutMapping`,
  `@DeleteMapping`, `@PatchMapping`. (`@RequestMapping(method=...)` 금지)
- 경로는 `/api/v{버전}/{리소스}` 형태로 버전을 명시한다. 예: `/api/v1/products`.
- 자원 중심으로 네이밍한다. 동작은 HTTP 메서드로 표현하고, 경로엔 명사를 둔다.

---

## 2. 반환 타입은 `ResponseEntity<ApiResponse<Response>>`

Controller 메서드의 반환 타입은 **`ResponseEntity<ApiResponse<*Response>>`** 로 통일한다.
기본 형태와 200/201/204 변형 예제 → reference.md §2

왜 `ResponseEntity`로 한 겹 더 감싸는가:

- **HTTP 상태 코드와 헤더를 명시적으로 제어**하기 위해서다. 본문(`ApiResponse`)만
  반환하면 상태 코드는 항상 200으로 고정되고, `Location` 같은 헤더를 실을 수 없다.
- 생성 201, 본문 없는 204, 리다이렉트, 캐시 헤더 등 표현이 필요할 때 `ResponseEntity`가
  그 자리를 만들어 준다.

세 겹의 역할이 다르다:

| 계층 | 책임 |
|---|---|
| `ResponseEntity<...>` | HTTP **상태 코드 + 헤더** (전송 계층 메타) |
| `ApiResponse<...>` | 공통 응답 **포맷** (`code/status/message/data`) — §5 |
| `*Response` | 실제 **데이터** (순수 DTO) — §3 |

---

## 3. Request DTO 규칙

### 3-1. 허용 어노테이션은 `@Getter` + 기본 생성자뿐

Request DTO 예제 → reference.md §3-1

- 클래스에는 **`@Getter`와 기본 생성자(`@NoArgsConstructor`) 두 어노테이션만** 허용한다.
- `@Setter`는 두지 않는다 (요청 객체는 역직렬화 후 변경되지 않는다).
- 기본 생성자는 Jackson 역직렬화에 필요하다. 외부에서 직접 호출할 일은 없으므로
  `access = PROTECTED` 권장.

### 3-2. 엔티티 생성은 Request의 정적 팩토리 메서드에서

Service에 엔티티가 필요하면 **Request가 자기 데이터로 엔티티를 만든다.**
`toEntity()` 예제 → reference.md §3-2

- 메서드 이름은 `toEntity()`를 기본으로 한다.
- **내부는 반드시 빌더 패턴**으로 엔티티를 생성한다 (생성자 직접 호출 대신).
- 이 메서드의 **호출 주체는 Service**다. Controller는 호출하지 않는다.
  (Controller는 Request를 그대로 Service에 넘기고, Service가 `request.toEntity()`를 호출한다.)

### 3-3. 검증은 Bean Validation으로

- 형식·범위 검증은 필드에 검증 어노테이션을 단다:
  `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Positive`, `@Min`, `@Max`, `@Size`, `@Pattern` 등.
- Controller 메서드 파라미터에 **`@Valid`를 반드시** 붙여야 검증이 동작한다.
- 검증 실패는 `MethodArgumentNotValidException`(또는 `BindException`)으로 던져지며,
  **`@RestControllerAdvice`의 `ExceptionHandler`가 잡아** 공통 에러 응답으로 변환한다.
- 검증 메시지(`message` 속성)는 사용자에게 노출되므로 명확한 한국어 문장으로 적는다.
  예: `@NotNull(message = "상품 타입은 필수입니다.")`
- **비즈니스 검증**(중복 여부, 재고 충분 여부 등)은 Bean Validation으로 풀지 않는다.
  이는 Service가 도메인 규칙으로 판단하고 의미 있는 예외를 던진다.

---

## 4. Response DTO 규칙

### 4-1. 응답은 Response 객체로 — Map 금지

- 응답 데이터는 전용 `*Response` DTO로 표현한다. **`Map`이나 익명 구조 반환 금지.**
- `Response`는 `@Getter`/`@Setter`를 모두 써도 무방하다 (Request와 달리 제약 없음).
  단 불변으로 두고 싶다면 `@Setter`를 빼도 된다 — 선택.

### 4-2. Response는 Service가 완성한다

- **Service가 `Response` DTO를 완성해서 반환**한다. Controller는 받은 Response를 그대로 쓴다.
- Controller에서 Response의 필드를 채우거나 가공하지 않는다 (가공은 곧 비즈니스 로직).
- 이 규칙 덕분에 엔티티는 Service 경계 밖으로 나오지 않는다.

### 4-3. Response DTO의 범위 — HTTP를 모르게 한다

Service가 Response를 안다는 건 Service가 표현 데이터에 의존한다는 뜻이다.
이 결합을 최소화하기 위해, **`Response`는 순수 데이터 객체로만** 둔다.

- `*Response`에는 도메인/응답 데이터만 담는다.
- `code`, `status`, `message` 같은 **HTTP 메타 정보는 Response에 넣지 않는다.**
  이는 공통 래퍼(`ApiResponse`, §5)와 `ResponseEntity`(§2)가 Controller 단에서 감싼다.
- 결과적으로 역할이 이렇게 나뉜다:
  - **Service**: `ProductResponse`(순수 데이터)까지 완성 → "Service가 Response 완성" 규칙 만족
  - **Controller**: `ResponseEntity.ok(ApiResponse.ok(response))`로 HTTP 메타·포맷만 씌움

> 이 분리 덕분에 "엔티티도 Controller에 안 새고, Service는 HTTP를 모르고, Response
> 본체는 Service가 완성한다"는 세 조건이 동시에 만족된다.

---

## 5. 공통 응답 래퍼 `ApiResponse<T>`

모든 응답 본문을 동일한 포맷으로 감싼다. (controller-test 스킬이 이 포맷을 검증한다.)
응답 JSON 포맷 예제 → reference.md §5

- 성공 응답: `ApiResponse.ok(data)` 형태의 정적 팩토리로 감싼다.
- 실패 응답: `@RestControllerAdvice`의 `ExceptionHandler`가 동일 포맷으로 변환한다
  (`data`는 null/empty).
- `data`의 타입은 §4의 `*Response` DTO다.
- `ApiResponse`는 본문 포맷만 책임진다. HTTP 상태 코드·헤더는 `ResponseEntity`(§2)가 담당한다.

응답 포맷이 바뀌면 모든 Controller와 Controller 테스트가 함께 영향을 받는다.
이는 의도된 결합이며, 포맷 변경 시 테스트가 알려주는 안전장치 역할을 한다.

---

## 6. 예외 처리 — Controller는 던지지도 잡지도 않는다

- Controller에는 **`try/catch`가 없다.**
- Service는 상황에 맞는 **의미 있는 예외**(도메인 예외 또는 명확한 커스텀 예외)를 던진다.
  - 나쁨: Service에서 `null` 반환 후 Controller가 분기.
  - 좋음: Service에서 `ProductNotFoundException` throw.
- 모든 예외는 `@RestControllerAdvice` + `@ExceptionHandler`가 한 곳에서 잡아
  공통 래퍼(`ApiResponse`) 에러 포맷으로 변환한다.
- 예외 타입별로 적절한 HTTP 상태 코드를 매핑한다 (검증 실패 → 400, 미존재 → 404 등).

---

## 7. 흐름 요약 — 단방향 데이터 흐름

데이터 흐름 다이어그램 → reference.md §7

이 방향을 거스르는 코드(엔티티가 Controller로 올라옴, Controller가 판단함,
Controller가 예외를 잡음)가 보이면 잘못된 신호다.

---

## 8. 인프라/라이브러리 자원은 `config`에서 빈으로 분리한다

`Executor`(스레드풀), HTTP/AWS 클라이언트, 외부 SDK 객체 같은 **라이브러리 자원은
Controller가 만들지도 소유하지도 않는다.** Controller는 그저 주입받아 쓴다.
before/after 예제 → reference.md §8

### 8-1. 왜 분리하는가

- **책임 위반** — Controller의 일은 HTTP 경계 변환이다. 스레드풀 크기·큐 용량·종료 처리
  같은 인프라 설정은 그 책임이 아니다.
- **생명주기** — `new`로 만든 자원은 누군가 닫아야 한다. Controller에 `@PreDestroy`가
  붙기 시작하면 그건 자원을 잘못된 곳에서 소유한다는 신호다. 빈으로 등록하면 스프링이
  생성·소멸을 관리한다(`destroyMethod`, `@PreDestroy` 모두 빈 쪽에서).
- **재사용·테스트** — 빈으로 분리하면 여러 컴포넌트가 같은 자원을 공유하고, 테스트에서
  쉽게 교체(mock/stub)할 수 있다.

### 8-2. 규칙

- 자원 생성 코드는 `config/{영역}` 패키지(예: `config/executor`)의 `@Configuration`
  클래스로 옮기고 `@Bean` 메서드로 등록한다.
- 종료가 필요한 자원은 빈 정의에서 생명주기를 지정한다
  (`@Bean(destroyMethod = "shutdown")` 등). Controller는 종료에 관여하지 않는다.
- Controller는 그 빈을 **`private final` 생성자 주입**으로 받는다 (§1의 의존성 주입 규칙과 동일).
- 빈이 여러 개라 충돌하면 `@Qualifier`나 빈 이름으로 구분한다.

### 8-3. 판단 기준

> Controller에서 `new ...Executor()`, `new ...Client()` 같은 **라이브러리 객체 생성**이나
> `@PreDestroy`/`@PostConstruct` 같은 **생명주기 콜백**이 보이면 `config`로 옮길 신호다.

---

## 9. 작성 체크리스트

Controller 계층 코드를 작성/수정한 뒤 점검한다.

### Controller

- [ ] `@RestController` + `@RequiredArgsConstructor`를 사용했는가?
- [ ] Service를 `private final` 생성자 주입으로 받는가? (필드/세터 주입 없음)
- [ ] 반환 타입이 `ResponseEntity<ApiResponse<*Response>>`인가?
- [ ] 메서드 본문에 `if`/`for`/계산/`try-catch`가 없는가? (있으면 Service로 이동)
- [ ] 엔티티를 반환하지 않고 `Response` DTO만 반환하는가?
- [ ] Request를 그대로 Service에 넘기는가? (`toEntity()`를 Controller에서 호출하지 않음)
- [ ] HTTP 매핑 축약 어노테이션과 `/api/v{n}/{resource}` 경로 컨벤션을 따르는가?
- [ ] `Executor`·클라이언트 등 라이브러리 자원을 `new`로 만들거나 소유하지 않고, `config`의
  빈을 주입받아 쓰는가? (`@PreDestroy`/`@PostConstruct`가 Controller에 없는가)

### Request DTO

- [ ] `@Getter`와 기본 생성자 두 어노테이션만 있는가? (`@Setter` 없음)
- [ ] 엔티티가 필요하면 `toEntity()` 팩토리가 있고, 내부가 빌더 패턴인가?
- [ ] 검증 어노테이션이 붙어 있고, Controller 파라미터에 `@Valid`가 있는가?
- [ ] 검증 메시지가 명확한 한국어 문장인가?
- [ ] 비즈니스 검증을 Bean Validation으로 풀려 하지 않았는가? (Service의 몫)

### Response DTO

- [ ] 응답이 `Map`이 아닌 `*Response` DTO인가?
- [ ] Response를 Service가 완성하고, Controller는 가공 없이 그대로 쓰는가?
- [ ] Response에 `code/status/message` 같은 HTTP 메타가 섞여 있지 않은가?

### 공통 / 예외

- [ ] 본문이 `ApiResponse`(code/status/message/data) 포맷으로 감싸지는가?
- [ ] Controller에 `try/catch`가 없고, 예외는 `@RestControllerAdvice`가 처리하는가?
- [ ] 예외 타입별로 적절한 HTTP 상태 코드가 매핑되는가?