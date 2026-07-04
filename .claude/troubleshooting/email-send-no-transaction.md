# 이메일 인증 코드 전송에 트랜잭션을 적용하지 않은 이유

> 관련 코드: `MemberService#sendVerificationCode`, `MailService#sendVerificationCode`

## 1. 고민의 시작

이메일 인증 코드 전송 로직을 구현하면서 한 가지 의문이 생겼다.

> **"메일을 전송하는 도중 타임아웃이 발생하면 어떻게 될까?"**

메일 발송은 외부 SMTP 서버에 의존하는 **네트워크 I/O 작업**이다.
상대 서버가 느리거나 응답하지 않으면 응답을 기다리는 시간이 길어질 수 있다.

## 2. 문제 인식 — 트랜잭션 안에서 메일을 보내면?

만약 이 메서드 전체에 트랜잭션(`@Transactional`)을 걸어 두면,
메일 전송이 끝날 때까지 **DB 커넥션을 계속 점유**하게 된다.

```
[트랜잭션 시작] → DB 커넥션 획득
   ├─ 이메일 중복 조회 (DB 사용)
   ├─ 인증 코드 Redis 저장 (DB 불필요)
   └─ 메일 전송 (외부 I/O) ← 여기서 타임아웃 발생 시 커넥션을 계속 쥐고 대기
[트랜잭션 종료] → 그제서야 커넥션 반납
```

- 메일 전송이 지연되는 동안에도 커넥션은 트랜잭션에 묶여 반납되지 못한다.
- 요청이 몰리면 커넥션 풀이 빠르게 고갈되고, **전체 시스템 성능 저하**로 이어진다.
- 정작 메일 전송 단계에서는 **DB가 전혀 필요하지 않은데도** 자원을 낭비하는 셈이다.

## 3. 로직 흐름 분석 — DB가 정말 필요한 구간은?

`sendVerificationCode`의 실제 처리 순서를 보면:

| 순서 | 작업 | DB(JPA) 필요 여부 |
|------|------|-------------------|
| 1 | 이메일 가입 여부 검증 (`existsByEmail`) | ✅ 필요 |
| 2 | 인증 코드 생성 | ❌ 불필요 |
| 3 | 인증 코드를 Redis에 임시 저장 | ❌ 불필요 (Redis) |
| 4 | 메일 전송 (외부 SMTP) | ❌ 불필요 (외부 I/O) |

**JPA(DB 커넥션)가 실제로 필요한 부분은 1번 '이메일 조회' 단 한 번뿐**이다.

- 단건 조회는 트랜잭션 없이도 동작하며, 원자성을 보장해야 할 다중 쓰기 작업이 없다.
- 인증 코드 저장은 DB가 아닌 Redis에서 이뤄지므로 JPA 트랜잭션 범위 밖이다.
- 즉, 이 메서드에는 **롤백으로 보호해야 할 DB 쓰기 작업 자체가 존재하지 않는다.**

## 4. 결론 — 트랜잭션을 적용하지 않음

위 분석을 근거로, 메일 전송 로직에는 트랜잭션을 적용하지 않기로 결정했다.

- 원자성으로 묶을 다중 DB 쓰기 작업이 없다.
- 가장 느린 외부 I/O(메일 전송)가 DB 커넥션을 점유하는 상황을 원천 차단한다.
- 커넥션은 필요한 조회 시점에만 짧게 사용되고 즉시 반납되어 풀 고갈 위험이 없다.

실제로 클래스에는 `@Transactional(readOnly = true)`가 걸려 있지만,
`sendVerificationCode`에는 다음과 같이 트랜잭션을 명시적으로 **비활성화**했다.

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

`Propagation.NOT_SUPPORTED`는 호출 시 진행 중인 트랜잭션이 있다면 잠시 보류(suspend)하고,
**트랜잭션 없이** 메서드를 실행한다. 이로써 메일 전송이 길어져도 DB 커넥션을 붙잡지 않는다.

## 5. 정리

