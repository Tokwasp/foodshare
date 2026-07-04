---
name: exception
description: |
  Spring `@RestControllerAdvice` 전역 예외 처리와 그에 딸린
  `ErrorCode`(enum) · `ChatbotException`(커스텀 예외) · `ApiResponse` 에러 응답을
  새로 만들거나 수정할 때 적용한다.

  예외를 "어디서 던지고(Service)", "어떻게 한 곳에서 잡아(GlobalExceptionHandler)",
  "어떤 포맷으로 내려주는가(ApiResponse)"를 정의한다.

  Controller는 예외를 던지지도 잡지도 않는다 — 이는 controller 스킬과 짝을 이룬다.
---

# 예외 처리 규칙

이 스킬은 **비즈니스 레이어에서 발생하는 예외**와 **DB/프레임워크에서 발생하는 예외**를
HTTP 경계에서 **일관된 에러 응답**으로 변환하는 규칙을 정의한다.

핵심 질문은 하나다: **"이 예외 메시지를 사용자에게 그대로 보여줘도 되는가?"**
우리가 의도해서 던진 예외면 보여주고, 인프라/미상 예외면 절대 그대로 노출하지 않는다.

> 코드·JSON 예제는 모두 같은 패키지의 **`reference.md`** 에 있다. 각 규칙 옆의
> "→ reference.md §N" 표시를 따라 확인한다.

---

## 0. 예외는 한 곳에서만 처리한다 — `@RestControllerAdvice`

- 모든 예외는 `GlobalExceptionHandler`(`@RestControllerAdvice`) 한 곳에서 잡는다.
  → reference.md §5
- **Controller에는 `try/catch`가 없다.** Service가 의미 있는 예외를 던지고, 핸들러가 변환한다.
- Service/도메인은 `null` 반환으로 실패를 표현하지 않는다. **의미 있는 예외를 throw** 한다.
  - 나쁨: `return null;` 후 Controller가 분기
  - 좋음: `throw new SessionNotFoundException();` → reference.md §1

---

## 1. 신뢰 경계 — 우리 예외 vs 인프라/미상 예외 (가장 중요)

예외 메시지를 클라이언트에 그대로 노출할지는 **예외의 출처**로 결정한다.

| 출처 | 예시 | 사용자에게 보여줄 message | 로그 |
|------|------|--------------------------|------|
| **우리가 던진 비즈니스 예외** | `BusinessException` 하위 (`SessionNotFoundException` 등) | 예외의 메시지 그대로 (우리가 한국어로 통제) | `info` |
| **검증 실패** | `MethodArgumentNotValidException` | 필드 검증 메시지(한국어) | (생략 가능) |
| **DB 예외** | `DataAccessException` | **고정 친화 메시지** (원본 숨김) | `error` + 스택 |
| **미상 예외** | `Exception` (그 외 전부) | **고정 친화 메시지** (원본 숨김) | `error` + 스택 |

DB/미상 예외의 원본 메시지(`e.getMessage()`)를 그대로 내려주면 안 되는 이유:

- **보안** — SQL·테이블명·내부 구조가 새어 나간다.
- **UX** — 영어이고 길거나 `null`일 수 있다. (CLAUDE.md: 사용자 응답은 한국어)
- 원본은 **서버 로그에만** 남기고, 사용자에겐 `ErrorCode`의 고정 메시지를 보낸다.

---

## 2. 에러 식별자는 `ErrorCode` enum으로 중앙화한다

자유 문자열 대신 enum 하나에 **(HTTP 상태, 기본 메시지)** 를 묶는다. → reference.md §2

- enum 상수 이름(`name()`)이 곧 응답의 `errorCode` 문자열이다. (예: `SESSION_NOT_FOUND`)
- 새 에러를 추가할 때는 **enum에 상수 하나만** 추가한다 (코드·메시지·상태가 한 줄에 모임).
- 기본 메시지는 사용자에게 보여줄 **한국어 문장**으로 적는다.
- 같은 에러를 여러 곳에서 던져도 코드/상태/메시지가 한 곳에서 일관되게 관리된다.

---

## 3. 예외는 이름만 보아도 무엇인지 알 수 있게 — 베이스 + named 하위 예외

비즈니스 예외는 **이름 있는 하위 클래스**로 만든다. throw 지점과 스택트레이스에서
**이름만 봐도** 어떤 상황인지 파악되게 한다. → reference.md §3

- 추상 베이스 `BusinessException`이 `ErrorCode`를 들고 다닌다. 핸들러는 **이 베이스 하나만** 잡는다(§5).
- 실제로 던지는 건 이름 있는 하위 예외다: `SessionNotFoundException extends BusinessException`.
  - 기본형: `throw new SessionNotFoundException();`
  - 나쁨: `throw new BusinessException(...)` — 베이스를 직접 던지지 않는다(그래서 `abstract`).
