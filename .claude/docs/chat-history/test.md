# chat-history 테스트 보고 (1단계)

> `/implement chat-history` 1단계 산출물. **테스트만 작성**(프로덕션 골격 없음)했으므로
> 현재는 참조 타입 미존재로 **컴파일 실패(red) 상태가 정상**이다.
> 검토 후 "구현 들어가" 지시가 있으면 2단계로 넘어간다.

## 테스트가 가정하는 구현 표면(API surface)

테스트가 참조하는, 2단계에서 만들 타입/메서드는 다음과 같다.

### 신규 예외 (`com.foodservice.common.exception.chat`)
- `ChatRoomNotFoundException` → `ErrorCode.CHAT_ROOM_NOT_FOUND` (404)
- `ForbiddenChatAccessException` → `ErrorCode.FORBIDDEN_CHAT_ACCESS` (403)
- (`ErrorCode`에 위 2개 enum 상수 추가 필요)

### Repository 메서드
- `ChatMessageRepository` — 파생 메서드명이 한도(20자)를 크게 넘어 **`@Query`(JPQL)로 분리**
  (가이드 `3layer/repository` §2-1/§3). 메서드명은 짧게, 의도는 JPQL로 드러낸다.
  - `List<ChatMessage> findLatestMessages(Long roomId, Limit limit)`
    `where m.roomId = :roomId order by m.messageId desc` — cursor 없는 최신 조회.
    **마지막 메시지 1건은 `Limit.of(1)`로 재사용**(전용 메서드 없음).
  - `List<ChatMessage> findMessagesBefore(Long roomId, Long cursor, Limit limit)`
    `where m.roomId = :roomId and m.messageId < :cursor order by m.messageId desc` — 과거 방향 조회.
  - `List<ChatMessage> findLastMessagesByRoomIds(List<Long> roomIds)`
    `where m.messageId in (select max(m2.messageId) ... group by m2.roomId)` — 목록의 방별 마지막 메시지 일괄(N+1 제거).
- `ChattingMemberRepository` — 짧고 자명하여 **이름 메서드 유지**
  - `List<ChattingMember> findByMemberId(Long memberId)`
  - `List<ChattingMember> findByRoomIdIn(List<Long> roomIds)` — 목록의 상대 멤버 일괄 조회
  - `Optional<ChattingMember> findByRoomIdAndMemberId(Long roomId, Long memberId)`

> **안읽음 수는 쿼리하지 않는다.** `ChattingMember.unreadCount`(비정규화)를 읽기만 한다(아래 Entity). 증가(+1)는 chat-realtime.

### Entity
- `ChatMessage`에 복합 인덱스 추가: `@Table(indexes = @Index(columnList = "room_id, message_id"))` — 이력/마지막메시지 커서 조회용.
- `ChattingMember.unreadCount`(비정규화 카운터) + `resetUnreadCount()` — 읽음 시 0.
- `ChattingMember.updateLastReadMessageId(Long messageId)` — 읽음 위치 갱신용(이동 지점·읽음표시·정합성 truth).

### DTO (일반 클래스, `com.foodservice.domain.chat.dto.response`)
> 기존 `ChatRoomCreateResponse`와 동일 컨벤션: `@Getter @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor` + `static of(...)`. (record 미사용)
- `ChatRoomListResponse` — `roomId, foodId, foodName, partnerNickName, lastMessage, lastMessageAt(LocalDateTime), unreadCount(long)`
- `ChatHistoryResponse` — `messages(List<ChatMessageResponse>), nextCursor(Long), hasNext(boolean)`
- `ChatMessageResponse` — `messageId, senderId, senderNickName, content, mine(boolean), createdAt(LocalDateTime)`

### Service — `ChatHistoryService`
- `List<ChatRoomListResponse> getMyRooms(Long memberId)`
- `ChatHistoryResponse getMessages(Long memberId, Long roomId, Long cursor, int size)`

### Controller — `ChatHistoryController`
- `GET /api/v1/members/me/chat/rooms`
- `GET /api/v1/chat/rooms/{roomId}/messages?cursor=&size=` (size 기본 20)

