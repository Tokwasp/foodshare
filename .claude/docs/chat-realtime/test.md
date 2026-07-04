# chat-realtime — 테스트 보고 (구현 전 / red)

> `/implement chat-realtime` 1단계 산출물. **구현이 없어 현재 컴파일/실행은 red가 정상**이다.
> 검토 후 "구현 들어가"를 받으면 2단계로 진행한다.

## 작성한 테스트

### 1) `ChatMessageServiceTest` (service, `extends IntegrationTestSupport`)
실시간 발행의 **순수 로직**(정본 DB 저장 + 안읽음 비정규화 + 방참여 검증)을 검증한다.
WebSocket 없이 H2 통합 테스트로 빠르게 돈다. (docs.md "DB저장/방참여 검증은 service 단위로 분리" 지침)

| 테스트 | 검증 케이스 |
|---|---|
| `send` | `ChatMessage`가 DB에 정본 저장되고, 발신자 닉네임을 채운 `payload` 반환(type=`CHAT_MESSAGE`, messageId/roomId/senderId/content/createdAt) |
| `sendIncrementsRecipientUnreadCountOnly` | 수신자(발신자 제외) `ChattingMember.unreadCount`만 +1, 발신자는 그대로 |
| `sendReturnsRecipientIdsExcludingSender` | 반환값이 발신자를 제외한 수신자 memberId 목록을 포함 (단위 4 Redis 팬아웃 전달 경로용) |
| `sendByNonMember` | 방 미참여자가 보내면 `ForbiddenChatAccessException`, 메시지 미저장 |
| `sendToNonexistentRoom` | 없는 방이면 `ChatRoomNotFoundException` |

### 2) `ChatStompIntegrationTest` (`@SpringBootTest(RANDOM_PORT)`)
STOMP **전 구간 배선**을 실제 브로커로 검증한다(3계층 스킬 밖, docs.md ⚠️ 항목).

| 테스트 | 검증 케이스 |
|---|---|
| `publishDeliversToRecipientAndPersists` | 연결 → `/user/queue/messages` 구독 → `/pub/chat/rooms/{roomId}` 발행 시, **수신자 큐로 payload 전달** + `ChatMessage` DB 저장 + 수신자 `unreadCount` 증가를 한 번에 검증 |
| `handshakeWithoutMemberIdIsRejected` | 미인증(memberId 없음) 핸드셰이크가 **401로 거절**되어 연결이 실패 |

- 인증은 세션 쿠키(Spring Session/Redis) 대신 **`?memberId=` 쿼리 파라미터 → 테스트용 `@Primary HandshakeInterceptor`**가 `attributes`에 `LOGIN_MEMBER_ID`를 심는다(테스트 프로파일엔 실제 Redis 세션이 없음). 운영의 실제 `MemberHandshakeHandler`가 그 attributes를 읽어 Principal을 만들므로 핸들러·브로커·매핑·서비스 경로는 실배선을 그대로 탄다. memberId 없으면 401 거절 → 거절 경로도 동일 인터셉터로 검증.
- 메시지 컨버터는 Jackson 버전(Boot4→Jackson3) 의존을 피하려고 클라이언트는 `SimpleMessageConverter`(byte[]) + `Content-Type: application/json`을 직접 지정하고, 수신 payload는 JSON 문자열 `contains`로 검증한다. 서버측 직렬화/역직렬화는 부트 기본 컨버터를 사용한다.

## 2단계(구현)에서 만들/바꿀 것 — 테스트가 가정하는 운영 API

> 테스트를 컴파일/통과시키려면 아래가 필요하다. (이번 단계에선 **작성하지 않음**)

- **build.gradle**: `spring-boot-starter-websocket` 추가 (현재 없음 → 두 테스트 컴파일 불가가 곧 red).
- `ChatMessageService.send(Long senderId, Long roomId, String content)` → `SentMessage(ChatMessagePayload payload, List<Long> recipientIds)` 반환. 방 조회→참여 검증→`ChatMessage` 저장→수신자 `unreadCount += 1`.
- `ChatMessagePayload` (response DTO): `type`(고정 `CHAT_MESSAGE`)/`messageId`/`roomId`/`senderId`/`senderNickName`/`content`/`createdAt`.
- `ChattingMember`에 **증가 메서드 추가**(`increaseUnreadCount()` 등) — 현재 `resetUnreadCount()`만 존재.
- `WebSocketConfig`(`@EnableWebSocketMessageBroker`): 엔드포인트 `/ws`, app prefix `/pub`, simple broker `/queue`, userDestinationPrefix `/user`, heartbeat 10s/10s(TaskScheduler). 등록 시 `HttpSessionHandshakeInterceptor` + **인증용 `HandshakeInterceptor`(빈 주입, 테스트 `@Primary` 대체용 seam)** + `MemberHandshakeHandler`.
- 인증 `HandshakeInterceptor`(운영): `beforeHandshake`에서 세션 `LOGIN_MEMBER_ID` 없으면 **401 거절(false)**, 있으면 attributes에 복사. (결정: 미인증 거절을 핸드셰이크 단계에서 처리)
- `MemberHandshakeHandler`(운영): attributes `LOGIN_MEMBER_ID` → Principal=memberId.
- `@MessageMapping("/chat/rooms/{roomId}")` 핸들러: Principal→senderId, 서비스 호출 후 수신자별 `convertAndSendToUser(recipientId, "/queue/messages", payload)`.
- SEND 요청 DTO `ChatSendRequest { String content }`.

## 결정 사항 (검토 완료)
1. **발신자 ACK**: 수신자에게만 전달(낙관적 UI). 별도 ACK 프레임 없음 → STOMP RECEIPT로 갈음. ✅
2. **핸드셰이크 미인증**: 핸드셰이크 단계에서 **401 거절**. ✅ (테스트 `handshakeWithoutMemberIdIsRejected`로 검증)

## 남은 검토 포인트
1. **반환 형태** `SentMessage(payload, recipientIds)` record + 전달(convertAndSendToUser)은 컨트롤러가 수행(단위4에서 "Redis 채널 publish→리스너 전달"로 교체할 부분을 컨트롤러/브로드캐스터로 격리). 이대로 OK?
2. **STOMP 메시지 컨버터** 클래스명(Jackson3) — 서버 자동설정 의존. 배선 시 실제 빈 확인(테스트는 컨버터명 비의존).
