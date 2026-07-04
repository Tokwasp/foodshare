# 이메일 전송 타임아웃(5초)이 실제로 동작하는지 검증한 방법

> 관련 코드: `application.yaml`(mail.smtp timeout), `MailService#sendVerificationCode`, `MailServiceTimeoutTest`

## 1. 고민의 시작

메일 발송은 외부 SMTP 서버에 의존하는 네트워크 I/O 작업이라,
상대 서버가 응답하지 않으면 우리 서버 스레드가 무한정 대기할 수 있다.
그래서 `application.yaml`에 타임아웃을 5초로 걸어 두었다.

```yaml
mail:
  properties:
    mail:
      smtp:
        connectiontimeout: 5000  # 연결(소켓 connect) 대기 한도
        timeout: 5000            # 응답(read) 대기 한도
        writetimeout: 5000       # 전송(write) 대기 한도
```

> **"그런데 이 5초 설정이 정말로 동작할까? 진짜로 5초 뒤에 예외가 나긴 할까?"**

설정만 적어두고 믿을 수는 없어서, 타임아웃이 실제로 발동하는지 직접 검증하기로 했다.

## 2. 검증 아이디어 — 응답하지 않는 서버가 필요하다

타임아웃은 "상대가 응답하지 않을 때" 발동한다.
따라서 **연결은 받아주지만 아무 응답도 돌려주지 않는 SMTP 서버**가 있어야 한다.

실제 Gmail SMTP로는 이 상황을 만들 수 없으니,
테스트 안에서 **임의의 빈 포트를 열어 놓고, 접속만 받고 침묵하는 가짜 서버(블랙홀)** 를 직접 띄우기로 했다.

- `new ServerSocket(0)` → OS가 비어 있는 임의 포트를 하나 잡아준다.
- 별도 스레드에서 `accept()` 만 반복 → 클라이언트 접속은 받아주되, SMTP 인사(220 greeting)를 **절대 보내지 않는다.**
- `MailService`의 목적지를 이 포트로 지정 → 메일 전송을 시도하면 응답을 영원히 기다리게 된다.

## 3. 어떤 타임아웃이 발동하는가

목적지가 `localhost`의 열린 포트라 **소켓 연결(connect) 자체는 즉시 성공**한다.
그래서 `connectiontimeout`이 아니라, 연결 후 SMTP 인사 응답을 기다리는
**`mail.smtp.timeout`(read timeout, 5초)** 이 발동한다.
5초가 지나면 JavaMail이 `SocketTimeoutException`을 던지고,
`MailService`가 이를 잡아 `MailSendException`(→ `EMAIL_SEND_FAILED`, 500)으로 변환한다.

## 4. 테스트 구현

핵심은 두 가지를 검증하는 것이다.
(1) 예외 타입이 `MailSendException`인가, (2) **즉시 실패가 아니라 진짜 5초를 기다린 뒤** 실패했는가.

```java
@DisplayName("SMTP 서버가 응답하지 않으면 timeout 대기 후 MailSendException이 발생한다.")
@Timeout(10)
@Test
void sendVerificationCodeFailsAfterTimeout() {
    MailService mailService = new MailService(createBlackholeMailSender());

    long start = System.currentTimeMillis();
    Throwable thrown = catchThrowable(
            () -> mailService.sendVerificationCode("test@example.com", "123456"));
    long elapsed = System.currentTimeMillis() - start;

    assertThat(thrown).isInstanceOf(MailSendException.class);
    assertThat(elapsed)
            .as("즉시 실패가 아니라 실제 timeout(5초) 대기 후 실패해야 한다")
            .isGreaterThanOrEqualTo(TIMEOUT_MILLIS - 500);
}
```

- 블랙홀 서버(`ServerSocket(0)` + accept 스레드)와 그 포트를 가리키는 `JavaMailSenderImpl`을 만들어 주입한다.
- `elapsed >= 4500ms` 검증이 포인트다. 만약 설정이 잘못돼 타임아웃이 안 걸리면 테스트가 `@Timeout(10)`에 걸려 실패하고,
  반대로 설정을 무시하고 즉시 실패하면 경과시간 검증에서 실패한다. → **5초 근처에서 실패해야만 통과**한다.

## 5. 이 테스트를 @Disabled로 둔 이유

이 테스트는 통과하려면 **매번 5초를 실제로 대기**해야 한다.
일반 빌드/CI에서 매번 5초씩 잡아먹는 것은 비효율적이라,
타임아웃 설정을 검증하고 싶을 때만 수동으로 돌리도록 `@Disabled`로 표시해 두었다.
(설정을 바꿨을 때 회귀 검증용으로 남겨 두는 성격의 테스트다.)

## 6. 정리

- **문제**: `mail.smtp.timeout: 5000` 설정이 실제로 동작하는지 확신할 수 없었다.
- **아이디어**: 접속만 받고 응답하지 않는 임의 포트의 "블랙홀 서버"를 띄워, 응답 없는 SMTP 상황을 재현.
- **결과**: 연결은 성공하지만 read timeout(5초) 후 `MailSendException`이 발생함을 확인.
  경과시간이 4.5초 이상인지 함께 검증해 "설정된 타임아웃이 실제로 걸린다"는 것을 보장.
- **운영**: 5초 대기 비용 때문에 평소엔 `@Disabled`, 설정 변경 시 수동 검증용으로 유지.