---

## 사용자 흐름 (이 단위가 지원하는 시나리오)

이 단위는 "채팅 목록을 보고 → 방에 들어가 이전 대화를 읽는" 조회/읽음 흐름을 담당한다.
각 테스트가 흐름의 어디를 검증하는지 함께 적는다.

### 흐름 1. 채팅 목록 화면 진입
> 사용자가 채팅 탭에 들어가면 자신이 참여한 모든 방이 **최근 대화순**으로 보인다.
> 각 방에는 상대 닉네임 · 물품명 · 마지막 메시지 · 안읽음 배지가 표시된다.

`GET /members/me/chat/rooms`
→ Service `getMyRooms`: 내 멤버십 조회(`findByMemberId`) → roomIds로 **일괄 조회**(방 `findAllById`,
상대 `findByRoomIdIn`, 닉/물품 `findAllById`, 마지막 메시지 `findLastMessagesByRoomIds`) 후 메모리 조립.
**안읽음 수 = 내 `ChattingMember.unreadCount`(비정규화, 추가 쿼리 0)** → 마지막 메시지 시각 내림차순 정렬.

- 검증: `getMyRooms`(필드 조립·unread 컬럼), `getMyRoomsOrderedByLastMessage`(정렬), 컨트롤러 `getMyRooms`(직렬화).

### 흐름 2. 방 입장 — 최신 대화 + 자동 읽음
> 방을 누르면 최신 메시지가 아래에 깔리고(최신→과거), 본 시점까지 **읽음 처리**된다.
> 내가 보낸 말풍선과 상대 말풍선을 구분(`mine`)해 좌우로 배치한다.

`GET /chat/rooms/{roomId}/messages` (cursor 없음)
→ Service `getMessages`: 방 존재·참여자 검증 → `findLatestMessages(roomId, Limit.of(size+1))`로
`hasNext` 판정 → `mine`/`senderNickName` 매핑 → **부수효과**로 내 `lastReadMessageId`를
조회된 최신 messageId로 갱신.

- 검증: `getMessagesLatest`(정렬·mine), `getMessagesHasNext`(size+1/nextCursor),
  `getMessagesUpdatesLastRead`(읽음 갱신), 컨트롤러 `getMessages`.

### 흐름 3. 위로 스크롤 — 과거 무한 스크롤
> 위로 당기면 가진 것 중 가장 오래된 메시지(`cursor`)보다 더 과거를 불러온다.
> 과거를 다시 보는 것은 읽음 위치를 **낮추지 않는다**.

`GET /chat/rooms/{roomId}/messages?cursor={oldestId}&size={n}`
→ Service `getMessages`: `findMessagesBefore(roomId, cursor, Limit.of(size+1))` →
`nextCursor`(묶음 중 가장 오래된 id) · `hasNext` 반환, 읽음 갱신 없음.

- 검증: `getMessagesWithCursor`(과거 조회), `getMessagesWithCursorDoesNotUpdateLastRead`(읽음 미갱신),
  컨트롤러 `getMessagesWithCursorAndSize`(파라미터 전달).

### 흐름 4. 권한 / 예외
> 없는 방이나 내가 참여하지 않은 방의 이력은 볼 수 없다.

- 없는 방 → 404 `CHAT_ROOM_NOT_FOUND` (`getMessagesChatRoomNotFound` × 서비스/컨트롤러)
- 미참여자 → 403 `FORBIDDEN_CHAT_ACCESS` (`getMessagesForbidden` × 서비스/컨트롤러)

---

## 레이어별 작성한 테스트

### Repository

**`ChatMessageRepositoryTest`** (신규)
| 메서드 | 검증 케이스 |
|--------|-------------|
| `findLatestMessages` | cursor 없는 최신 조회: 해당 방만, messageId 내림차순, limit 적용. 다른 방 메시지 제외 |
| `findLatestMessages` (Limit.of(1)) | 방의 최근 메시지 1건 반환 / 메시지 없으면 빈 리스트 |
| `findMessagesBefore` | cursor보다 작은(과거) 메시지만 내림차순 |
| `findLastMessagesByRoomIds` | 여러 방의 마지막 메시지 일괄(방별 1건) / 메시지 없는 방 제외 |

