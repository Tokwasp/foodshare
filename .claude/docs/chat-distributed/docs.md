# chat-distributed — Redis Pub/Sub 팬아웃 (구현 단위 4/4)

> **scoped 문서.** 전체 채팅 명세의 마지막 단위(분산 실시간 전달)다.
> **canonical 명세: [`../chat/docs.md`](../chat/docs.md)** — 4장 전체 + 설계 결정 로그를 따른다.
> **선행 단위: `chat-realtime`**(단일 인스턴스 실시간이 동작해야 함).
> 인프라(멀티 인스턴스) 의존이 있어 **후순위로 미뤄도 됨** — 단위 3까지면 단일 서버로 채팅이 동작한다.

## 이 단위의 목표
다중 인스턴스(ECS 2)에서 상대방이 어느 서버에 붙어 있든 실시간 전달되게 한다.
단위 3의 "로컬 convertAndSend"를 **Redis Pub/Sub 단일 채널 팬아웃**으로 교체한다.
**presence·워커·RabbitMQ·DLX는 두지 않는다**(팬아웃 + 로컬 세션 필터로 충분).

## 핵심 아이디어
- 모든 인스턴스가 단일 채널 `chat.messages`를 구독.
- 발행 시: DB 저장 → `(payload, recipientIds)`를 채널에 publish → **모든 인스턴스 수신** → 각자 `recipientIds`에 `convertAndSendToUser`.
- 수신자 WS 세션을 **로컬에 가진 인스턴스만** 실제 push, 나머지는 Spring user 레지스트리가 못 찾아 **자동 no-op**.
- `memberId → serverId` presence가 불필요한 이유: 각 인스턴스의 로컬 `SimpUserRegistry`가 "이 인스턴스에 그 회원 세션이 있는가"를 자동 판정한다.

## 구현 범위 (canonical 참조: `../chat/docs.md` 4장)
1. **Redis 발행**: `RedisTemplate.convertAndSend("chat.messages", message)`. message = `(payload, recipientIds)` 직렬화. `recipientIds`는 발신자 제외.
2. **Redis 구독**: `RedisMessageListenerContainer`에 `chat.messages` 채널 리스너 등록. 수신 시 역직렬화 → `recipientIds`에 대해 `SimpMessagingTemplate.convertAndSendToUser(memberId, "/queue/messages", payload)`.
3. **발행 흐름 분산화**: 단위 3의 3단계(로컬 convertAndSendToUser)를 "Redis publish"로 교체. 1·2·4단계(검증·DB저장+`unreadCount`·ACK)와 유저 큐 구독 모델은 단위 3 그대로.
   - **권장 seam**: 발행 핸들러가 `ChatBroadcaster` 인터페이스에 위임.
     - 단위 3 = `LocalChatBroadcaster` (직접 `convertAndSendToUser`, Redis 불필요 → 기존 단위 3 통합테스트 유지).
     - 단위 4 = `RedisChatBroadcaster` (채널 publish) + 리스너가 수신 측에서 `convertAndSendToUser`.
     - 전환은 빈 교체(프로파일/조건부 빈)로 처리. 발행 흐름 코드는 불변.
4. **장애 처리(단순)**: best-effort. 수신자 오프라인이면 모든 인스턴스에서 no-op. 인스턴스 크래시·Redis 단절도 **메시지는 DB 정본**이라 유실 아님. DLX/재평가 경로 없음.
5. **다중 기기**: 같은 memberId Principal로 여러 세션이 등록되면 `convertAndSendToUser`가 모든 세션에 전달(presence 단일값 제약 없음).

## 범위 밖 (2차/향후)
- 오프라인 회원 **푸시 알림(FCM/APNs)** — 수신자가 어느 인스턴스에도 세션이 없을 때. 본 단위에선 자리만(no-op/로그).
- 실시간 '읽음' 표시.
- 대규모 트래픽용 RabbitMQ directed routing(워커+presence+DLX) — canonical "향후 확장: RabbitMQ directed routing" 섹션 참고.

## ⚠️ 테스트 방식

| 무엇 | 외부 인프라 | 방법 | 위치 |
|------|-----------|------|------|
| ① 브로드캐스터 분기/직렬화 **로직** | 불필요 | `RedisTemplate` **mock** → publish 호출·`recipientIds` 구성(발신자 제외) 검증 | 정상 suite (단위 테스트) |
| ② Redis Pub/Sub **전파 동작** | Redis | "한 쪽 publish → 다른 리스너 수신 → `convertAndSendToUser` 호출" 검증 | 정상 suite (통합) 또는 `external/` |
| ③ 실 AWS ElastiCache 연결 | 실 AWS | 연결 확인 스모크 | `external/` `@Disabled` (수동) |

- **②는 mock이 아니다.** 실제 Redis Pub/Sub로 publish→subscribe 전파를 검증한다. 기존 `external/RedisIntegrationTest` 패턴 또는 Testcontainers Redis 사용.
- ③은 기존 `external/RedisIntegrationTest`·`MailServiceIntegrationTest`의 `@SpringBootTest`+`@Disabled` 패턴을 따른다(연결 확인용, CI 미포함).
- 단위 3의 `ChatStompIntegrationTest`는 `LocalChatBroadcaster` 경로를 검증하므로 본 단위 변경으로 깨지지 않는다.

## 구현 메모
- **의존성 추가 없음.** Redis Pub/Sub는 기존 `spring-boot-starter-data-redis`로 충분(`RedisMessageListenerContainer`, `RedisTemplate`). `spring-boot-starter-amqp` 불필요.
- 운영: STOMP heartbeat 10s < ALB idle 60s. 팬아웃 모델이라 **ALB sticky session 불필요**(어느 인스턴스에 붙어도 동작).
- payload는 단위 3과 동일(`type`,`messageId`,`roomId`,`senderId`,`senderNickName`,`content`,`createdAt`).
