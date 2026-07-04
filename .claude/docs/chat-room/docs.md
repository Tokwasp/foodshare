# chat-room — 채팅방 생성/조회 + 엔티티 토대 (구현 단위 1/4)

> **scoped 문서.** 전체 채팅 명세의 일부만 구현하는 단위다.
> **canonical 명세: [`../chat/docs.md`](../chat/docs.md)** — 공통 규약·전체 맥락은 반드시 그 문서를 따른다.
> 본 문서는 "이 단위에서 무엇을 만들지"만 좁혀 적는다.

## 이 단위의 목표
채팅의 **토대 엔티티**와 **방 생성/조회 REST**를 구현한다. 순수 controller/service/repository
3계층이라 `.claude/skills/test/`·`.claude/skills/3layer/` 가이드가 그대로 적용된다.

## 구현 범위 (canonical 참조)
1. **엔티티** (`../chat/docs.md` "엔티티" 절)
   - `ChatRoom`: `roomId, foodId, ownerId`. (방 호스트=물품 등록자만 보관)
   - `ChattingMember`(조인): `chattingMemberId, roomId, memberId, role(OWNER/MEMBER), lastReadMessageId`. **`unique(roomId, memberId)`**.
   - `ChatMessage`: `messageId, roomId, senderId, content`(TEXT).
   - 컨벤션: `BaseEntity` 상속, `@GeneratedValue(IDENTITY)`, Builder + protected 생성자, **FK는 ID만(@ManyToOne 미사용)**. (food 도메인 엔티티 참고)
   - 이 단위에선 `ChattingMember.lastReadMessageId`를 **선언만** 한다(읽음 로직은 단위 2).
   - `ChatRole` enum(OWNER/MEMBER) 추가.
   - repository: `ChatRoomRepository`, `ChattingMemberRepository`, `ChatMessageRepository`.
2. **채팅방 생성/조회** (`../chat/docs.md` "1. 채팅방 생성/조회")

### `POST /chat/rooms`
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
- **새 방 생성은 물품이 `IN_PROGRESS`일 때만** 허용 (`COMPLETED/EXPIRED/INCOMPLETE`면 거절). 기존 방 반환은 상태 무관.
- **기존 1:1 방 탐색** = `foodId` 방 중 세션 로그인 회원이 `ChattingMember`로 참여한 방. 있으면 반환, 없으면 생성.
- 신규 생성 시 `ChatRoom(foodId, ownerId=food.memberId)` + `ChattingMember` 2행(등록자=OWNER, 로그인 회원=MEMBER, `lastReadMessageId`=0)을 함께 만든다.

**Response (신규 생성 201 / 기존 방 반환 200)**
```json
{ "code": "SUCCESS", "data": { "roomId": 700, "foodId": 100, "created": true }, "message": "채팅방이 생성되었습니다." }
```
> `created`: 이번 요청으로 새로 만들어졌으면 `true`(201), 기존 방을 반환했으면 `false`(200).

**Error** (실패 시 ProblemDetail. `code`→`title`, `설명`→`detail`)
| code | 상태 | 설명 |
|------|------|------|
| `FOOD_NOT_FOUND` | 404 | 존재하지 않는 물품 |
| `SELF_CHAT_NOT_ALLOWED` | 400 | 본인 물품에는 채팅 불가 |
| `FOOD_NOT_AVAILABLE` | 409 | 새 방 생성 불가 상태(완료/만료/삭제). 요청 도메인과 동일 코드 재사용 |

## 범위 밖 (다른 단위)
- 목록·이력·unreadCount·읽음 갱신 → 단위 2 (`chat-history`)
- 실시간 송수신(WebSocket/STOMP) → 단위 3 (`chat-realtime`)
- Redis Pub/Sub 팬아웃(다중 인스턴스 전달) → 단위 4 (`chat-distributed`)
- 나가기/차단/메시지 삭제 → 미지원(MVP)

## 구현 메모
- 패키지 `domain/chat/{controller,service,repository,entity,dto}`.
- **facade 불필요**: 방 생성/조회는 단일 use case라 `controller → ChatRoomService → repository`로 충분하다. 물품은 `FoodRepository`를 읽기로 직접 주입한다. (FoodFacade처럼 여러 write 서비스를 조율할 때만 facade 도입)
- 인증: 세션 `SessionConst.LOGIN_MEMBER_ID`. 채팅 REST 경로는 `AuthenticationFilter` WHITELIST에 넣지 않는다(인증 필요).
- 물품 상태/소유자는 `FoodRepository`로 조회. `FOOD_NOT_AVAILABLE`는 요청 도메인과 동일 코드 재사용(`ErrorCode`에 없으면 추가).
- 신규 ErrorCode: `SELF_CHAT_NOT_ALLOWED`, `CHAT_ROOM_NOT_FOUND`, `FORBIDDEN_CHAT_ACCESS` 등은 `../chat/docs.md` "신규 ErrorCode" 표 참조(이 단위에선 방 생성에 쓰는 것만).
