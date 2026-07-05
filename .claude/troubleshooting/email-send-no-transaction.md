# 이메일 인증 코드 전송에 트랜잭션을 적용하지 않은 이유

## 1. 고민의 시작
**"메일을 전송하는 도중 타임아웃이 발생하면 어떻게 될까?"** <br>
메일 발송은 외부 SMTP 서버에 의존하는 **네트워크 I/O 작업**이므로 상대 서버가 응답하지 않으면 기다리는 시간이 길어질 수 있습니다.

---
## 2. 트랜잭션 안에서 메일 전송을 해도될까?
만약 메일 전송 과정에 트랜잭션을 걸면 메일 전송이 끝날 때까지 **DB 커넥션을 계속 점유**하게 됩니다.

```
[트랜잭션 시작] → DB 커넥션 획득
   ├─ 이메일 중복 조회 (DB 사용)
   ├─ 인증 코드 Redis 저장 (DB 불필요)
   └─ 메일 전송 (외부 I/O) ← 여기서 타임아웃 발생 시 커넥션을 계속 쥐고 대기
[트랜잭션 종료] → 그제서야 커넥션 반납
```
- 메일 전송이 지연되는 동안에도 커넥션은 트랜잭션에 묶여 반납되지 못하여 시스템 성능 저하를 발생 시킬 수 있습니다.

---
## 3. DB가 정말 필요한 구간은?

| 순서 | 작업 | DB(JPA) 필요 여부 |
|------|------|-------------------|
| 1 | 이메일 가입 여부 검증 (`existsByEmail`) | ✅ 필요 |
| 2 | 인증 코드 생성 | ❌ 불필요 |
| 3 | 인증 코드를 Redis에 임시 저장 | ❌ 불필요 (Redis) |
| 4 | 메일 전송 (외부 SMTP) | ❌ 불필요 (외부 I/O) |

- DB 커넥션이 실제로 필요한 부분은 1번 '이메일 조회' 단 한번 입니다.
- 단건 조회는 트랜잭션 없이도 동작하며, 원자성을 보장해야 할 다중 쓰기 작업이 없습니다.
- 즉 메일 전송 메서드에는 **롤백으로 보호해야 할 DB 쓰기 작업 자체가 존재하지 않습니다.**

---
## 4. 결론 — 메일 전송에 트랜잭션 적용X

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public EmailSendResponse sendVerificationCode(String email) {
    if (memberRepository.existsByEmail(email)) {
        throw new EmailDuplicatedException();
    }
    String code = verificationCodeGenerator.generate();
    saveVerificationCode(email, code);          // Redis
    mailService.sendVerificationCode(email, code); // 외부 SMTP I/O
    return EmailSendResponse.of(VERIFICATION_CODE_EXPIRES_IN_SECONDS);
}
```

## 5. 정리
- **문제**: 트랜잭션 안에서 메일을 보내면 외부 I/O 지연(타임아웃) 동안 DB 커넥션을 계속 점유 → 커넥션 풀 고갈 → 성능 저하.
- **분석**: 해당 로직에서 DB가 필요한 구간은 '이메일 중복 조회' 단건뿐. 인증 코드는 Redis에 저장. 롤백으로 보호할 DB 쓰기 작업이 없음.
- **결정**: 트랜잭션을 적용하지 않음 (`Propagation.NOT_SUPPORTED`로 명시)
