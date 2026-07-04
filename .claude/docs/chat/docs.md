# 채팅(CHAT) 도메인 명세 (draft)

> 상위 공통 규약(Base URL `/api/v1`, 세션 인증, 성공=ApiResponse envelope, 실패=RFC7807 ProblemDetail)은
> 루트 `CLAUDE.md`를 따른다. 본 문서는 채팅 도메인 전용 명세이며 **논의 중인 초안(draft)**이다.

## 개요

- 음식 물품 상세에서 "채팅하기"를 누르면 **등록자(owner) ↔ 요청자(requester) 1:1 채팅방**이 생성된다.
- 방은 `(foodId, requesterId)` 단위로 유일하다. 이미 존재하면 기존 방을 반환한다.
- 방 관리·이력 조회는 **REST**, 실시간 송수신은 **WebSocket(STOMP) + Redis Pub/Sub 단일 채널 팬아웃**으로 처리한다.
- 메시지는 현재 **텍스트만** 지원한다.

## 설계 결정 (확정)

| 항목 | 결정 | 이유 |
|------|------|------|
| 엔티티 구조 | 3개 (`ChatRoom` + `ChattingMember`(조인) + `ChatMessage`) | 향후 **그룹 채팅(한 방 N명)** 확장 대비. 참여자·읽음 위치를 회원당 행으로 일반화 |
| 방 식별(1:1) | `foodId` + 참여 `ChattingMember` | 1:1 방은 (음식, 신청자) 멤버십으로 유일. DB 유니크 대신 멤버십 조회로 get-or-create |
| 전송 방식 | REST(방/이력) + WebSocket(실시간) | 다중 인스턴스 전달은 Redis Pub/Sub 단일 채널 팬아웃(presence·워커 불필요). 대규모 확장 시 RabbitMQ directed routing은 "향후 확장" 섹션 참고 |

> 1:1 MVP만 보면 `ChatRoom`+`ChatMessage` 2개로도 충분하나, **향후 그룹 채팅(한 방에 N명)** 확장을 위해
> 참여자·읽음 위치를 회원당 행으로 분리하는 `ChattingMember`(조인)를 추가해 **3개 구조**로 둔다.
> (현재 `POST /chat/rooms`로 생성되는 방은 등록자+신청자 2인이지만, 모델은 N인 대응.)

## 엔티티

기존 컨벤션 준수: `BaseEntity` 상속(`createdAt`/`modifiedAt`/`deleted` 자동), `@GeneratedValue(IDENTITY)`,
**FK는 ID만 저장(@ManyToOne 미사용)**, Builder + protected 생성자. 닉네임 등 변동값은 저장하지 않고 조회 시 매핑.

| 엔티티 | 주요 컬럼 | 비고 |
|--------|-----------|------|
| `ChatRoom` | `roomId`, `foodId`, `ownerId` | 방의 호스트(=물품 등록자)만 둔다. 참여자/읽음은 `ChattingMember`로 분리(N인 대응) |
| `ChattingMember` | `chattingMemberId`, `roomId`, `memberId`, `role`(OWNER/MEMBER), `lastReadMessageId`, `unreadCount` | **`unique(roomId, memberId)`**. 회원당 1행으로 참여·읽음 위치(`lastReadMessageId`)·안읽음 수(`unreadCount` 비정규화 캐시) 관리. 닉네임은 조회 시 매핑 |
| `ChatMessage` | `messageId`, `roomId`, `senderId`, `content`(TEXT) | 닉네임은 조회 시 매핑 |

> **읽음 위치**는 `ChattingMember.lastReadMessageId`(회원당 1행)로 관리한다. 참여자 2인 고정이 아니라
> N인까지 일반화된다. `unreadCount`는 `ChattingMember.unreadCount`(수신 시 +1, 읽음 시 0) 비정규화 카운터로 관리한다.

> **나가기/차단/메시지 삭제는 미지원(MVP).** 방은 생성 후 영구 유지하며 목록 정렬·`unreadCount`로 관리한다. (`BaseEntity.deleted`는 상속하나 채팅 전용 삭제 API는 두지 않음 — 필요 시 2차)

---

