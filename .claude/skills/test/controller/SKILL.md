---
name: controller-test
description: |
  `src/test/java` 하위에서 컨트롤러 테스트 코드를 작성하거나 수정할 때 적용된다.
  컨트롤러 테스트는 `@WebMvcTest` 또는 `MockMvc`를 사용하는 테스트로 식별한다.

  순수 Java 가독성(readable-java)보다 본 스킬이 우선한다.
---

# Controller 테스트 작성 규칙

이 스킬은 Spring `@RestController`/`@Controller` 빈을 검증하는 테스트 코드를
작성할 때 따라야 하는 규칙을 정의한다.

구체적인 코드 예시는 같은 디렉토리의 `reference.md`를 참조한다.

---

## 0. Controller 테스트의 책임 — "무엇을 검증할 것인가"

Controller 테스트는 **HTTP 계약**만 검증한다. 비즈니스 로직 검증은 Service 테스트의 몫이다.

### 0-1. 테스트해야 하는 것

- **HTTP 메서드 / 경로 / 상태 코드** — `POST /api/v1/products/new` → 200
- **요청 본문의 JSON 역직렬화** — `ObjectMapper`로 변환된 `Request` DTO가 올바르게 매핑되는가
- **응답 본문의 JSON 직렬화 포맷** — `jsonPath`로 `code`, `status`, `message`, `data` 구조 확인
- **Bean Validation 동작** — `@NotNull`, `@NotBlank`, `@Positive`, `@NotEmpty` 등이
  실제로 동작하여 400 Bad Request로 매핑되는가
- **ControllerAdvice의 에러 응답 변환** — `BindException`이 정의된 포맷으로 변환되는가

### 0-2. 테스트하지 말아야 하는 것

- **비즈니스 로직** — Service에서 검증. Controller 테스트에서는 Service를 `@MockBean`으로 격리.
- **DB 상태** — Repository/Service 테스트의 책임. Controller 테스트는 DB를 띄우지 않는다.
- **Service의 내부 동작** — `productService.createProduct(...)`가 어떻게 동작하는지는
  관심사가 아니다. **호출되었다는 사실** 자체도 검증할 필요는 거의 없다.
  (호출 검증이 의미 있는 경우는 드물다.)

---

## 1. 테스트 클래스의 기본 구조

### 1-1. `ControllerTestSupport`를 상속한다

모든 Controller 테스트가 상속하는 공통 추상 상위 클래스를 둔다.

→ 코드 예시: reference.md §1

이 클래스의 역할:

- `@WebMvcTest(controllers = {...})`에 모든 Controller를 모아두어 **하나의 슬라이스
  컨텍스트를 재사용**한다. 매 테스트마다 새 컨텍스트를 띄우지 않아 빠르다.
- `MockMvc`, `ObjectMapper`를 `protected` 필드로 노출하여 하위 클래스에서 바로 사용.
- 모든 Service를 `@MockBean`으로 격리 → DB/비즈니스 로직 없이 Controller만 검증한다.

### 1-2. 새 Controller를 추가하면 `ControllerTestSupport`도 함께 수정한다

- `@WebMvcTest`의 `controllers` 배열에 새 Controller 클래스를 추가.
- 새 Controller가 의존하는 Service를 `@MockBean`으로 추가.

이 작업을 빼먹으면 새 Controller 테스트가 컨텍스트 로딩 단계에서 실패한다.

### 1-3. 하위 테스트 클래스

```java
class ProductControllerTest extends ControllerTestSupport {
    // 필드 선언 없이 바로 테스트 메서드부터 시작
}
```

- `mockMvc`, `objectMapper`, `productService`(Mock)는 상위에서 상속받은 `protected`
  필드로 사용한다.
- `@WebMvcTest`도, `@MockBean`도 하위에 중복 선언하지 않는다.

---

## 2. 테스트 메서드 작성 규칙

### 2-1. `@DisplayName`은 한국어 문장으로 "보장되는 것"을 적는다

- ✅ `"신규 상품을 등록한다."`
- ✅ `"신규 상품을 등록할 때 상품 타입은 필수값이다."`
- ✅ `"신규 상품을 등록할 때 상품 가격은 양수이다."`
- ✅ `"신규 주문을 등록할 때 상품번호는 1개 이상이어야 한다."`
- ❌ `"createProduct 테스트"` — 메서드명 반복
- ❌ `"validation 테스트"` — 무엇이 보장되는지 불명확