**`ChattingMemberRepositoryTest`** (기존 파일에 추가)
| 메서드 | 검증 케이스 |
|--------|-------------|
| `findByMemberId` | 회원이 참여한 방 멤버십만 전부 조회(다른 회원 것 제외) |
| `findByRoomIdIn` | 여러 방의 멤버십 일괄 조회(다른 방 제외) |
| `findByRoomIdAndMemberId` | (방, 회원) 멤버십 단건 조회 / 미참여 시 빈 결과 |

### Service — `ChatHistoryServiceTest` (통합, `IntegrationTestSupport` 상속)
| 테스트 | 검증 케이스 |
|--------|-------------|
| `getMyRooms` | 상대 닉네임·물품명·마지막 메시지·안읽음 수(비정규화 컬럼) 매핑 |
| `getMyRoomsOrderedByLastMessage` | 마지막 메시지 시각 내림차순 정렬 |
| `getMessagesLatest` | cursor 없음: 최신→과거 내림차순, mine 플래그, hasNext=false/nextCursor=null |
| `getMessagesHasNext` | size 초과 시 hasNext=true, nextCursor=가장 오래된 messageId |
| `getMessagesWithCursor` | cursor보다 과거 메시지 조회 |
| `getMessagesUpdatesLastRead` | 최신 조회 시 lastReadMessageId 갱신 + unreadCount 0 리셋(부수효과) |
| `getMessagesWithCursorDoesNotUpdateLastRead` | 과거 스크롤은 lastReadMessageId·unreadCount 미갱신 |
| `getMessagesChatRoomNotFound` | 방 없음 → `ChatRoomNotFoundException` |
| `getMessagesForbidden` | 미참여자 → `ForbiddenChatAccessException` |

### Controller — `ChatHistoryControllerTest` (`@WebMvcTest`, 서비스 Mock)
| 테스트 | 검증 케이스 |
|--------|-------------|
| `getMyRooms` | 200, `data[]` 방 목록 필드 직렬화 |
| `getMessages` | 200, `data.messages[]` + nextCursor/hasNext 직렬화 |
| `getMessagesWithCursorAndSize` | cursor/size 쿼리 파라미터가 서비스로 전달됨 |
| `getMessagesChatRoomNotFound` | 404 + ProblemDetail `title=CHAT_ROOM_NOT_FOUND` |
| `getMessagesForbidden` | 403 + ProblemDetail `title=FORBIDDEN_CHAT_ACCESS` |

---

## 검토 포인트 (사용자 확인 요청)

1. **읽음 갱신 메서드명**: `ChattingMember.updateLastReadMessageId(Long)` 로 가정. 다른 컨벤션 선호 시 알려주세요.
2. **커서 페이징 방식**: `@Query`(JPQL) + Spring Data `Limit` 파라미터로 확정(`size+1`은 서비스에서 처리). 긴 파생 메서드명을 피하기 위함.
3. **DTO는 일반 클래스**로 확정(`ChatRoomCreateResponse`와 동일 Lombok 클래스 스타일, record 미사용).
4. **unreadCount 전략(확정)**: `ChattingMember.unreadCount` **비정규화 카운터**. 목록은 읽기만(추가 쿼리 0),
   읽음 시 0 리셋. 증가(+1)는 chat-realtime(메시지 전송)으로 연기. 설계 고민은 `.claude/트러블슈팅/채팅-안읽은-메시지-수-설계.md`.
5. **메시지 컨트롤러 응답 메시지**: "조회에 성공했습니다." 로 가정(명세 예시 문구).
6. `getMessages` size 기본값 20, cursor optional — 명세 일치.

> 구현·테스트 green 완료(전체 166건 통과). 비정규화 리팩터링까지 반영됨.
