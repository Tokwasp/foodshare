# 채팅방 목록 · 이력 조회 · 읽음 처리 (chat-history)

채팅 조회(query) 측 REST를 구현한다 — 내 채팅방 목록과 채팅 이력(과거 방향 커서 무한스크롤)을 조회하고, 최신 조회 시 읽음 위치를 갱신한다. (선행 단위 `chat-room`의 엔티티·repository 위에 올라간다.)

## 사용자 흐름
```
채팅 목록 진입(방 목록·안읽음)  →  방 입장(최신 대화·자동 읽음)  →  위로 스크롤(과거 무한스크롤)
```

## 서버 흐름

### 1) 내 채팅방 목록 — `GET /members/me/chat/rooms`
```
내 멤버십 조회  →  roomIds 일괄 조회(상대·물품명·마지막 메시지)  →  메모리 조립  →  최근 메시지순 정렬
```
1. **내 멤버십 조회** (`findByMemberId`) — 로그인 회원이 참여한 `ChattingMember` 목록 → `roomIds` 추출
2. **일괄 조회(배치, N+1 제거)** — 방(`findAllById`), 상대 멤버(`findByRoomIdIn`), 상대 닉·물품명(`MemberRepository`/`FoodRepository`의 `findAllById`), 마지막 메시지(`findLastMessagesByRoomIds`)를 한 번에 받아 `roomId`로 Map 구성 후 메모리 조립
3. **안읽음 수** (비정규화) — 내 `ChattingMember.unreadCount`를 그대로 사용(추가 쿼리 0). 카운터는 메시지 수신 시 +1(chat-realtime)·읽음 시 0 리셋으로 유지
4. **정렬** — 마지막 메시지 시각 내림차순(메시지 없는 방은 뒤로)

### 2) 채팅 이력 — `GET /chat/rooms/{roomId}/messages`
```
방·참여자 검증  →  size+1 조회로 hasNext 판정  →  mine·닉네임 매핑  →  (최신 조회 시) 읽음 갱신
```
1. **방·참여자 검증** — 방 존재(`CHAT_ROOM_NOT_FOUND`) + 내가 그 방의 `ChattingMember`인지(`FORBIDDEN_CHAT_ACCESS`)
2. **커서 페이징** — cursor 없으면 `findLatestMessages`, 있으면 `findMessagesBefore(messageId < cursor)`. 둘 다 `Limit.of(size+1)`로 가져와 `hasNext` 판정 후 size만큼 자른다
3. **응답 매핑** — 발신자 닉네임 일괄 조회 후 `mine`(senderId == 나) 플래그·`senderNickName` 매핑, `nextCursor`(묶음 중 가장 오래된 messageId, 없으면 null)
4. **읽음 갱신(부수효과)** — cursor 없는(최신) 조회일 때만 내 `lastReadMessageId`를 조회된 최신 messageId로 갱신. 과거 스크롤은 읽음 위치를 낮추지 않음

---

## 예외 처리
- **존재하지 않는 채팅방** — `ChatRoomNotFoundException` → **404** · 존재하지 않는 채팅방입니다.
- **방 참여자 본인 아님** — `ForbiddenChatAccessException` → **403** · 해당 채팅방에 대한 권한이 없습니다.

## 엔드포인트

### `GET /api/v1/members/me/chat/rooms` — 내 채팅방 목록 (인증 필요)

**Response** `200 OK`
```json
{
  "code": 200,
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

### `GET /api/v1/chat/rooms/{roomId}/messages?cursor={oldestMessageId}&size={size}` — 채팅 이력 (인증 필요, 방 참여자)

- `cursor`(선택, long): 가진 것 중 가장 오래된 messageId. 없으면 최신부터. 전달 시 `messageId < cursor` 과거 조회
- `size`(선택, int): 기본 20

**Response** `200 OK`
```json
{
  "code": 200,
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
    "nextCursor": 8980,
    "hasNext": true
  },
  "message": "조회에 성공했습니다."
}
```

## 테스트
- ✅ **Repository 테스트** (`ChatMessageRepositoryTest`) — 최신 조회(내림차순·limit·방 필터), `Limit.of(1)` 마지막 메시지/빈 방, cursor 과거 조회, 방별 마지막 메시지 일괄(`findLastMessagesByRoomIds`)
- ✅ **Repository 테스트** (`ChattingMemberRepositoryTest`) — `findByMemberId`(참여 방 전부), `findByRoomIdAndMemberId`(참여/미참여)
- ✅ **통합 테스트** (`ChatHistoryServiceTest`) — 목록 조립(안읽음=비정규화 컬럼)·최근순 정렬, 이력 최신/커서/hasNext·nextCursor, 읽음 갱신 부수효과(최신만 lastRead 갱신 + unreadCount 0 리셋), 권한·방 없음 예외
- ✅ **컨트롤러 테스트** (`ChatHistoryControllerTest`, `@WebMvcTest`) — 목록/이력 직렬화, cursor·size 파라미터 전달, 404 `CHAT_ROOM_NOT_FOUND` / 403 `FORBIDDEN_CHAT_ACCESS` ProblemDetail

> 전체 chat 도메인 테스트 38건 통과(신규 chat-history 25건 + 기존 chat-room 유지).

## 참고 — 비고
- `unreadCount`는 **비정규화 카운터**(`ChattingMember.unreadCount`)로 관리한다. 목록 조회 시 추가 쿼리 없이 읽기만 하여 N+1이 없다. 카운터 **증가(+1)는 chat-realtime**(메시지 전송) 단위로 연기, chat-history는 컬럼·읽기·읽음 리셋만 담당. 설계 고민 과정은 `.claude/트러블슈팅/채팅-안읽은-메시지-수-설계.md` 참고.
- 조회 컨트롤러는 방 생성(`ChatRoomController`, command)과 분리된 `ChatHistoryController`(query)로 둔다.
