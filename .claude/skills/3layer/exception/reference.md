# 예외 처리 코드 예제

`SKILL.md`의 각 규칙에서 "→ reference.md §N"으로 가리키는 코드·JSON 예제 모음이다.
규칙 설명은 SKILL.md에 있고, 여기에는 예제와 짧은 캡션만 둔다.

---

## §1. 예외를 던지는 쪽 (Service / 도메인)

`null` 반환 대신 **이름 있는** 예외를 던진다. 호출부(Controller)는 받지 않고 그대로 전파한다.

```java
// Service / Controller 내부 — 없는 세션 접근
public Session requireSession(String id) {
    return sessionManager.get(id)
            .orElseThrow(SessionNotFoundException::new);
}
```

이름만 봐도 무슨 상황인지 드러난다(`SessionNotFoundException`). 메시지를 상황에 맞게
덮어써야 하는 예외는 하위 예외 생성자에서 `super(ErrorCode.X, "구체 메시지")`로 넘긴다.

---

## §2. `ErrorCode` enum

(HTTP 상태, 기본 메시지)를 한 줄에 묶는다. `name()`이 곧 응답의 `errorCode`다.

```java
@Getter
public enum ErrorCode {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    DATA_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "데이터 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
```

---

## §3. 베이스 `BusinessException` + named 하위 예외

베이스는 `abstract`이고 `ErrorCode` 하나만 들고 다닌다. HTTP 상태·기본 메시지는 enum이 안다.
핸들러가 잡는 대상이 바로 이 베이스다(§5).

```java
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

실제로 던지는 건 이름 있는 하위 예외다. 자신의 `ErrorCode`를 `super(...)`로 고정한다.

```java
public class SessionNotFoundException extends BusinessException {

    public SessionNotFoundException() {
        super(ErrorCode.SESSION_NOT_FOUND);
    }
}
```

---

## §4. `ApiResponse` 에러 포맷

`errorCode`는 `@JsonInclude(NON_NULL)`이라 에러 응답에만 나타난다.

```java
@Getter
public class ApiResponse<T> {

    private final int code;        // HTTP 숫자
    private final String status;   // HTTP 이름

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String errorCode; // 애플리케이션 에러 식별자

    private final String message;
    private final T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), HttpStatus.OK.name(), null, HttpStatus.OK.name(), data);
    }

    public static <T> ApiResponse<T> error(HttpStatus httpStatus, String errorCode, String message) {
        return new ApiResponse<>(httpStatus.value(), httpStatus.name(), errorCode, message, null);
    }
}
```

성공 응답 — `errorCode` 없음:

```json
{
  "code": 200,
  "status": "OK",
  "message": "OK",
  "data": { "id": "session-1", "title": "내 채팅", "userId": "user-1" }
}
```

에러 응답 — `errorCode` 포함, `data`는 null:

```json
{
  "code": 404,
  "status": "NOT_FOUND",
  "errorCode": "SESSION_NOT_FOUND",
  "message": "세션을 찾을 수 없습니다.",
  "data": null
}
```

---

## §5. `GlobalExceptionHandler`

구체 예외 핸들러가 우선하고, `Exception` 핸들러가 최후 방어선이다.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.info("Business error [{}]: {}", errorCode.name(), e.getMessage());
        return build(errorCode.getStatus(), errorCode.name(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return build(ErrorCode.INVALID_REQUEST.getStatus(), ErrorCode.INVALID_REQUEST.name(), message);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException e) {
        log.error("Data access error", e);
        ErrorCode errorCode = ErrorCode.DATA_ACCESS_ERROR;
        return build(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return build(errorCode.getStatus(), errorCode.name(), errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status, errorCode, message));
    }
}
```

---

## §6. 신뢰 경계 — 메시지 노출 차이

같은 "메시지 노출"이라도 출처에 따라 다르게 처리한다.

```java
// 우리 예외: 우리가 통제한 한국어 메시지를 그대로 노출 (예상된 흐름이라 info)
log.info("Business error [{}]: {}", errorCode.name(), e.getMessage());
return build(errorCode.getStatus(), errorCode.name(), e.getMessage());   // ← e.getMessage() 노출 OK

// DB/미상 예외: 원본은 로그에만, 사용자에겐 고정 메시지
log.error("Data access error", e);                                       // ← 원본은 여기에만
return build(errorCode.getStatus(), errorCode.name(), errorCode.getMessage()); // ← e.getMessage() 노출 X
```