- 각 named 예외는 생성자에서 **자신의 `ErrorCode`를 `super(...)`로** 넘긴다. HTTP 상태·기본 메시지는 enum이 안다.
- 메시지를 상황에 맞게 덮어쓰려면 `super(ErrorCode.INVALID_REQUEST, "구체 메시지")`.
- 새 비즈니스 예외를 추가할 때 = **(1) `ErrorCode` 상수 추가 + (2) 이름 있는 하위 예외 클래스 추가**.
- HTTP 상태 코드를 예외 생성 시점에 숫자로 직접 넘기지 않는다 (enum이 가진다).

---

## 4. 에러 응답 포맷 — `ApiResponse`에 `errorCode` 필드

성공/에러 모두 같은 `ApiResponse<T>`로 감싼다. → reference.md §4

| 필드 | 의미 | 성공 | 에러 |
|------|------|------|------|
| `code` | **HTTP 숫자** | 200 | 404 / 400 / 500 |
| `status` | **HTTP 이름** | `OK` | `NOT_FOUND` 등 |
| `errorCode` | **애플리케이션 에러 식별자**(`ErrorCode.name()`) | (생략) | `SESSION_NOT_FOUND` |
| `message` | 사용자 메시지 | `OK` | 한국어 메시지 |
| `data` | 응답 데이터 | 실제 데이터 | `null` |

- HTTP 메타(`code`/`status`)와 도메인 에러(`errorCode`)를 **분리**한다 — 의미가 섞이지 않는다.
- `errorCode`는 `@JsonInclude(NON_NULL)`로 둬서 **에러 응답에만** 나타난다(성공엔 빠짐).
- 성공은 `ApiResponse.ok(data)`, 에러는 `ApiResponse.error(status, errorCode, message)` 팩토리로 만든다.
- 프론트는 **`errorCode`(안정적인 식별자)로 분기**한다. `message`(언제든 바뀜)로 분기하지 않는다.

---

## 5. 핸들러 목록 — 예외 타입별 매핑

`GlobalExceptionHandler`는 다음 4종을 처리한다. → reference.md §5

| 핸들러 | 예외 | errorCode | message | HTTP | 로그 |
|--------|------|-----------|---------|------|------|
| 비즈니스 | `BusinessException` (하위 전부) | `e.getErrorCode().name()` | 예외 메시지 | enum 상태 | `info` |
| 검증 | `MethodArgumentNotValidException` | `INVALID_REQUEST` | 첫 필드 에러 메시지 | 400 | — |
| DB | `DataAccessException` | `DATA_ACCESS_ERROR` | 고정 메시지 | 500 | `error` |
| 미상 | `Exception` | `INTERNAL_ERROR` | 고정 메시지 | 500 | `error` |

- 비즈니스 핸들러는 **베이스 `BusinessException` 하나만** 잡으면 모든 named 하위 예외가 한 번에 처리된다.
- 구체 예외 핸들러가 우선하고, `Exception` 핸들러가 **최후 방어선**이다.
- 새 비즈니스 예외 종류가 늘어도 비즈니스 핸들러는 **그대로**다(베이스를 잡으므로). 인프라 예외 종류가 늘면 위 표에 행을 추가한다.

---

## 6. 로깅 정책

- **비즈니스 예외(`BusinessException` 하위)** → `log.info`. 대부분 예상된 클라이언트 오류(4xx)라
  경고/알람 대상이 아니다. 스택트레이스 없이 `errorCode`와 메시지만 남긴다.
- **DB/미상 예외** → `log.error`로 **스택트레이스까지** 남긴다 (원인 추적이 필요).
- 로그 메시지는 **영어**, 사용자 응답 메시지는 **한국어** (CLAUDE.md 원칙).

---

## 7. 작성 체크리스트

- [ ] 모든 예외가 `@RestControllerAdvice` 한 곳에서 처리되는가? (Controller에 `try/catch` 없음)
- [ ] Service는 `null` 대신 이름 있는 비즈니스 예외(`BusinessException` 하위)를 던지는가?
- [ ] 베이스 `BusinessException`은 `abstract`이고, 실제로는 named 하위 예외만 던지는가?
- [ ] 새 에러를 `ErrorCode` enum 상수 + named 하위 예외로 추가했는가? (상태·메시지가 한 곳에)
- [ ] DB/미상 예외의 원본 메시지를 사용자에게 노출하지 않고 고정 메시지로 바꿨는가?
- [ ] 원본 예외를 `log.error`(DB/미상) / `log.info`(비즈니스)로 남겼는가?
- [ ] 에러 응답이 `ApiResponse`(code/status/errorCode/message/data) 포맷인가?
- [ ] `errorCode`가 `ErrorCode.name()`과 일치하고, 프론트가 그걸로 분기할 수 있는가?
- [ ] 사용자 메시지는 한국어, 로그 메시지는 영어인가?