- **문제**: 트랜잭션 안에서 메일을 보내면 외부 I/O 지연(타임아웃) 동안 DB 커넥션을 계속 점유 → 커넥션 풀 고갈 → 성능 저하.
- **분석**: 해당 로직에서 DB가 필요한 구간은 '이메일 중복 조회' 단건뿐. 인증 코드는 Redis에 저장. 롤백으로 보호할 DB 쓰기 작업이 없음.
- **결정**: 트랜잭션을 적용하지 않음 (`Propagation.NOT_SUPPORTED`로 명시).

## 6. 후속 문제 — 트랜잭션 제거 후 테스트 실패

트랜잭션을 제거하자 `MemberServiceTest#sendVerificationCodeWithDuplicatedEmail` 테스트가 실패했다.
"이미 가입된 이메일로 인증 코드 전송 시 `EmailDuplicatedException`이 발생한다"를 검증하는 테스트인데,
예외가 발생하지 않았다.

### 원인 분석

- 테스트는 격리를 위해 **트랜잭션으로 감싸고 종료 시 롤백**하도록 되어 있었다.
- H2의 기본 트랜잭션 격리 수준은 **READ COMMITTED(커밋된 읽기)** 이다.
- 흐름:
  1. 테스트 트랜잭션 시작
  2. 회원 저장 (아직 **커밋 전**)
  3. `memberService.sendVerificationCode()` 호출
  4. `MemberService`는 트랜잭션이 없으므로(`NOT_SUPPORTED`) **별도 컨텍스트에서 `existsByEmail` 조회 실행**
  5. 테스트 트랜잭션이 커밋되지 않았으므로, READ COMMITTED 기준으로 아직 **저장된 회원을 읽지 못함** → 중복으로 판단되지 않아 예외 미발생

> 트랜잭션 제거 이전에는 트랜잭션 전파로 테스트와 서비스가 **하나의 트랜잭션**을 공유했기 때문에,
> 커밋 전 데이터도 같은 트랜잭션 안에서 조회되어 정상 동작했다.
> 트랜잭션을 끊으면서 서비스 조회가 테스트 트랜잭션 바깥으로 분리된 것이 원인이다.

### 해결 방안 검토

| 방안 | 내용 | 채택 여부 |
|------|------|-----------|
| 1 | `MemberRepository`를 `@MockBean` 처리 | 보류 — 서비스 테스트를 통합테스트 겸 단위테스트로 운영 중이라 실제 DB 동작을 검증하고 싶어 후보로만 둠 |
| 2 | 해당 테스트에만 트랜잭션을 걸지 않음 (`@Transactional(propagation = NOT_SUPPORTED)`) | ✅ 채택 |

2번은 저장한 회원이 실제 커밋되어 다른 테스트에 영향을 줄 수 있다는 문제가 있는데,
`@AfterEach`에서 `memberRepository.deleteAllInBatch()`로 정리해 해결했다.
테스트의 일관성(실제 DB 조회 검증 유지)을 위해 2번을 선택했다.

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("이미 가입된 이메일로 인증 코드 발송을 요청하면 예외가 발생한다.")
@Test
void sendVerificationCodeWithDuplicatedEmail() {
    String email = "user@example.com";
    memberRepository.save(createMember(email, "닉네임")); // 실제 커밋됨

    assertThatThrownBy(() -> memberService.sendVerificationCode(email))
            .isInstanceOf(EmailDuplicatedException.class)
            .hasMessage("이미 사용 중인 이메일입니다.");
}

@AfterEach
void tearDown() {
    memberRepository.deleteAllInBatch(); // 커밋된 데이터 정리
}
```

### 정리

- **문제**: 서비스의 트랜잭션 제거 후, 테스트 트랜잭션(미커밋)과 서비스 조회가 분리되어 READ COMMITTED 하에서 저장 회원을 읽지 못함 → 중복 예외 미발생.
- **원인**: 이전에는 트랜잭션 전파로 하나의 트랜잭션을 공유했으나, 분리되면서 커밋 전 데이터를 조회 불가.
- **해결**: 해당 테스트만 트랜잭션 비활성화하여 실제 커밋되게 하고, `@AfterEach`로 데이터 정리. (MockBean 처리는 통합+단위 테스트 성격 유지를 위해 보류)