### 2-2. given / when // then 구조

`MockMvc` 호출은 when과 then을 분리하기 어렵다 (`.perform(...).andExpect(...)`로
체이닝되기 때문). 그래서 **`// when // then` 한 줄로 묶는다**.

→ 코드 예시: reference.md §2

### 2-3. 메서드 시그니처

- `throws Exception` — `MockMvc.perform`이 체크 예외를 던지므로 필요.
- 메서드명은 정상 케이스: `createProduct`, `getSellingProducts`
- 변형 케이스는 의도를 덧붙임:
  - `createProductWithoutType` — 필수 필드 누락
  - `createProductWithoutSellingStatus`
  - `createProductWithoutName`
  - `createProductWithZeroPrice` — 경계값
  - `createOrderWithEmptyProductNumbers` — 빈 리스트

---

## 3. 요청 작성 규칙

### 3-1. POST 요청 (JSON 본문)

```java
mockMvc.perform(
        post("/api/v1/products/new")
            .content(objectMapper.writeValueAsString(request))
            .contentType(MediaType.APPLICATION_JSON)
    )
```

- `objectMapper.writeValueAsString(request)` 패턴을 일관되게 사용한다.
- `contentType(MediaType.APPLICATION_JSON)`을 **반드시** 명시한다. 빼면 415 Unsupported
  Media Type이 떨어진다.
- `MediaType.APPLICATION_JSON_VALUE`(문자열 상수) 대신 `MediaType.APPLICATION_JSON`(객체)을 사용.

### 3-2. GET 요청

```java
mockMvc.perform(
        get("/api/v1/products/selling")
    )
```

- 쿼리 파라미터는 `.param("key", "value")`로 추가.
- GET은 본문이 없으므로 `contentType` 불필요.

