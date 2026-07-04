# 채팅 아키텍처 한눈 요약

> 빠른 참조용 요약. 정식(canonical) 명세는 [`docs.md`](./docs.md),
> 구현 단위별 문서는 `../chat-room`, `../chat-history`, `../chat-realtime`, `../chat-distributed`.
> 본 문서와 정식 명세가 다르면 정식 명세가 우선.

## 한 줄 개요
음식 물품 상세에서 1:1 채팅. 방/이력은 **REST**, 실시간은 **WebSocket(STOMP)** +
**Redis Pub/Sub 단일 채널 팬아웃**. 메시지 정본은 항상 **DB**. (대규모 확장 시 RabbitMQ directed routing은 canonical "향후 확장" 섹션 참고)

> **엔티티 3개**(그룹 채팅 확장 대비): `ChatRoom`(roomId, foodId, ownerId) +
> `ChattingMember`(조인: roomId, memberId, role, lastReadMessageId, unreadCount, `unique(roomId,memberId)`) +
> `ChatMessage`(roomId, senderId, content). 참여·읽음 위치·안읽음 수(비정규화)는 회원당 1행(N명 일반화).
> 1:1 방은 `(foodId, 신청자 멤버십)`으로 get-or-create.

## 1. 인프라
- ECS 2 태스크 = **모놀리식(채팅 포함) 2**. 별도 워커 없음.
- Redis(ElastiCache)는 Spring Session + Pub/Sub 겸용 → **새 의존성 0**(`spring-boot-starter-data-redis` 기존).

## 2. 연결 & 세션 매핑 (로그인 직후)
- 로그인 성공 직후 `/ws` **전역 1회 연결**(방 진입 때가 아님, 끊기면 재연결).
- 연결 직후 `/user/queue/messages` **구독 필수**(이게 없으면 실시간 수신 불가).
- 핸드셰이크에서 **Principal = memberId** 설정(세션 쿠키 기반). → 이거 하나만 우리가 설정.
- 나머지 `Principal(memberId) ↔ simpSessionId` 매핑은 **Spring user 레지스트리(`SimpUserRegistry`, 인스턴스별 인메모리)가 자동 관리**. `convertAndSendToUser(memberId, ...)`가 그 매핑으로 세션 큐를 찾음.
- **presence(`memberId→serverId`)·serverId UUID 없음** — 로컬 user 레지스트리가 대체.

## 3. 메시지 전송 흐름 (분산 팬아웃)
Redis 채널은 **1개**(`chat.messages`). 모든 인스턴스가 구독.

1. (발행 인스턴스) 세션으로 발신자 확인 → 방 참여 검증
2. **`ChatMessage` DB 저장(정본)** → `messageId` 채번 → 발신자 ACK. 수신자(발신자 제외) `unreadCount += 1`
3. `(payload, recipientIds)`(발신자 제외)를 **Redis 채널 `chat.messages`에 publish**
4. **모든 인스턴스**가 채널 수신(발행 인스턴스 포함)
5. 각 인스턴스가 `recipientIds`에 `convertAndSendToUser(memberId, "/queue/messages", payload)`
   → 수신자 세션을 **로컬에 가진 인스턴스만** push, 나머지는 **자동 no-op**

> 순서 요약: **앱 → DB 저장 → Redis 채널 publish → 전 인스턴스 팬아웃 → (로컬 세션 가진 인스턴스만) 구독 세션 push**.

## 4. 사용자 부재 / 장애 처리 (단순)
- 수신자 **오프라인**(어느 인스턴스에도 세션 없음) → 모든 인스턴스에서 no-op으로 흘려보냄. 재접속 후 이력/`unread`로 확인. 푸시는 *(2차)*.
- 발신자 **중복 수신 없음**: `recipientIds`가 발신자 제외 → 자기 메시지 되돌려받지 않음.
- 인스턴스 **크래시 / Redis 일시 단절** → best-effort. 메시지는 **DB 정본**이라 유실 아님. 별도 DLX/재평가 경로 없음.

## 5. 요청/응답 형식 (CLAUDE.md 규약)
- **REST**(방 생성/목록/이력): 성공 `ApiResponse`(code/data/message) + 실패 `ProblemDetail` 준수. ✅
- **WebSocket(STOMP)**: 메시지는 envelope 없이 원시 payload `{type, messageId, roomId, senderId, senderNickName, content, createdAt}`.
- ⚠️ **미정(갭)**: WS 처리 중 에러(방 미참여 거절·content 검증 실패 등)의 응답 형식.
  ProblemDetail은 HTTP 전용이라 STOMP에는 적용 불가 → STOMP ERROR 프레임/에러 payload 형식은 구현 단계(단위 3)에서 결정.

## 범위 / 상태
- **2차(향후)**: 오프라인 푸시(FCM/APNs), 실시간 '읽음' 표시, 방/메시지 나가기·차단·삭제.
- **현재**: 명세 확정, 구현 미착수.