## 1. 채팅방 생성/조회
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/chat/rooms` |
| 인증 | 필요 |
| 설명 | 물품에 대한 1:1 채팅방을 생성한다. `(foodId, 로그인 회원)` 방이 이미 있으면 기존 방을 반환한다. |

**Request Body**
```json
{ "foodId": 100 }
```

**제약**
- 본인 물품에는 채팅 불가
- 존재하지 않는 물품 불가
- **새 방 생성은 물품이 `IN_PROGRESS`일 때만** 허용. `COMPLETED/EXPIRED/INCOMPLETE`면 거절. (이미 존재하는 방을 반환하는 경우는 상태 무관 — 기존 방은 4장 참고로 계속 대화 가능)
- **기존 1:1 방 탐색** = `foodId` 방 중 로그인 회원이 `ChattingMember`로 참여한 방. 있으면 반환, 없으면 생성.
- 신규 생성 시 `ChatRoom(foodId, ownerId=food.memberId)` + `ChattingMember` 2행(등록자=OWNER, 로그인 회원=MEMBER, `lastReadMessageId`=0, `unreadCount`=0)을 함께 만든다.

**Response (신규 생성 201 / 기존 방 반환 200)**
```json
{ "code": "SUCCESS", "data": { "roomId": 700, "foodId": 100, "created": true }, "message": "채팅방이 생성되었습니다." }
```

> `created`: 이번 요청으로 새로 만들어졌으면 `true`(201), 기존 방을 반환했으면 `false`(200).

**Error**
| code | 상태 | 설명 |
|------|------|------|
| `FOOD_NOT_FOUND` | 404 | 존재하지 않는 물품 |
| `SELF_CHAT_NOT_ALLOWED` | 400 | 본인 물품에는 채팅 불가 |
| `FOOD_NOT_AVAILABLE` | 409 | 새 방 생성 불가 상태(완료/만료/삭제). 요청 도메인과 동일 코드 재사용 |

---

## 2. 내 채팅방 목록
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/members/me/chat/rooms` |
| 인증 | 필요 |
| 설명 | 로그인 회원이 참여(등록자 또는 요청자)한 채팅방 목록을 조회한다. 최근 메시지 시각 내림차순. |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": [
    {
      "roomId": 700,
      "foodId": 100,
      "foodName": "미개봉 시리얼",
      "partnerNickName": "상대닉네임",
      "lastMessage": "안녕하세요, 나눔 가능할까요?",
      "lastMessageAt": "2025-05-28T10:05:00",
      "unreadCount": 3
    }
  ],
  "message": "조회에 성공했습니다."
}
```

> `unreadCount`: 로그인 회원이 안 읽은 메시지 수 = 내 `ChattingMember.unreadCount`(비정규화 카운터, 수신 시 +1·읽음 시 0). 목록 조회는 저장값을 읽기만 한다(매번 세지 않음).

---

## 3. 채팅 이력 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/chat/rooms/{roomId}/messages?direction={direction}&cursor={cursor}&size={size}` |
| 인증 | 필요 (방 참여자 본인) |
| 설명 | 채팅방 메시지를 **양방향 커서**로 무한 스크롤 조회한다. 방 진입(`initial`)은 마지막 읽은 메시지 기준 위·아래를 함께 보여주고, 이후 위(과거)·아래(최신)로 더 불러온다. |

**Query Params**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| direction | string | X | `initial`(기본) / `before`(위로, 과거) / `after`(아래로, 최신) |
| cursor | long | △ | `before`/`after`일 때 필수. `before`면 `messageId < cursor`, `after`면 `messageId > cursor`. `initial`이면 무시(서버가 `lastReadMessageId` 기준 사용). |
| size | int | X | 페이지 크기. 기본 20 (권장 20~30) |

