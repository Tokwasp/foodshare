# chat-room (단위 1) 테스트 보고

> `/implement chat-room` 1단계 산출물. 명세: `.claude/docs/chat-room/docs.md` (+ canonical `../chat/docs.md`).
> **현재 red(컴파일 실패)가 정상** — 프로덕션 클래스를 아직 만들지 않았다(엄격 test-first).
> 검토 후 "구현 들어가" 지시를 받으면 2단계(구현)로 진행한다.

## 작성한 테스트 (4개 파일)

### 1. `ChatRoomRepositoryTest` (`extends IntegrationTestSupport` = `@SpringBootTest`)
`findDirectRoom(foodId, memberId)` — 1:1 방 멤버십 조회(커스텀 `@Query`) 검증.
| 메서드 | 검증 |
|--------|------|
| `findDirectRoomReturnsRoomWhenMemberParticipates` | 물품+신청자가 멤버인 방을 찾는다 |
| `findDirectRoomReturnsEmptyWhenNotParticipant` | 참여 안 한 회원이면 빈 결과 |
| `findDirectRoomReturnsEmptyForDifferentFood` | 다른 물품의 방은 조회 안 됨 |

### 2. `ChattingMemberRepositoryTest` (`extends IntegrationTestSupport`)
| 메서드 | 검증 |
|--------|------|
| `uniqueRoomIdAndMemberId` | `unique(roomId, memberId)` 위반 시 `DataIntegrityViolationException` |

### 3. `ChatRoomServiceTest` (통합, `extends IntegrationTestSupport`)
get-or-create는 "기존 방 존재 여부"라는 DB 상태에 의존 → 통합 테스트로 작성.
| 메서드 | 검증 |
|--------|------|
| `createOrGetRoomNew` | 신규 시 방 + `ChattingMember` 2행(등록자 OWNER / 요청자 MEMBER), `created=true` |
| `createOrGetRoomReturnsExisting` | 동일 (물품, 회원) 재요청 시 같은 방 반환, `created=false`, 방·멤버 중복 없음 |
| `createOrGetRoomSelfChat` | 본인 물품 → `SelfChatNotAllowedException` |
| `createOrGetRoomFoodNotFound` | 없는 물품 → `FoodNotFoundException` |
| `createOrGetRoomFoodNotAvailable` | 비 `IN_PROGRESS` 물품 신규 → `FoodNotAvailableException` |

### 4. `ChatRoomControllerTest` (`@WebMvcTest(ChatRoomController.class)`, `@MockitoBean`)
| 메서드 | 검증 |
|--------|------|
| `createRoomNew` | 201 + `data{roomId,foodId,created:true}` + 메시지 |
| `createRoomExisting` | 200 + `created:false` |
| `createRoomWithoutFoodId` | `foodId` null → 400 `title=VALIDATION_FAILED` |
| `createRoomSelfChat` | 비즈니스 예외 → ProblemDetail `title=SELF_CHAT_NOT_ALLOWED` |

## 이 테스트가 전제하는 프로덕션 API (2단계에서 생성)
- **엔티티** `domain/chat/entity/`: `ChatRoom`(roomId, foodId, ownerId), `ChattingMember`(roomId, memberId, role, lastReadMessageId, `unique(roomId,memberId)`), `ChatMessage`, `ChatRole`(OWNER/MEMBER). `Food.java` 컨벤션(Builder + protected, FK는 ID만).
- **레포지토리**: `ChatRoomRepository.findDirectRoom(Long foodId, Long memberId)`(FK ID-only라 세타조인 `@Query`, `Optional<ChatRoom>`), `ChattingMemberRepository.findByRoomId(Long)`, `ChatMessageRepository`.
- **DTO**: `ChatRoomCreateRequest{ @NotNull Long foodId }`(+@NoArgsConstructor/@Getter for Jackson), `ChatRoomCreateResponse{ Long roomId; Long foodId; boolean created; static of(...) }`.
- **서비스**: `ChatRoomService.createOrGetRoom(Long requesterId, Long foodId) -> ChatRoomCreateResponse`. 물품은 `FoodRepository.findById`로 조회(소유자/상태 검증).
- **컨트롤러**: `POST /api/v1/chat/rooms`, `@SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId` + `@Valid @RequestBody`. `created`로 201/200 분기.
- **ErrorCode 추가**: `FOOD_NOT_FOUND`(404), `FOOD_NOT_AVAILABLE`(409), `SELF_CHAT_NOT_ALLOWED`(400).
- **예외**(`BusinessException` 상속): `FoodNotFoundException`/`FoodNotAvailableException`(`common/exception/food`), `SelfChatNotAllowedException`(`common/exception/chat`).

## 결정/메모 (검토 포인트)
- **인증**: 컨트롤러는 세션(`@SessionAttribute(LOGIN_MEMBER_ID)`) 채택 — 신규 컨트롤러(`AuthController`/`MemberController`)의 `/api/v1` + 세션 방향에 맞춤. `FoodController`의 임시 `X-Member-Id` 헤더는 따르지 않음. 컨트롤러 테스트는 `.sessionAttr(...)`로 주입.
- **Base path** `/api/v1/chat/rooms` (명세/신규 컨트롤러 기준).
- **테스트 베이스**: repository·service 모두 `IntegrationTestSupport`(`@SpringBootTest`, `@Transactional` 롤백 격리) 상속 — 컨텍스트 1회 로딩 공유(repository/SKILL.md §2-1). **컨트롤러만 `@WebMvcTest` 따로**. (`@DataJpaTest` 미사용)
- `ChatMessage`/`ChatMessageRepository`는 단위 1에서 **선언만** 하고 직접 테스트는 단위 2(이력)에서.

## 범위 밖
- 목록/이력/unreadCount/읽음(단위 2), 실시간(단위 3), 분산(단위 4).
