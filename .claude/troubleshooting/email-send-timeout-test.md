# 이메일 전송 Time-Out 테스트

## 1. 고민의 시작
메일 발송 예외 상황을 생각하여 설정 파일에 타임아웃을 5초로 설정했습니다. <br>
그런데 타임 아웃 5초 설정이 정말로 동작하는지 궁금하여 테스트가 필요했는데 어떻게 테스트 할지가 고민되었습니다.

```yaml
mail:
  properties:
    mail:
      smtp:
        connectiontimeout: 5000  # 연결(소켓 connect) 대기 한도
        timeout: 5000            # 응답(read) 대기 한도
        writetimeout: 5000       # 전송(write) 대기 한도
```
---
## 2. 검증 아이디어 — 응답하지 않는 서버 필요

타임아웃은 상대 서버가 응답하지 않을 때 발생하기 때문에 연결은 되지만 응답은 하지않는 SMTP 서버가 필요했습니다. <br>
실제 Gmail SMTP로는 이 상황을 만들 수 없으니 테스트 안에서 **임의의 서버 포트를 열어두고 접속만 받고 침묵하는 가짜 서버**를 사용했습니다.
- `new ServerSocket(0)` → OS가 비어 있는 임의 포트 사용
- 별도 스레드에서 `accept()` 만 반복 → 클라이언트 접속은 받지만 응답은 하지않습니다.
- 메일 요청 목적지를 해당 서버로 지정 → 메일 전송을 시도하면 응답을 기다립니다.

---
## 3. 어떤 타임아웃이 발동하는가
- 목적지가 `localhost`의 서버 포트라 소켓 연결 자체는 즉시 성공
- 하지만 서버가 응답이 없기때문에 5초 후 JavaMail이 SocketTimeoutException 예외 발생하고 MailService가 MailSendException 커스텀 예외로 변환

---
## 4. 테스트 구현
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
- 가짜 서버(`ServerSocket(0)` + accept 스레드)와 그 포트를 가리키는 `JavaMailSenderImpl`을 만들어 주입

## 5. 이 테스트를 @Disabled로 둔 이유
- 테스트를 통과하려면 매번 5초를 실제로 대기 필요
- 일반 빌드/CI에서 매번 5초씩 잡아먹는 것은 비효율적
- 타임아웃 설정을 검증하고 싶을 때만 수동으로 사용 +  해당 내용을 문서의 의미로 남겨두고자 `@Disabled`로 표시

## 6. 정리
- **문제**: `mail.smtp.timeout: 5000` 설정이 실제로 동작하는지 확신할 수 없었다.
- **아이디어**: 접속만 받고 응답하지 않는 임의 포트의 "블랙홀 서버"를 띄워, 응답 없는 SMTP 상황을 재현
- **결과**: 연결은 성공하지만 read timeout(5초) 후 `MailSendException`이 발생함을 확인
- **운영**: 5초 대기 비용 때문에 평소엔 `@Disabled`, 설정 변경 시 수동 검증용으로 유지
