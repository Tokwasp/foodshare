# chat-realtime — WebSocket/STOMP 단일 인스턴스 (구현 단위 3/4)

> **scoped 문서.** 전체 채팅 명세의 일부만 구현하는 단위다.
> **canonical 명세: [`../chat/docs.md`](../chat/docs.md)** — 4장 "실시간 메시지 송수신"의 맥락을 따른다.
> **선행 단위: `chat-room`, `chat-history`**(엔티티·이력 조회가 있어야 함).

## 이 단위의 목표
**한 서버 안에서** 실시간 채팅이 완전히 동작하게 만든다. WebSocket(STOMP) 연결·구독·발행 +
DB 저장 + 로컬 브로드캐스트까지. **Redis Pub/Sub는 쓰지 않는다**(단위 4에서 Redis 단일 채널 팬아웃으로 분산).
즉 여기까지면 단일 인스턴스 배포로 채팅이 동작한다.

## 구현 범위 (canonical 참조: `../chat/docs.md` 4장)
- **STOMP 설정** (`WebSocketMessageBrokerConfigurer`)
  - 엔드포인트 `/ws` (핸드셰이크), app prefix `/pub`, **in-memory simple broker** `/queue` + userDestinationPrefix `/user`. (룸 브로드캐스트 `/sub` 미사용)
  - heartbeat 10s/10s (SimpleBroker용 `TaskScheduler` 필요).
- **연결 라이프사이클**: 채팅방 진입 시가 아니라 **로그인된 상태로 앱 로드 시 `/ws` 전역 1개 연결**(끊기면 재연결). 방 진입은 새 연결이 아니라 REST 이력 로드 + 유저 큐 필터(소켓 동작 없음).
- **핸드셰이크 인증**: `/ws` 연결 시 세션 쿠키 기반 인증(미인증 401/거절). `SessionConst.LOGIN_MEMBER_ID`로 `senderId` 확보 + **Principal=memberId 설정**(`convertAndSendToUser` 매핑용).
- **구독(필수, 유일)** `/user/queue/messages`: **연결 직후 구독.** 내게 오는 모든 메시지/알림 수신(방 밖에서도). 이게 있어야 실시간 push 성립. (룸 토픽 구독은 두지 않음)
- **발행(SEND)** `/pub/chat/rooms/{roomId}`, 서버 처리 흐름:
  1. 세션 `senderId` → 방 참여 검증(아니면 거절)
  2. `ChatMessage` **DB 저장(정본)** → `messageId` 채번. 동시에 **수신자(발신자 제외)의 `ChattingMember.unreadCount += 1`** (안읽음 수 비정규화 — 목록 조회는 이 값을 읽기만 함, canonical 6-2/6-4)
  3. `SimpMessagingTemplate.convertAndSendToUser(수신자 memberId, "/queue/messages", payload)` (단일 인스턴스: 로컬 SimpleBroker가 수신자 세션 구독을 찾아 push)
  4. 발신자 ACK

**SEND Request 본문** (`/pub/chat/rooms/{roomId}`)
```json
{ "content": "네, 가능합니다!" }
```

**수신 Payload** (`/user/queue/messages` 구독자 수신)
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
- 클라는 payload의 `roomId`로 분기: **열린 방이면 메시지 append, 아니면 배지/토스트.**
- **채팅 가능 시점**: 기존 방 메시지 전송은 물품 상태 무관 허용(canonical 1장/4장).

## 범위 밖 (단위 4 `chat-distributed`)
- Redis Pub/Sub 단일 채널(`chat.messages`) 팬아웃 전파, `RedisMessageListenerContainer` 구독
- 다중 인스턴스(ECS 2) 전달
- 오프라인 푸시(2차)
> 단위 4에서는 3단계의 "로컬 convertAndSendToUser"를 "Redis 채널 publish → 각 인스턴스 리스너가 convertAndSendToUser"로 **교체**한다. 그래서 발행 흐름의 1·2·4단계(검증·DB저장·ACK)와 유저 큐 구독 모델은 단위 3에서 확정하고, 3단계(전달 경로의 분산화)만 단위 4에서 추가한다. (presence·워커·DLX는 팬아웃 모델에서 불필요 — canonical 참고)

## ⚠️ 테스트 방식 (기존 3계층 스킬 밖)
`.claude/skills/test/`는 controller/service/repository만 다룬다. WebSocket은 별도 접근 필요:
- **STOMP 통합테스트 (외부 인프라 불필요)**: Spring SimpleBroker는 **임베디드(인프로세스)**라 외부 의존성·도커 없이 정상 suite에서 검증한다. `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebSocketStompClient`로 **연결 → `/user/queue/messages` 구독 → SEND `/pub/...` → 수신 payload + DB 저장** 검증.
  - 핸드셰이크 Principal: 테스트에선 세션/Principal을 주입해야 한다(테스트용 핸드셰이크 핸들러로 memberId Principal 고정, 또는 세션 시드). `convertAndSendToUser`가 그 Principal로 매핑되는지까지 확인.
- DB 저장/방참여 검증 같은 순수 로직은 service 단위 테스트로 분리(기존 스킬 적용).
- 필요 시 `.claude/skills/test/websocket/` 가이드로 승격 검토.

## 구현 메모
- 의존성 추가 필요: `spring-boot-starter-websocket` (현재 build.gradle에 없음).
- `senderNickName`은 저장 말고 조회 매핑.

### 핸드셰이크에서 Principal=memberId 설정 (필수)
유저 destination(`/user/queue/messages`)이 동작하려면 WS 세션에 `Principal`(이름=memberId)이 붙어야 한다. Spring이 `Principal(memberId) ↔ simpSessionId` 매핑을 보관 → `convertAndSendToUser(memberId, ...)`가 그 세션 큐(`/queue/messages-user{sessionId}`)로 변환·전달한다. 세션 쿠키(Spring Session) 기반이므로 `HandshakeHandler.determineUser()`로 설정한다.

```java
public class MemberHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Long memberId = (Long) attributes.get(SessionConst.LOGIN_MEMBER_ID);
        if (memberId == null) return null;          // 미인증 → 연결 거절(401)
        String name = String.valueOf(memberId);
        return () -> name;                           // Principal.getName() == memberId
    }
}
```
```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setHandshakeHandler(new MemberHandshakeHandler())
            .addInterceptors(new HttpSessionHandshakeInterceptor()); // HttpSession 속성 → 핸드셰이크 attributes
}
```
- `HttpSessionHandshakeInterceptor`가 HTTP 세션(Spring Session/Redis) 속성을 핸드셰이크 `attributes`로 복사 → `determineUser`에서 `LOGIN_MEMBER_ID` 조회 가능.
- `Principal.getName()`(=memberId 문자열)이 `convertAndSendToUser(여기, ...)`의 첫 인자와 **반드시 일치**해야 한다(`String.valueOf(memberId)`로 통일).
- 토큰을 STOMP 헤더로 받는 구조라면 `ChannelInterceptor`로 CONNECT에서 `accessor.setUser(...)` 하는 대안도 있으나, 본 프로젝트는 세션 쿠키 방식이라 HandshakeHandler가 적합.
