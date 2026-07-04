# 실시간 메시지 송수신 — WebSocket/STOMP 단일 인스턴스 (chat-realtime)

채팅 실시간 발행/수신을 **한 서버 안에서** 동작하게 구현한다 — WebSocket(STOMP) 연결·구독·발행 + 메시지 정본 DB 저장 + 수신자 안읽음 수 증가 + 로컬 브로드캐스트(`convertAndSendToUser`)까지. (선행 단위 `chat-room`/`chat-history`의 엔티티·repository 위에 올라간다.)

> **분산(다중 인스턴스) 전달은 단위 4(`chat-distributed`, Redis Pub/Sub 팬아웃)로 분리.** 이 단위의 전달부(`convertAndSendToUser` 루프)만 단위 4에서 "Redis 채널 publish → 각 인스턴스 리스너 전달"로 교체하면 되고, 검증·DB저장·ACK·유저 큐 구독 모델은 여기서 확정한다.

## 사용자 흐름
```
로그인 직후 /ws 전역 1회 연결  →  /user/queue/messages 구독(필수)  →  SEND /pub/chat/rooms/{roomId}  →  상대가 유저 큐로 실시간 수신
```
- 연결은 방 진입 시점이 아니라 **로그인된 상태로 앱 로드 시 전역 1개**. 방 진입은 새 연결이 아니라 REST 이력 로드 + 유저 큐 필터(소켓 동작 없음).
- 수신은 룸 토픽이 아니라 **유저 큐 단일 채널**. 클라는 payload의 `roomId`로 분기(열린 방→append / 그 외→배지·토스트).

## 서버 흐름

### 1) 연결(Handshake) — `GET /ws`
```
HttpSession 속성 복사  →  인증 인터셉터(LOGIN_MEMBER_ID 없으면 401 거절)  →  Principal=memberId 설정
```
- `HttpSessionHandshakeInterceptor`가 HTTP 세션(Spring Session) 속성을 핸드셰이크 attributes로 복사.
- `SessionHandshakeAuthInterceptor`: attributes에 `LOGIN_MEMBER_ID` 없으면 **401로 거절**(`beforeHandshake` false).
- `MemberHandshakeHandler.determineUser`: attributes의 `LOGIN_MEMBER_ID`로 **Principal(name=String.valueOf(memberId))** 설정 → `/user/...` destination이 그 세션으로 매핑된다.

### 2) 발행(SEND) — `/pub/chat/rooms/{roomId}`
```
Principal→senderId  →  방 존재·참여 검증  →  ChatMessage DB 저장(정본)  →  수신자 unreadCount +1  →  수신자별 convertAndSendToUser
```
1. **발신자 식별** — `Principal.getName()` → `senderId`.
2. **검증** — 방 존재(`CHAT_ROOM_NOT_FOUND`) + 내가 그 방의 `ChattingMember`인지(`FORBIDDEN_CHAT_ACCESS`). 미참여면 저장 전에 거절.
3. **정본 저장** — `ChatMessage` 저장 → `messageId` 채번(전달보다 먼저: 오프라인 유실 방지·멱등성·즉시 ACK).
4. **안읽음 수 증가** — 수신자(발신자 제외) `ChattingMember.unreadCount += 1` (비정규화 카운터, 목록 조회는 이 값을 읽기만 함).
5. **전달** — `SentMessage(payload, recipientIds)` 반환 → 컨트롤러가 `recipientIds`마다 `convertAndSendToUser(memberId, "/queue/messages", payload)`. 로컬 SimpleBroker가 수신자 세션 구독을 찾아 push.

### STOMP 설정 (`WebSocketConfig`)
- 엔드포인트 `/ws`, app prefix `/pub`, in-memory SimpleBroker `/queue`, userDestinationPrefix `/user`. (룸 토픽 `/sub` 미사용)
- heartbeat 10s/10s (SimpleBroker용 `TaskScheduler` 빈 등록). ALB idle timeout(60s)보다 작게.

