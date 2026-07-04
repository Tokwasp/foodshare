# 채팅방 생성/조회 (chat-room, 단위 1)

물품에 대한 1:1 채팅방을 생성하거나, 이미 있으면 기존 방을 반환하는 API 구현.
향후 그룹 채팅 확장을 위해 참여자를 조인 테이블(`ChattingMember`)로 분리한 3엔티티 토대 포함.

## 사용자 흐름
```
로그인  →  물품 상세에서 "채팅하기"  →  채팅방 생성/입장(roomId 반환)
```

## 서버 흐름
```
세션에서 회원 식별  →  물품 조회  →  본인물품 검증  →  기존 방 탐색  →  (없으면) 상태 검증  →  방+멤버2행 생성
```
1. **회원 식별** (세션) — `@SessionAttribute(LOGIN_MEMBER_ID)`로 요청자 `memberId` 확보
2. **물품 조회** — `FoodRepository.findById`로 물품 조회(없으면 예외)
3. **본인 물품 검증** — `food.memberId == requesterId`면 예외
4. **기존 방 탐색** (JPQL) — `findDirectRoom(foodId, memberId)`로 (물품, 신청자) 멤버십 방 조회. 있으면 그 방 반환(`created=false`, 200), **상태 무관**
5. **상태 검증** — 새 방 생성은 물품 `IN_PROGRESS`일 때만(아니면 예외)
6. **방+멤버 생성** — `ChatRoom`(foodId, ownerId) + `ChattingMember` 2행(등록자 `OWNER` / 요청자 `MEMBER`, `lastReadMessageId=0`) 저장(`created=true`, 201)

---

## 예외 처리
- **존재하지 않는 물품** — `FoodNotFoundException` → **404** · 존재하지 않는 물품입니다.
- **본인 물품에 채팅 요청** — `SelfChatNotAllowedException` → **400** · 본인 물품에는 채팅할 수 없습니다.
- **새 방 생성 불가 상태** (완료/만료/삭제 물품에 첫 방 생성) — `FoodNotAvailableException` → **409** · 새 채팅방을 만들 수 없는 물품 상태입니다.
- **요청 본문 검증 실패** (`foodId` 누락) — `MethodArgumentNotValidException` → **400** · `title=VALIDATION_FAILED`

> 실패 응답은 공통 `GlobalExceptionHandler`가 `ProblemDetail`(`title`/`status`/`detail`)로 변환.

## 엔드포인트
- **`POST /api/v1/chat/rooms`** — 1:1 채팅방 생성/조회 (인증 필요, 세션)

**Request**
```json
{ "foodId": 100 }
```

**Response** `201 Created` (신규) / `200 OK` (기존 방)
```json
{
  "code": 200,
  "data": { "roomId": 700, "foodId": 100, "created": true },
  "message": "채팅방이 생성되었습니다."
}
```

## 테스트
- ✅ **Repository 통합 테스트** (`ChatRoomRepositoryTest`, `extends IntegrationTestSupport`) — `findDirectRoom` 멤버십 조회: 참여자 매칭 / 비참여자 빈 결과 / 다른 물품 제외
- ✅ **Repository 통합 테스트** (`ChattingMemberRepositoryTest`) — `unique(roomId, memberId)` 위반 시 `DataIntegrityViolationException`
- ✅ **Service 통합 테스트** (`ChatRoomServiceTest`) — 신규 생성(방+멤버 2행, `created=true`) / 재요청 시 기존 방 반환·중복 0 / 본인물품 / 없는물품 / 비 `IN_PROGRESS`
- ✅ **Controller 테스트** (`ChatRoomControllerTest`, `@WebMvcTest`) — 201/200 분기, `foodId` 검증 실패 400, 비즈니스 예외 → ProblemDetail `title` 매핑
- ✅ **전체 스위트** `./gradlew test` green — 기존 food/member 회귀 없음

## 비고
- `ChatMessage`/`ChatMessageRepository`는 토대만 선언(직접 사용은 단위 2 이력 조회부터).
- 인증 방식은 세션(`@SessionAttribute`) 채택 — 신규 컨트롤러(`/api/v1` + 세션) 방향에 맞춤. `FoodController`의 임시 `X-Member-Id` 헤더는 미채택.
- 범위 밖: 목록/이력/unread(단위2), 실시간(단위3), 분산(단위4).