### 3-3. import 규칙

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
```

전부 static import한다. `MockMvcRequestBuilders.post(...)`처럼 풀네임으로 쓰지 않는다.

### 3-4. `.andDo(print())`

- **모든 테스트에 일관되게 추가**한다. 실패 시 요청/응답 전문이 콘솔에 출력되어
  디버깅이 빠르다.
- 성공 시에는 출력이 무시되거나 큰 비용이 없다.

---

## 4. 응답 검증 규칙

### 4-1. 상태 코드 검증

```java
.andExpect(status().isOk())            // 200
.andExpect(status().isBadRequest())    // 400
.andExpect(status().isNotFound())      // 404
.andExpect(status().isCreated())       // 201
```

- 숫자 리터럴(`.is(200)`)보다 의미 있는 메서드(`.isOk()`)를 우선.

### 4-2. JSON 본문 검증은 `jsonPath`로

공통 응답 포맷이 `{ code, status, message, data }` 인 경우:

```java
.andExpect(jsonPath("$.code").value("200"))
.andExpect(jsonPath("$.status").value("OK"))
.andExpect(jsonPath("$.message").value("OK"))
.andExpect(jsonPath("$.data").isArray());
```

**규칙:**

- `$.필드명`으로 JSON 루트 기준 경로 표기.
- 단일 값: `.value(...)`
- 배열 여부: `.isArray()`
- 객체 여부: `.isMap()`
- 비어있음: `.isEmpty()` (null이거나 빈 컬렉션)
- 존재 여부만: `.exists()` / `.doesNotExist()`

### 4-3. 에러 응답 검증 (validation 실패)

```java
.andExpect(status().isBadRequest())
.andExpect(jsonPath("$.code").value("400"))
.andExpect(jsonPath("$.status").value("BAD_REQUEST"))
.andExpect(jsonPath("$.message").value("상품 타입은 필수입니다."))
.andExpect(jsonPath("$.data").isEmpty())
```

- **메시지 문자열은 `Request` DTO의 validation 어노테이션 `message` 속성과 정확히
  일치**해야 한다.
- 메시지 변경은 DTO와 테스트를 함께 수정해야 한다 (강한 결합이지만 의도된 것).

### 4-4. 검증 라인 정렬

여러 `.andExpect`가 이어질 때는 들여쓰기를 일관되게 정렬한다.

→ 코드 예시: reference.md §3, §4

스타일 규칙:

- `mockMvc.perform(...)`의 인자는 들여쓰기 한 단계 안쪽으로.
- `.andDo`, `.andExpect`는 perform 호출과 같은 들여쓰기 깊이로 체이닝.
- 끝에 빈 `;` 라인을 두는 스타일도 있다 — 줄 추가/삭제 시 diff가 깔끔해진다.
  팀 규칙으로 채택 여부는 선택.

---

## 5. Service Mocking

### 5-1. 호출 결과가 응답에 반영되는 경우만 stubbing

→ 코드 예시: reference.md §6

- **Service 응답이 Controller의 응답 본문에 들어가는 경우**만 stubbing한다.
- POST 요청의 정상 케이스에서는 Service 호출 결과를 검증하지 않으므로 stubbing이
  불필요할 수 있다 (Mockito는 stubbing 없으면 `null` 반환 — Controller가 그것을
  그대로 응답에 담아도 200이 떨어지면 통과).
- 그러나 응답에 `data` 필드를 검증하고 싶다면 stubbing이 필수.

### 5-2. `given().willReturn()` vs `when().thenReturn()`

- Controller 테스트의 기본 스타일은 `when().thenReturn()`이다.
- Service 스킬에서는 BDD 스타일(`given().willReturn()`)을 권장했지만, **Controller
  테스트는 `when().thenReturn()`**을 그대로 사용한다.
- 팀에서 BDD 스타일로 통일하려면 둘 다 BDD로 바꿔도 무방하다.

### 5-3. 호출 검증(`verify`)은 거의 쓰지 않는다

Controller 테스트는 **계약(HTTP 입출력)을 검증**하지 Service 호출 자체를 검증하지 않는다.
응답이 올바르면 Service가 적절히 호출된 것으로 간주한다.

---

## 6. Validation 테스트 — 경계와 분기를 빠짐없이

`Request` DTO의 모든 validation 어노테이션마다 **별도 테스트**를 작성한다.

→ 코드 예시: reference.md §3 (`@NotNull`), §4 (`@NotBlank`), §5 (`@Positive` 경계값), §7 (`@NotEmpty`)

### 6-1. 어노테이션별 검증 케이스

| 어노테이션 | 위반 입력 |
|---|---|
| `@NotNull` | 필드 자체를 빌더에서 제외 (null) |
| `@NotBlank` | 필드 자체 제외 또는 `""` / `"   "` |
| `@NotEmpty` | 빈 리스트 `List.of()` 또는 빈 문자열 |
| `@Positive` | `0` 또는 음수 |
| `@Min(N)` | `N-1` |
| `@Max(N)` | `N+1` |
| `@Size(min=N)` | `N-1` 길이 문자열/리스트 |
| `@Pattern(regex)` | 정규식 매칭 안 되는 문자열 |

### 6-2. 테스트 메서드 네이밍

- `create{대상}Without{필드}` — 필수 필드 누락
- `create{대상}With{조건}` — 잘못된 조건 (예: `WithZeroPrice`, `WithEmptyProductNumbers`)

### 6-3. 경계값 테스트 권장 사항

- `@Positive`: `0`은 양수가 아님 → 실패 케이스. **`1`도 별도로 테스트하지 않음**
  (정상 케이스에 포함됨).
- `@Min(1)`: `0` 실패, `1` 통과 — 경계 양쪽을 모두 확인하려면 두 테스트로 분리.
- 매 경계마다 분리하지 않고 **대표 케이스 하나**만 두는 것을 권장한다 — 과도하게 늘리지 않는다.

---

## 7. 자주 발생하는 함정

### 7-1. `@WebMvcTest`에 `@Repository` 빈은 올라오지 않는다

- `@WebMvcTest`는 Web 슬라이스만 로딩한다 (`@Controller`, `@ControllerAdvice`,
  `@JsonComponent`, `Filter`, `HandlerInterceptor`, `WebMvcConfigurer` 등).
- Service, Repository, JPA 빈은 올라오지 않는다.
- Controller가 의존하는 Service는 **반드시 `@MockBean`** (또는 `@Import`로 다른 빈)으로 채워야 한다.

### 7-2. `@EnableJpaAuditing` 같은 설정이 메인 클래스에 있으면 컨텍스트 로딩 실패

- 메인 `@SpringBootApplication`에 `@EnableJpaAuditing`이 붙어 있으면 `@WebMvcTest`가
  JPA 빈을 찾으려다 실패한다.
- 해결: JPA Auditing 설정을 별도 `@Configuration` 클래스로 분리하여 `@SpringBootApplication`이
  스캔하는 위치에서 분리하거나, 테스트에서 `@MockBean(JpaMetamodelMappingContext.class)`를 추가.

### 7-3. JSON 직렬화 시 `@NoArgsConstructor`가 없으면 역직렬화 실패

- Jackson은 기본 생성자가 필요하다.
- `Request` DTO에 `@NoArgsConstructor` (`@Getter`와 함께) 또는 `@JsonCreator`를 둔다.
- 권장 패턴: `@NoArgsConstructor` + `@Getter` + `@Builder` 조합.

### 7-4. `@PathVariable` / `@RequestParam` 검증

- `@RequestBody` validation은 `BindException`/`MethodArgumentNotValidException`으로
  처리되지만, `@PathVariable`/`@RequestParam`의 `@Min`, `@NotBlank` 등은
  `ConstraintViolationException`으로 처리된다.
- 컨트롤러 클래스에 `@Validated` 어노테이션이 필요하고, `ControllerAdvice`에 해당
  예외 핸들러도 추가해야 한다.
- 이 경우 테스트는 `status().isBadRequest()`까지는 동일하지만, 에러 메시지 포맷이 다를 수 있다.

---

## 8. 공통 응답 포맷과의 결합

모든 응답을 `ApiResponse<T>` 포맷으로 감싸는 컨벤션을 가정한다:

```json
{ "code": 200, "status": "OK", "message": "OK", "data": {...} }
```

→ `ApiResponse<T>`와 `ApiControllerAdvice`의 구현 코드: reference.md §0

**Controller 테스트는 이 포맷을 신뢰하고 검증**한다. 즉:

- 성공 응답: `code`, `status`, `message`, `data` 4개 필드를 검증
- 실패 응답: `code`, `status`, `message`, `data` 4개 필드를 검증 (data는 null/empty)

`ApiResponse` 포맷이 바뀌면 모든 Controller 테스트가 함께 깨진다. 이것은 의도된
결합이며, 응답 포맷 변경 시 테스트가 알려주는 안전장치 역할을 한다.

---

## 9. import 규칙 요약

```java
// MockMvc
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Mockito
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