---

## 예외 처리
- **존재하지 않는 채팅방** — `ChatRoomNotFoundException` → **404**
- **방 참여자 본인 아님** — `ForbiddenChatAccessException` → **403** (메시지 미저장)
- **미인증 핸드셰이크** — 핸드셰이크 단계에서 **401 거절**(연결 실패)

> STOMP 처리 중 에러는 ProblemDetail(HTTP 전용)이 아니라 연결 거절/STOMP ERROR 프레임으로 처리. 미참여 발행은 서비스 예외로 저장이 롤백된다.

## 엔드포인트 (WebSocket / STOMP)

| 구분 | 경로 | 설명 |
|------|------|------|
| 연결 | `GET /ws` | 로그인 직후 전역 1회. 세션 쿠키 기반 인증(미인증 401). |
| 구독 | `/user/queue/messages` | 연결 직후 필수(유일). 방 밖에서도 수신. |
| 발행 | `/pub/chat/rooms/{roomId}` | 본문 `{ "content": "..." }` |

**수신 Payload** (`/user/queue/messages`)
```json
{
  "type": "CHAT_MESSAGE",
  "messageId": 9002,
  "roomId": 700,
  "senderId": 1,
  "senderNickName": "내닉네임",
  "content": "네, 가능합니다!",
  "createdAt": "2025-05-28T10:06:00"
}
```

## 테스트
- ✅ **서비스 통합 테스트** (`ChatMessageServiceTest`, `extends IntegrationTestSupport`) — 정본 저장 + 발신자 닉네임 채운 payload 반환, 수신자만 `unreadCount` +1(발신자 0), `recipientIds`(발신자 제외) 반환, 방 미참여 `ForbiddenChatAccessException`(미저장), 없는 방 `ChatRoomNotFoundException`. (5건)
- ✅ **STOMP 통합 테스트** (`ChatStompIntegrationTest`, `@SpringBootTest(RANDOM_PORT)`) — 연결 → `/user/queue/messages` 구독 → `/pub/chat/rooms/{roomId}` 발행 시 **수신자 큐로 payload 전달 + ChatMessage DB 저장 + 수신자 unreadCount 증가**를 한 번에 검증, 미인증(memberId 없음) 핸드셰이크 401 거절. (2건)

> 인증은 테스트 프로파일에 실제 Redis 세션이 없으므로 `?memberId=` 쿼리 파라미터 → 테스트용 `@Primary HandshakeInterceptor`가 attributes에 `LOGIN_MEMBER_ID`를 심는다. 운영 `MemberHandshakeHandler`가 그 attributes를 읽으므로 브로커·핸들러·매핑·서비스 경로는 실배선을 그대로 검증한다. STOMP 임베디드 SimpleBroker는 외부 인프라·도커 불필요.
>
> 전체 테스트 BUILD SUCCESSFUL(회귀 없음).

## 참고 — 비고
- **새 의존성은 `spring-boot-starter-websocket`만.** Redis Pub/Sub(단위 4)는 기존 `spring-boot-starter-data-redis`로 충분(AMQP 불필요).
- **전달부는 컨트롤러에 직접** 두었다(`convertAndSendToUser` 루프). 단위 4에서 이 루프를 `ChatBroadcaster`(Local→Redis) seam으로 빼면 팬아웃으로 교체된다 — 검증·저장·ACK 로직은 그대로 재사용.
- `SessionHandshakeAuthInterceptor`는 빈으로 등록되어 테스트가 `@Primary`로 대체할 수 있는 seam이다. `Principal.getName()`(=`String.valueOf(memberId)`)과 `convertAndSendToUser`의 첫 인자 포맷이 반드시 일치해야 유저 destination이 매핑된다.
- `AuthenticationFilter`는 `/api/v1/*`만 처리하므로 `/ws` 핸드셰이크와 무관 — WS 인증은 핸드셰이크 인터셉터가 담당.
