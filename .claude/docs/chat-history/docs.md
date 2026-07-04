# chat-history — 채팅방 목록 + 이력 조회 + 읽음 (구현 단위 2/4)

> **scoped 문서.** 전체 채팅 명세의 일부만 구현하는 단위다.
> **canonical 명세: [`../chat/docs.md`](../chat/docs.md)** — 공통 규약·전체 맥락은 그 문서를 따른다.
> **선행 단위: `chat-room`**(엔티티·repository가 이미 있어야 함).

## 이 단위의 목표
조회/읽음(query) 쪽 REST를 구현한다. controller/service/repository 3계층이라 기존
`.claude/skills/test/`·`.claude/skills/3layer/` 가이드 그대로 적용.

## 구현 범위 (canonical 참조)

### 1. `GET /members/me/chat/rooms` — 내 채팅방 목록
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
- `partnerNickName`은 상대 회원 닉네임을 `MemberRepository`로 조회 매핑(엔티티에 저장 안 함).
- **`unreadCount`** = 내 `ChattingMember.unreadCount`(비정규화 카운터). 메시지 수신 시 +1(chat-realtime), 읽음 시 0 리셋되어 목록은 저장값을 읽기만 한다. `lastReadMessageId`는 이동 지점·읽음표시·정합성 truth로 유지.

### 2. `GET /chat/rooms/{roomId}/messages` — 채팅 이력 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/chat/rooms/{roomId}/messages?cursor={oldestMessageId}&size={size}` |
| 인증 | 필요 (방 참여자 본인) |
| 설명 | 채팅방의 이전 메시지를 과거 방향 커서로 무한 스크롤 조회한다. |

**Query Params**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| cursor | long | X | 가진 것 중 **가장 오래된 메시지 id**. 없으면 최신부터. 전달 시 `messageId < cursor` 인 과거 메시지를 가져온다. |
| size | int | X | 페이지 크기. 기본 20 (권장 20~30) |

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
    "nextCursor": 8980,
    "hasNext": true
  },
  "message": "조회에 성공했습니다."
}
```
- `messages`: `messageId` 내림차순(최신→과거). `nextCursor`: 묶음 중 가장 오래된 messageId. `hasNext`: false면 더 없음(`nextCursor`는 null).
- **읽음 갱신(부수효과)**: cursor 없는(최신) 조회 시 내 `ChattingMember.lastReadMessageId`를 조회된 최신 messageId로 업데이트. 과거 스크롤(cursor 전달)은 읽음 위치를 낮추지 않는다.

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방 |
| `FORBIDDEN_CHAT_ACCESS` | 403 | 방 참여자 본인 아님 |

## 범위 밖 (다른 단위)
- 엔티티/방 생성 → 단위 1 (`chat-room`)
- 실시간 메시지 생성/송수신 → 단위 3 (`chat-realtime`). **이 단위 테스트에선 `ChatMessage`를 repository로 직접 seed**해 목록/이력/unreadCount를 검증한다.
- Redis Pub/Sub 팬아웃(다중 인스턴스 전달) → 단위 4 (`chat-distributed`)

## 구현 메모
- 목록/이력 응답도 `ApiResponse`로 감싼다.
- 커서 페이징은 전체 카운트 쿼리 없이 `messageId < cursor` + `size+1`로 hasNext 판정 권장.
- 방 참여자 검증(세션 회원이 해당 방의 `ChattingMember`인지) 실패 시 `FORBIDDEN_CHAT_ACCESS`.