> **동작**
> - 방 진입(`initial`): 내 `lastReadMessageId` 기준 **위쪽(`<= lastRead`) 최신 size개 + 아래쪽(`> lastRead`) 오래된 size개**를 합쳐(최대 `2*size`) 반환. 신규(lastRead=0)면 위쪽이 비어 처음부터, 다 읽었으면 아래쪽이 비어 맨 밑 → 별도 분기 불필요.
> - 위로 스크롤(`before`): 직전 응답의 `upCursor`를 `cursor`로 전달.
> - 아래로 스크롤(`after`): 직전 응답의 `downCursor`를 `cursor`로 전달.
> - 새로 도착하는 메시지는 이력 API가 아니라 **실시간(WebSocket)** 으로 수신한다.
> - **읽음 갱신(부수효과)**: `initial` 조회 시에만 내 `ChattingMember.lastReadMessageId`를 방의 **최신 messageId**로 갱신하고 `unreadCount`를 `0`으로 리셋한다(입장=전부 읽음). `before`/`after` 스크롤은 읽음 위치·안읽음 수를 바꾸지 않는다.

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": {
    "messages": [
      {
        "messageId": 9001,
        "senderId": 2,
        "senderNickName": "상대닉네임",
        "content": "안녕하세요, 나눔 가능할까요?",
        "mine": false,
        "createdAt": "2025-05-28T10:05:00"
      }
    ],
    "anchorMessageId": 8990,
    "upCursor": 9001,
    "downCursor": 9020,
    "hasPrev": true,
    "hasNext": true
  },
  "message": "조회에 성공했습니다."
}
```

> - `messages`: direction과 무관하게 `messageId` 내림차순(최신 → 과거).
> - `anchorMessageId`: `initial`에서만 채워지는 진입 기준값(직전 `lastReadMessageId`). FE가 "새 메시지" 구분선·스크롤 위치로 사용. `before`/`after`는 `null`.
> - `upCursor`/`downCursor`: 묶음 중 가장 오래된/최신 messageId. 각각 위(`before`)/아래(`after`) 추가 조회 커서. 묶음이 비면 `null`.
> - `hasPrev`/`hasNext`: `upCursor`보다 과거 / `downCursor`보다 최신 메시지가 더 있으면 `true`.

**Error**
| code | 상태 | 설명 |
|------|------|------|
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방 |
| `FORBIDDEN_CHAT_ACCESS` | 403 | 방 참여자 본인 아님 |

---

## 4. 실시간 메시지 송수신 (WebSocket / STOMP)

> **아키텍처: Redis Pub/Sub 단일 채널 팬아웃**
> 분산(다중 서버) 환경에서 서버 간 그물망 직접 연결을 피하기 위해, **Redis Pub/Sub 단일 채널 팬아웃**으로 메시지를 상대방에게 전달한다. 발행 메시지를 모든 인스턴스에 전파하고, 수신자의 WS 세션을 **로컬에 가진 인스턴스만** 실제 push한다.
>
> - **전제**: 발행된 메시지는 **항상 DB에 저장(정본)** 되며, 실시간 전달/푸시는 그 위의 best-effort 전달이다. 실시간으로 못 받아도 수신자는 이력 조회로 볼 수 있다.
> - **DB가 정본**이다. Redis Pub/Sub는 영구 저장소가 아니라 **순간 전파(fan-out)** 채널일 뿐이다. 구독 중인 인스턴스가 없거나 수신자 세션이 없으면 그 메시지는 그냥 흘려보낸다(유실 아님 — 이력으로 확인).
> - **Redis 역할**: 단일 채널(`chat.messages`)로 모든 인스턴스에 메시지를 팬아웃. 서버 간 별도 HTTP/RPC를 만들지 않는다. **presence(`memberId→serverId`)·워커·라우팅 큐·DLX는 두지 않는다** — 팬아웃 + 로컬 세션 필터로 충분하다.
> - **인프라**: ECS 2 태스크 = 모놀리식(채팅 포함) 2. 별도 워커 프로필 없음. (Redis는 Spring Session으로 이미 사용 중 → **새 의존성 0**.)
> - **로컬 세션 필터는 자동**: 각 인스턴스는 조건 없이 `convertAndSendToUser`를 호출하고, 수신자 세션 보유 여부 판정은 Spring의 user destination 레지스트리(`SimpUserRegistry`, 인스턴스별 인메모리)가 자동으로 한다 → 우리가 `memberId→serverId`를 추적할 필요가 없다(presence 제거 근거).
> - **오프라인 푸시 알림은 2차(향후) 범위.** 1차(MVP)는 온라인 실시간 전달 + 오프라인은 이력/`unread` 조회까지.
> - **채팅 가능 시점**: 새 방 생성은 물품 `IN_PROGRESS`일 때만(1장). **이미 존재하는 방의 메시지 전송은 물품 상태와 무관하게 허용**한다(완료 후 수령 장소·시간 조율용).
>
> **연결 라이프사이클 & 전달 destination (중요)**
> - **연결 시점**: 채팅방 진입 시가 아니라 **로그인된 상태로 앱/페이지 로드 시 `/ws` 전역 1개 연결**(끊기면 재연결). 로그인 REST에 끼우는 게 아니라 성공 직후 별도 연결. 방 진입 = 새 연결이 아니라 **구독 추가**. (당근/번개 방식)
> - **연결 직후 `/user/queue/messages` 구독은 필수.** STOMP는 SUBSCRIBE가 있어야 브로커가 그 세션으로 push할 수 있다. **연결만 하고 아무것도 구독 안 하면 실시간 수신 불가.** 방이 아니라 이 유저 큐를 구독해두므로 방 밖에서도 알림/배지를 받는다.
> - **수신은 유저 큐 단일 채널**: 서버는 `convertAndSendToUser(수신자 memberId, "/queue/messages", payload)`로 보낸다. 클라는 payload의 `type`/`roomId`로 분기(열린 방→메시지 append, 그 외→배지/토스트). **룸 토픽(`/sub/chat/rooms/{roomId}`) 구독은 MVP에서 두지 않는다** — 방 진입은 소켓 동작 없이 REST 이력 로드 + 유저 큐 메시지 필터로 처리.
> - **핸드셰이크에서 Principal=memberId 설정**(세션 `LOGIN_MEMBER_ID` 기반) → `/user/...` destination이 그 세션으로 매핑된다. Spring이 `Principal(memberId) ↔ simpSessionId`를 인스턴스별로 관리하므로 우리가 직접 매핑을 만들지 않는다.
> - **브로커 prefix**: SimpleBroker가 `/queue` 처리 + userDestinationPrefix `/user`, 앱 prefix `/pub`. (룸 브로드캐스트 `/sub` 미사용)
> - **브로커 2종 구분**: **Redis Pub/Sub** = 서버↔서버 팬아웃(모든 서버에 전파). **Spring SimpleBroker** = 서버 내에서 클라 SUBSCRIBE를 기억하고 그 WS 세션으로 push(어느 세션으로 줄지).
> - **payload에 `type` 필드**: 현재는 `CHAT_MESSAGE` 한 종류. 향후 읽음/시스템 알림 등으로 확장 시 클라가 `type`으로 분기.

**Redis Pub/Sub 토폴로지**
| 용도 | 채널 | 메시지 | 비고 |
|------|------|--------|------|
| 메시지 팬아웃 | `chat.messages` | `(payload, recipientIds)` 직렬화 | producer=발행 인스턴스, subscriber=**모든 인스턴스**(`RedisMessageListenerContainer`). 각 인스턴스가 받아서 `recipientIds`에 `convertAndSendToUser` |

| 구분 | 경로 | 설명 |
|------|------|------|
| 연결(Handshake) | `GET /ws` | STOMP WebSocket 연결. **로그인 직후 전역 1회**(방 진입과 무관). **세션 쿠키 동봉** 필요, 미인증 시 거절(401). 핸드셰이크에서 **Principal=memberId** 설정. heartbeat 10s/10s |
| 연결 해제(DISCONNECT) | — | 별도 서버측 정리 불필요(presence 없음). Spring이 세션 종료 시 로컬 user 레지스트리에서 자동 제거 |
| 구독(SUBSCRIBE) | `/user/queue/messages` | **연결 직후 필수 (유일).** 내게 오는 모든 메시지/알림 수신(방 밖에서도). 이게 없으면 실시간 수신 불가. 룸 토픽 구독은 두지 않음 |
| 발행(SEND) | `/pub/chat/rooms/{roomId}` | 메시지 전송(앱 prefix). 본문 `{ "content": "..." }` |

**서버 처리 흐름 (발행 시)**
1. 세션에서 `senderId` 확인 → 방 참여자 검증 (아니면 거절)
2. `ChatMessage` **DB 저장(정본)** → `messageId` 채번 *(전파보다 먼저. 오프라인 유실 방지, 재전송 멱등성, 발신자 즉시 ACK 가능)*. 동시에 **수신자(발신자 제외) `ChattingMember.unreadCount += 1`**
3. 발행 인스턴스가 `(payload, recipientIds)`(발신자 제외)를 **Redis 채널 `chat.messages`에 publish** → 발신자에게 ACK(전송됨)
4. **모든 인스턴스**가 `chat.messages`를 구독 중 → 메시지 수신(발행 인스턴스 자신 포함)
5. 각 인스턴스가 `recipientIds`에 대해 **`convertAndSendToUser(수신자 memberId, "/queue/messages", payload)`** 호출
   - 수신자 WS 세션 + 유저 큐 구독을 **로컬에 가진 인스턴스** → 전달 완료 (Spring SimpleBroker가 그 세션 구독을 찾아 push)
   - 로컬에 세션 없는 인스턴스 → Spring user 레지스트리에서 못 찾음 → **자동 no-op**(별도 정리 불필요)

> 5단계 push가 성립하려면 수신자가 **연결 직후 `/user/queue/messages`를 구독**해 둔 상태여야 한다(브로커에 세션 구독이 등록돼 있어야 함). 방 토픽 구독 여부와 무관하게 동작한다.

> **발신자 중복 수신 없음**: `recipientIds`는 발신자를 제외한다. Redis 팬아웃은 발행 인스턴스 자신에게도 메시지를 되돌려주지만, 발신자가 `recipientIds`에 없으므로 자기 메시지를 다시 받지 않는다(발신자는 별도 ACK로 확인).

> **장애 처리(단순)**: 실시간 전달은 best-effort다. 수신자가 오프라인(어느 인스턴스에도 세션 없음)이면 팬아웃 메시지는 모든 인스턴스에서 no-op으로 흘려보내지고, 수신자는 재접속 후 이력/`unread`로 확인한다. 인스턴스 크래시·Redis 일시 단절도 **메시지는 DB 정본**이라 유실이 아니다 — 별도 DLX/재평가 경로를 두지 않는다.

> **다중 기기**: 한 회원이 여러 기기로 접속하면(같은 memberId Principal) Spring user 레지스트리에 세션이 여러 개 등록되어, `convertAndSendToUser`가 **연결된 모든 세션에 전달**한다(presence 단일값 덮어쓰기 제약 없음).

> ⚠️ **운영 주의**: WebSocket이 ALB를 거치므로 **STOMP heartbeat 10s/10s < ALB idle timeout 60s** 로 설정해 유휴 연결이 끊기지 않게 한다. 팬아웃 모델이라 ALB sticky session은 필수 아님(어느 인스턴스에 붙어도 동작).

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
> `type`: 이벤트 종류(현재 `CHAT_MESSAGE` 한 종류). 클라는 `type`으로 처리 분기, `roomId`로 표현 분기(열린 방→append / 그 외→배지·토스트).

---

## 신규 ErrorCode (구현 시 `common/exception/ErrorCode.java`에 추가)

| code | status | message |
|------|--------|---------|
| `FOOD_NOT_FOUND` | 404 | 존재하지 않는 물품입니다. (이미 있으면 재사용) |
| `SELF_CHAT_NOT_ALLOWED` | 400 | 본인 물품에는 채팅할 수 없습니다. |
| `FOOD_NOT_AVAILABLE` | 409 | 새 채팅방을 만들 수 없는 물품 상태입니다. (요청 도메인에 이미 있으면 재사용) |
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방입니다. |
| `FORBIDDEN_CHAT_ACCESS` | 403 | 채팅방 참여자가 아닙니다. |

---

## 향후 구현 시 패키지 구조 (참고)

```
domain/chat/
├── controller/ChatRoomController.java, (WS) ChatStompController.java
├── dto/request, dto/response
├── entity/ChatRoom.java, ChattingMember.java, ChatMessage.java (+ ChatRole)
├── repository/ChatRoomRepository.java, ChattingMemberRepository.java, ChatMessageRepository.java
└── service/ChatRoomService.java, ChatMessageService.java
```
> facade는 기본적으로 두지 않는다. 채팅 동작은 대부분 단일 애그리거트라 `controller → service → repository`로 충분하다. 여러 write 서비스를 한 흐름에서 조율해야 하는 경우(예: FoodFacade)에만 facade를 추가한다.
- WebSocket/STOMP: `WebSocketMessageBrokerConfigurer` 설정 클래스에서 STOMP 엔드포인트(`/ws`)와 in-memory simple broker(`/queue`)·userDestinationPrefix(`/user`)·앱 prefix(`/pub`)를 구성한다. 로컬 WS 전달은 `SimpMessagingTemplate.convertAndSendToUser` 사용.
- Redis Pub/Sub: 발행은 `RedisTemplate.convertAndSend("chat.messages", ...)`, 구독은 `RedisMessageListenerContainer`에 리스너 등록 → 수신 시 `recipientIds`에 `convertAndSendToUser`. (단위 4 `chat-distributed`)
- 전달 seam 권장: 발행 핸들러가 `ChatBroadcaster` 인터페이스에 위임 → 단위 3 = 로컬 전달 구현, 단위 4 = Redis 발행+리스너 구현. 빈 교체로 전환.
- 의존성 추가 필요: `spring-boot-starter-websocket`만. **Redis Pub/Sub는 기존 `spring-boot-starter-data-redis`로 충분(AMQP 불필요)**.
- 인증: `AuthenticationFilter` 화이트리스트에서 채팅 REST 경로 제외(인증 필요). WebSocket `/ws` 핸드셰이크는 세션 쿠키 기반 인증 인터셉터 별도 필요.
- 세션에서 `SessionConst.LOGIN_MEMBER_ID`로 `memberId` 추출 (기존 패턴).
- ⚠️ 현재 코드는 컨트롤러에서 회원ID를 임시로 `@RequestHeader("X-Member-Id")`로 받는다(ArgumentResolver 미구현, `FoodController` 참고). 채팅 구현 시 세션 기반(`SessionConst.LOGIN_MEMBER_ID`)으로 통일할지, 기존 임시 헤더 방식을 따를지는 **구현 단계에서 결정**한다. 본 명세는 "로그인 회원(세션)" 기준으로 서술한다.

---

## 설계 결정 로그 (확정)

- [x] **엔티티 구조**: 3개(`ChatRoom` + `ChattingMember`(조인) + `ChatMessage`). 향후 그룹 채팅(한 방 N명) 확장 대비로 참여자·읽음을 회원당 행으로 분리. 1:1 방은 `(foodId, 신청자 멤버십)`으로 get-or-create(DB 유니크 대신).
- [x] **이력 페이징**: **양방향 커서** 무한 스크롤. 방 진입(`initial`)은 `lastReadMessageId` 기준 위·아래 묶음, 이후 `before`(과거)/`after`(최신). 응답은 `messages` + `anchorMessageId` + `upCursor`/`downCursor` + `hasPrev`/`hasNext` (전체 카운트 쿼리 불필요).
- [x] **채팅 가능 시점**: 새 방 생성은 물품 `IN_PROGRESS`일 때만(`FOOD_NOT_AVAILABLE`). **기존 방 메시지 전송은 상태 무관** 허용(수령 조율용).
- [x] **읽음 처리**: `ChattingMember.lastReadMessageId`(회원당 1행) + 목록 `unreadCount`. 방 입장/최신 이력 조회 시 갱신. 실시간 '읽음' 표시는 **2차**.
- [x] **나가기/차단/메시지 삭제**: **미지원(MVP).** 방은 영구 유지. (필요 시 2차)
- [x] **실시간 라우팅**: Redis Pub/Sub 단일 채널(`chat.messages`) 팬아웃. 발행 인스턴스가 `(payload, recipientIds)` publish → 모든 인스턴스 수신 → 각자 `convertAndSendToUser`(로컬 세션 가진 쪽만 전달). presence·워커·라우팅 큐 없음. 인프라 ECS 2.
- [x] **세션 없음(오프라인)**: 모든 인스턴스에서 no-op으로 흘려보냄(별도 정리 없음). 수신자는 재접속 후 이력/`unread`로 확인.
- [x] **장애(크래시/Redis 단절)**: best-effort. 메시지는 DB 정본이라 유실 아님. DLX/재평가 경로 없음.
- [x] **세션 정합성**: presence 미사용. Spring user 레지스트리(인스턴스별 인메모리)가 세션 종료 시 자동 정리. 다중 기기는 다중 세션으로 모두 전달.
- [x] **Redis 토폴로지**: 단일 채널 `chat.messages`(`(payload, recipientIds)` 팬아웃). 기존 `spring-boot-starter-data-redis`로 충분.
- [x] **STOMP/heartbeat**: `/ws`·`/pub`·`/queue`·`/user`, heartbeat 10s/10s, ALB idle timeout 60s, sticky session 불필요.
- [x] **확장 경로**: 대규모 트래픽 시 RabbitMQ directed routing(워커+presence+DLX)은 "향후 확장" 섹션에 보존.

## 2차(향후) 범위

- 오프라인 회원 **푸시 알림(FCM/APNs)** 연동 — 수신자가 어느 인스턴스에도 세션이 없을 때의 전달 경로.
- 실시간 '읽음' 표시(카톡 1).
- 채팅 방/메시지 나가기·차단·삭제.

---

## 향후 확장: RabbitMQ directed routing (2차)

> 아래는 **대규모 트래픽**에서 단일 채널 팬아웃(모든 인스턴스가 모든 메시지를 수신)이 부담이 될 때의 대안 설계다.
> MVP는 위 본문(Redis Pub/Sub 팬아웃)으로 충분하며, 이 섹션은 **확장 시점에 도입**할 참고 설계로만 보존한다.

**개념**: 서버 간 그물망 직접 연결을 피하기 위해 **Redis presence(`memberId→serverId`) + RabbitMQ 큐 중계(워커 directed routing)**로 수신자가 붙은 **그 서버에만** 보낸다(팬아웃 대신 directed).

- **인프라**: ECS 3 = 모놀리식(채팅 포함) 2 + **워커 프로필 1**. `serverId` = 프로세스 기동 UUID.
- **Redis presence**: 연결(`/ws` 핸드셰이크) 시 `memberId → serverId`(TTL 60s) 등록, DISCONNECT 시 제거. 각 인스턴스가 `@Scheduled`(~30s)로 로컬 연결 회원 키 일괄 `EXPIRE` refresh(TTL 60 > 갱신 30). 단일 세션(새 기기 접속 시 덮어쓰기).
- **RabbitMQ 토폴로지**
  | 용도 | exchange | queue | 비고 |
  |------|----------|-------|------|
  | 처리 큐 | `chat.processing` (direct, named) | `chat.processing` (durable) | producer=모든 앱 인스턴스, consumer=워커(경쟁 소비) |
  | 라우팅 | `chat.routing` (direct, routing key=`serverId`) | `chat.routing.{serverId}` | args: `x-message-ttl`, `x-dead-letter-exchange=chat.routing.dlx`, `x-expires`. consumer=해당 인스턴스 |
  | DLX(크래시) | `chat.routing.dlx` | `chat.routing.dead` | consumer=재평가 컨슈머(워커) |
- **발행 흐름**: DB 저장 → 처리 큐 등록 → 발신자 ACK → **워커**가 presence 조회 → online이면 `serverId` 라우팅 큐 publish / offline이면 (2차) 푸시 → **대상 서버**가 자기 큐 소비 → `convertAndSendToUser`.
- **장애**: 세션 없음(서버 생존) → presence 제거 + (2차)푸시(self-healing). 서버 크래시 → 라우팅 큐 `x-message-ttl` → DLX(`chat.routing.dead`) → 재평가 컨슈머(동일 serverId면 stale 제거 / 다른 serverId면 재전달). retry cap(`x-death`) 초과 시 폐기 + 로그.
- **의존성**: `spring-boot-starter-amqp` 추가 필요. 테스트는 Testcontainers RabbitMQ.