// MediaType은 객체 import
import org.springframework.http.MediaType;
```

---

## 10. 체크리스트

Controller 테스트를 작성/수정한 뒤 아래를 확인한다:

### 클래스 구조

- [ ] `ControllerTestSupport`를 상속하는가?
- [ ] 새 Controller라면 `ControllerTestSupport`의 `controllers` 배열과 `@MockBean`에 추가했는가?
- [ ] 하위 클래스에 `@WebMvcTest`, `@MockBean`을 중복 선언하지 않았는가?

### 메서드 작성

- [ ] `throws Exception`을 선언했는가?
- [ ] `@DisplayName`이 한국어로 "무엇이 보장되는지"를 설명하는가?
- [ ] `// when // then` 구조로 작성되어 있는가? (MockMvc는 한 줄로 묶음)
- [ ] 정상 케이스 메서드명은 Controller 메서드명을 따르는가?
- [ ] 변형 케이스는 `WithoutX`, `WithX` 등으로 의도를 드러내는가?

### 요청

- [ ] POST: `objectMapper.writeValueAsString()` + `contentType(MediaType.APPLICATION_JSON)` 패턴인가?
- [ ] `.andDo(print())`가 포함되어 있는가?

### 응답 검증

- [ ] 상태 코드를 의미 있는 메서드(`isOk`, `isBadRequest` 등)로 검증하는가?
- [ ] `code`, `status`, `message`, `data` 4개 필드를 일관되게 검증하는가?
- [ ] validation 실패 메시지가 `Request` DTO의 `message` 속성과 정확히 일치하는가?

### Validation

- [ ] `Request` DTO의 모든 validation 어노테이션마다 별도 테스트가 있는가?
- [ ] 경계값(`0`, 빈 리스트 등)이 의도적으로 포함되어 있는가?

### Service Mocking

- [ ] 응답 본문에 반영되는 Service 호출만 stubbing되어 있는가?
- [ ] DB 의존 비즈니스 로직을 Controller 테스트에서 검증하려 하지 않았는가? (Service 테스트의 몫)
