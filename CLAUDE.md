# 음식 나눔 서비스 API 명세서 (v3)

> 본 명세서는 제공된 ERD(MEMBER / FOOD / REQUEST_FOOD / IMAGE)와 요구사항 명세서를 기준으로 작성되었습니다.
> 인증 방식은 **Spring Session (Redis)** 기반 세션 쿠키를 사용합니다.
> 실패 응답은 **RFC 7807 `ProblemDetail`** 형식을 사용합니다.

---


## 공통 사항

### Base URL
```
/api/v1
```

### 인증
- 로그인 성공 시 서버가 세션을 생성하고 `SESSION` 쿠키를 발급한다.
- 인증이 필요한 API는 요청 시 세션 쿠키를 동봉해야 한다.
- 세션이 없거나 만료된 경우 `401 UNAUTHORIZED` 를 반환한다.

### 공통 응답 포맷
응답은 **성공**과 **실패**의 포맷이 분리된다.

- **성공**: 공통 envelope (`code` / `data` / `message`)
- **실패**: RFC 7807 `ProblemDetail` (`Content-Type: application/problem+json`)

#### 성공 응답
```json
{
  "code": "SUCCESS",
  "data": { },
  "message": "요청에 성공했습니다."
}
```

- `code`: 성공 시 항상 `SUCCESS`
- `data`: 응답 데이터 (없으면 `null`)
- `message`: 결과 메시지

#### 실패 응답 (ProblemDetail)
실패 시 Spring의 `ProblemDetail`(RFC 7807) 형식으로 응답한다.

```json
{
  "type": "about:blank",
  "title": "EMAIL_DUPLICATED",
  "status": 409,
  "detail": "이미 사용 중인 이메일입니다.",
  "instance": "/api/v1/auth/email/send"
}
```

| 필드 | 설명 |
|------|------|
| `type` | 오류 유형 URI. 별도 정의가 없으면 `about:blank` |
| `title` | 오류 코드. `BusinessException`의 `ErrorCode.name()` (예: `EMAIL_DUPLICATED`). 검증 실패 시 `VALIDATION_FAILED` |
| `status` | HTTP 상태 코드 |
| `detail` | 오류 메시지. `ErrorCode.getMessage()` 또는 검증 실패 메시지 |
| `instance` | 요청 경로 (자동 설정) |

> **Error 표 읽는 법**: 본 문서 각 API의 **Error 표에 적힌 `code`** 는 ProblemDetail의 **`title`**, **`상태`** 는 **`status`**, **`설명`** 은 **`detail`**(메시지)에 대응한다.

#### 검증 실패 응답
`@Valid` 요청 본문 검증 실패(`MethodArgumentNotValidException`) 시에는 `title`이 `VALIDATION_FAILED`로 고정되고, 위반된 필드 메시지가 `, ` 로 연결되어 `detail`에 담긴다.

```json
{
  "type": "about:blank",
  "title": "VALIDATION_FAILED",
  "status": 400,
  "detail": "비밀번호는 8~20자, 영문 대소문자와 특수문자를 포함해야 합니다, 닉네임은 2~10자여야 합니다",
  "instance": "/api/v1/members"
}
```

> ⚠️ Bean Validation(`@Valid`)으로 걸러지는 400 오류는 응답에서 `title`이 `VALIDATION_FAILED`로 나간다.
> 아래 Error 표의 특정 400 코드(예: `PASSWORD_POLICY_VIOLATION`)를 `title`로 유지하려면, 해당 검증을 서비스 계층에서 `BusinessException`으로 던져야 한다.

### 공통 HTTP 상태 코드
| 코드 | 설명 |
|------|------|
| 200 OK | 요청 성공 |
| 201 Created | 리소스 생성 성공 |
| 400 Bad Request | 요청 값 검증 실패 |
| 401 Unauthorized | 인증 필요 / 세션 만료 |
| 403 Forbidden | 권한 없음 (타인 리소스 접근 등) |
| 404 Not Found | 리소스 없음 |
| 409 Conflict | 중복 / 상태 충돌 |
| 500 Internal Server Error | 서버 오류 |

---

## 1. 이메일 인증 API

회원 가입 전, 이메일 소유 여부를 인증한다. 인증 정보는 Redis에 임시 저장한다.

### 1-1. 인증 코드 발송
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/auth/email/send` |
| 인증 | 불필요 |
| 설명 | 입력 이메일로 인증 코드를 발송한다. 이미 가입(탈퇴 포함)된 이메일이면 거절한다. |

**Request Body**
```json
{ "email": "user@example.com" }
```

**Response (200)**
```json
{ "code": "SUCCESS", "data": { "expiresIn": 300 }, "message": "인증 코드를 발송했습니다." }
```

**Error** (실패 시 ProblemDetail. `code` → `title`, `설명` → `detail`)
| code | 상태 | 설명 |
|------|------|------|
| `EMAIL_DUPLICATED` | 409 | 이미 가입되었거나 탈퇴한 이메일 |
| `EMAIL_SEND_FAILED` | 500 | 메일 발송 실패 |

---

### 1-2. 인증 코드 검증
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/auth/email/verify` |
| 인증 | 불필요 |
| 설명 | 발송된 인증 코드를 검증한다. 성공 시 회원 가입에 사용할 인증 토큰을 반환한다. |

**Request Body**
```json
{ "email": "user@example.com", "code": "123456" }
```

**Response (200)**
```json
{ "code": "SUCCESS", "data": { "verified": true, "emailVerifyToken": "a1b2c3..." }, "message": "이메일 인증에 성공했습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `CODE_MISMATCH` | 400 | 인증 코드 불일치 |
| `CODE_EXPIRED` | 400 | 인증 코드 만료 |

---

## 2. 회원 API

### 2-1. 회원 가입
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/members` |
| 인증 | 불필요 (이메일 인증 토큰 필요) |
| 설명 | 신규 회원을 등록한다. |

**Request Body**
```json
{
  "email": "user@example.com",
  "emailVerifyToken": "a1b2c3...",
  "password": "Passw0rd!@",
  "nickName": "닉네임",
  "address": {
    "roadAddress": "서울시 ...",
    "detailAddress": "101동 202호"
  }
}
```

**검증 규칙**
- `email`: 이메일 인증 토큰과 일치해야 하며 중복 불가 (탈퇴 이메일 포함)
- `password`: 8~20자, 영문 대소문자 + 특수문자 포함
- `nickName`: 2~10자, 중복 불가
- `address.detailAddress`: 선택 입력

**Response (201)**
```json
{ "code": "SUCCESS", "data": { "memberId": 1 }, "message": "회원 가입에 성공했습니다." }
```

**Error** (실패 시 ProblemDetail. 단, `@Valid` 본문 검증 실패는 `title` = `VALIDATION_FAILED`)
| code | 상태 | 설명 |
|------|------|------|
| `EMAIL_DUPLICATED` | 409 | 이메일 중복 |
| `NICKNAME_DUPLICATED` | 409 | 닉네임 중복 |
| `PASSWORD_POLICY_VIOLATION` | 400 | 비밀번호 정책 위반 |
| `EMAIL_NOT_VERIFIED` | 400 | 이메일 미인증 |

---

### 2-2. 닉네임 중복 확인
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/members/nickname/check?nickName={nickName}` |
| 인증 | 불필요 |

**Response (200)**
```json
{ "code": "SUCCESS", "data": { "available": true }, "message": "사용 가능한 닉네임입니다." }
```

---

### 2-3. 로그인
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/auth/login` |
| 인증 | 불필요 |
| 설명 | 세션을 생성하고 `SESSION` 쿠키를 발급한다. |

**Request Body**
```json
{ "email": "user@example.com", "password": "Passw0rd!@" }
```

**Response (200)** — `Set-Cookie: SESSION=...`
```json
{ "code": "SUCCESS", "data": { "memberId": 1, "nickName": "닉네임" }, "message": "로그인에 성공했습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호 불일치 |

---

### 2-4. 로그아웃
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/auth/logout` |
| 인증 | 필요 |
| 설명 | 세션을 만료시킨다. |

**Response (200)**
```json
{ "code": "SUCCESS", "data": null, "message": "로그아웃 되었습니다." }
```

---

### 2-5. 내 정보 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/members/me` |
| 인증 | 필요 |
| 설명 | 로그인 회원의 상세 정보를 조회한다. (비밀번호 제외) |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": {
    "memberId": 1,
    "email": "user@example.com",
    "nickName": "닉네임",
    "address": { "roadAddress": "서울시 ...", "detailAddress": "101동 202호" },
    "createdAt": "2025-05-28T10:00:00"
  },
  "message": "조회에 성공했습니다."
}
```

---

### 2-6. 회원 정보 수정
| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URL | `/members/me` |
| 인증 | 필요 |
| 설명 | 주소, 닉네임을 변경한다. |

**Request Body** (변경할 필드만 전달)
```json
{
  "nickName": "새닉네임",
  "address": { "roadAddress": "부산시 ...", "detailAddress": "" }
}
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `NICKNAME_DUPLICATED` | 409 | 닉네임 중복 |

---

### 2-7. 회원 탈퇴
| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| URL | `/members/me` |
| 인증 | 필요 |
| 설명 | 회원을 soft delete 처리한다. 탈퇴 이메일은 재가입 불가. |

**Response (200)**
```json
{ "code": "SUCCESS", "data": null, "message": "탈퇴 처리되었습니다." }
```

---

## 3. 음식 나눔 물품(FOOD) API

> **소비기한 인식 흐름**
> 1. `POST /foods/expired-date` 에 소비기한 사진을 업로드하면, 서버가 AI에 인식을 요청해 **소비기한 날짜(`expired`)만** 반환한다. (이 단계에서는 사진을 저장하지 않는 미리보기다.)
> 2. 물품 등록(`POST /foods`) 시 **소비기한 사진(`expiredImage`) + 물품 사진들(`images`) + 인식된 날짜(`expired`)** 를 한 요청에 함께 전송한다.
> 3. 서버는 소비기한 사진을 `image_type = EXPIRED` 로, 물품 사진을 `image_type = BASIC` 으로 저장하고, `expired` 는 범위(어제 이후)만 검증해 그대로 사용한다.

### 3-1. 소비기한 인식 (AI)
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/foods/expired-date` |
| 인증 | 필요 |
| Content-Type | `multipart/form-data` |
| 설명 | 소비기한 사진을 업로드하면 AI에 인식을 요청하여 **소비기한 날짜만** 반환한다. (사진을 저장하지 않는 미리보기) |

**Form Data**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| expiredImage | MultipartFile | O | 소비기한 인증 사진(단일) |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": {
    "expired": "2025-12-31"
  },
  "message": "소비기한을 인식했습니다."
}
```

> 반환된 `expired`는 AI 인식값이다. 등록(`POST /foods`) 시 이 값을 `request.expired` 로 함께 전송한다.

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `EXPIRED_DATE_NOT_DETECTED` | 400 | AI 소비기한 인식 실패 |
| `AI_REQUEST_FAILED` | 500 | AI 인식 요청 실패 |
| `FILE_TOO_LARGE` | 400 | 파일 용량 초과 |
| `UNSUPPORTED_FILE_TYPE` | 400 | 지원하지 않는 형식 |

---

### 3-2. 물품 등록
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/foods` |
| 인증 | 필요 |
| Content-Type | `multipart/form-data` |
| 설명 | 음식 나눔 물품을 등록한다. 등록 시 상태는 `IN_PROGRESS`. |

**제약**
- 회원당 활성화 물품 최대 **10개**
- 가공 식품 / 미개봉 식품만 등록
- `expiredImage`(소비기한 사진, 단일) 필수 → `image_type = EXPIRED` 로 저장
- 소비기한(`expired`)은 `request` 본문으로 전송 (3-1에서 받은 AI 인식값). 서버는 범위(어제 이후)만 검증
- `images`(일반 사진) 다중 등록 가능 → `image_type = BASIC` 으로 저장

**Form Data**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| request | application/json (part) | O | 아래 JSON 본문 |
| expiredImage | MultipartFile | O | 소비기한 사진(단일). `image_type = EXPIRED` 으로 저장 |
| images | List\<MultipartFile\> | X | 일반 사진(다중). `image_type = BASIC` 으로 저장 |

**request part (JSON)**
```json
{
  "foodName": "미개봉 시리얼",
  "capacity": 3,
  "details": "미개봉 가공식품입니다.",
  "expired": "2025-12-31"
}
```

> `expired`는 3-1의 AI 인식값을 그대로 전송한다. 서버는 어제 이후인지만 검증한다.

**Response (201)**
```json
{ "code": "SUCCESS", "data": { "foodId": 100 }, "message": "물품이 등록되었습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FOOD_LIMIT_EXCEEDED` | 409 | 활성 물품 10개 초과 |
| `EXPIRED_IMAGE_REQUIRED` | 400 | `expiredImage`(소비기한 사진) 누락 |
| `FILE_TOO_LARGE` | 400 | 파일 용량 초과 |
| `UNSUPPORTED_FILE_TYPE` | 400 | 지원하지 않는 형식 |

---

### 3-3. 물품 목록 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/foods?status={status}&page={page}&size={size}` |
| 인증 | 불필요 |
| 설명 | 물품 목록을 페이징 조회한다. `status` 필터 선택. |

**Query Params**
| 이름 | 타입 | 설명 |
|------|------|------|
| status | string | `IN_PROGRESS`, `COMPLETED`, `INCOMPLETE`, `EXPIRED` (선택) |
| page | int | 페이지 번호 (기본 0) |
| size | int | 페이지 크기 (기본 20) |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": {
    "content": [
      {
        "foodId": 100,
        "foodName": "미개봉 시리얼",
        "expired": "2025-12-31",
        "capacity": 3,
        "approvedCount": 1,
        "statusTx": "IN_PROGRESS",
        "thumbnailUrl": "https://.../thumb.jpg"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1
  },
  "message": "조회에 성공했습니다."
}
```

---

### 3-4. 물품 상세 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/foods/{foodId}` |
| 인증 | 불필요 |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": {
    "foodId": 100,
    "ownerNickName": "닉네임",
    "foodName": "미개봉 시리얼",
    "expired": "2025-12-31",
    "capacity": 3,
    "approvedCount": 1,
    "details": "미개봉 가공식품입니다.",
    "statusTx": "IN_PROGRESS",
    "images": [
      { "imageId": 10, "accessUrl": "https://.../1.jpg", "imageType": "BASIC" },
      { "imageId": 12, "accessUrl": "https://.../exp.jpg", "imageType": "EXPIRED" }
    ],
    "createdAt": "2025-05-28T10:00:00"
  },
  "message": "조회에 성공했습니다."
}
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FOOD_NOT_FOUND` | 404 | 존재하지 않는 물품 |

---

### 3-5. 물품 수정
| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URL | `/foods/{foodId}` |
| 인증 | 필요 (등록자 본인) |
| Content-Type | `multipart/form-data` |
| 설명 | 이름, 정원 수, 상세내용 및 일반 사진을 수정한다. |

**제약**
- 정원 수는 현재 승인 인원(`approvedCount`)보다 작게 설정 불가
- 소비기한 사진을 교체하려면 `POST /foods/expired-date` 로 재인식 후 새 `expiredImageId`를 전달한다. (소비기한 값은 인식 결과로 고정)

**Form Data**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| request | application/json (part) | O | 아래 JSON 본문 |
| images | List\<MultipartFile\> | X | 새로 추가할 일반 사진(다중). `image_type = BASIC` |

**request part (JSON)**
```json
{
  "foodName": "수정된 이름",
  "capacity": 5,
  "details": "수정 내용",
  "expiredImageId": 30,
  "deleteImageIds": [10, 11]
}
```

> `expiredImageId`: 소비기한 사진을 교체할 경우에만 전달 (선택). 전달 시 해당 인식값으로 소비기한이 갱신된다.
> `deleteImageIds`: 삭제할 기존 이미지 ID 목록 (선택)

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `CAPACITY_TOO_SMALL` | 400 | 정원 < 현재 승인 인원 |
| `FORBIDDEN_FOOD_ACCESS` | 403 | 등록자 본인 아님 |
| `EXPIRED_IMAGE_NOT_FOUND` | 404 | 유효하지 않은 `expiredImageId` |

---

### 3-6. 물품 삭제
| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| URL | `/foods/{foodId}` |
| 인증 | 필요 (등록자 본인) |
| 설명 | soft delete 처리하며 상태를 `INCOMPLETE`로 변경한다. 상대방의 완료 거래 정보는 유지된다. |

**Response (200)**
```json
{ "code": "SUCCESS", "data": null, "message": "물품이 삭제되었습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FORBIDDEN_FOOD_ACCESS` | 403 | 등록자 본인 아님 |

---

### 3-7. 내 등록 물품 목록
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/members/me/foods` |
| 인증 | 필요 |
| 설명 | 로그인 회원이 등록한 물품 목록을 조회한다. |

---

## 4. 나눔 요청(REQUEST_FOOD) API

### 4-1. 나눔 요청 생성
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/foods/{foodId}/requests` |
| 인증 | 필요 |
| 설명 | 특정 물품에 나눔을 요청한다. 초기 상태 `REQUEST`. |

**제약**
- 동일 회원이 같은 물품에 중복 요청 불가
- 본인 물품에는 요청 불가
- `IN_PROGRESS` 상태 물품만 요청 가능

**Response (201)**
```json
{ "code": "SUCCESS", "data": { "requestFoodId": 500, "status": "REQUEST" }, "message": "나눔 요청을 보냈습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `REQUEST_DUPLICATED` | 409 | 이미 요청한 물품 |
| `SELF_REQUEST_NOT_ALLOWED` | 400 | 본인 물품 요청 |
| `FOOD_NOT_AVAILABLE` | 409 | 요청 불가 상태(완료/만료 등) |

---

### 4-2. 받은 요청 목록 (등록자용)
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/foods/{foodId}/requests` |
| 인증 | 필요 (등록자 본인) |
| 설명 | 해당 물품에 들어온 요청 목록을 조회한다. |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": [
    { "requestFoodId": 500, "requesterNickName": "신청자A", "status": "REQUEST" }
  ],
  "message": "조회에 성공했습니다."
}
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FORBIDDEN_FOOD_ACCESS` | 403 | 등록자 본인 아님 |

---

### 4-3. 요청 수락
| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URL | `/requests/{requestFoodId}/approve` |
| 인증 | 필요 (등록자 본인) |
| 설명 | 요청을 수락한다. 승인 인원이 1 증가하며, 정원과 같아지면 물품 상태가 `COMPLETED`로 변경된다. |

**Response (200)**
```json
{
  "code": "SUCCESS",
  "data": { "requestFoodId": 500, "status": "APPROVED", "foodStatusTx": "IN_PROGRESS" },
  "message": "요청을 수락했습니다."
}
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `CAPACITY_FULL` | 409 | 정원이 이미 가득 참 |
| `INVALID_REQUEST_STATUS` | 409 | 이미 처리된 요청 |

---

### 4-4. 요청 거절
| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| URL | `/requests/{requestFoodId}/reject` |
| 인증 | 필요 (등록자 본인) |
| 설명 | 요청을 거절한다. 상태를 `REJECTED`로 변경한다. |

**Response (200)**
```json
{ "code": "SUCCESS", "data": { "requestFoodId": 500, "status": "REJECTED" }, "message": "요청을 거절했습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `INVALID_REQUEST_STATUS` | 409 | 이미 처리된 요청 |

---

### 4-5. 내가 보낸 요청 목록
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/members/me/requests` |
| 인증 | 필요 |
| 설명 | 로그인 회원이 보낸 나눔 요청 목록을 조회한다. |

---

## 5. 이미지(IMAGE) API

> 물품 등록/수정 시 일반 사진은 `multipart/form-data`로 함께 업로드된다(3-2, 3-5 참고).
> 소비기한 사진은 인식 API(3-1)에서 업로드·저장된다.
> 아래는 개별 이미지 삭제 API이다.

### 5-1. 이미지 삭제
| 항목 | 내용 |
|------|------|
| Method | `DELETE` |
| URL | `/images/{imageId}` |
| 인증 | 필요 (소유자 본인) |
| 설명 | 이미지를 soft delete 처리한다. |

**Response (200)**
```json
{ "code": "SUCCESS", "data": null, "message": "이미지가 삭제되었습니다." }
```

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FORBIDDEN_IMAGE_ACCESS` | 403 | 소유자 본인 아님 |
| `IMAGE_NOT_FOUND` | 404 | 존재하지 않는 이미지 |

---

## 6. 채팅(CHAT) API

> 음식 물품 상세에서 **등록자(owner) ↔ 요청자(requester) 1:1 채팅방**을 생성한다.
> 방 관리·이력 조회는 **REST**, 실시간 송수신은 **WebSocket(STOMP) + Redis Pub/Sub 팬아웃 중계**로 처리한다.
> 엔티티는 향후 그룹 채팅(한 방 N명) 확장을 위해 `ChatRoom` + `ChattingMember`(조인) + `ChatMessage` 3개로 구성한다.
> 메시지 정본은 항상 DB이며, 실시간 전달은 그 위의 best-effort다. (전체 아키텍처·인프라 흐름은 `.claude/docs/chat/docs.md` 참고)

### 6-1. 채팅방 생성/조회
| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/chat/rooms` |
| 인증 | 필요 |
| 설명 | 물품에 대한 1:1 채팅방을 생성한다. `(foodId, 로그인 회원)` 방이 이미 있으면 기존 방을 반환한다. |

**제약**
- 본인 물품에는 채팅 불가
- 새 방 생성은 물품이 `IN_PROGRESS`일 때만 허용 (이미 존재하는 방 반환은 상태 무관)
- 기존 1:1 방 탐색 = `foodId` 방 중 로그인 회원이 `ChattingMember`로 참여한 방
- 신규 생성 시 `ChatRoom`(ownerId=물품 등록자) + `ChattingMember` 2행(등록자 `OWNER` / 로그인 회원 `MEMBER`) 생성

**Request Body**
```json
{ "foodId": 100 }
```

**Response (신규 생성 201 / 기존 방 반환 200)**
```json
{ "code": "SUCCESS", "data": { "roomId": 700, "foodId": 100, "created": true }, "message": "채팅방이 생성되었습니다." }
```

> `created`: 이번 요청으로 새로 만들어졌으면 `true`(201), 기존 방을 반환했으면 `false`(200).

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `FOOD_NOT_FOUND` | 404 | 존재하지 않는 물품 |
| `SELF_CHAT_NOT_ALLOWED` | 400 | 본인 물품에는 채팅 불가 |
| `FOOD_NOT_AVAILABLE` | 409 | 새 방 생성 불가 상태(완료/만료/삭제) |

---

### 6-2. 내 채팅방 목록
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

> `unreadCount` = 내 `ChattingMember.unreadCount`(비정규화 카운터). 메시지 수신 시 수신자 카운터 +1(6-4), 읽음 시 0으로 리셋(6-3)되어, 목록 조회는 저장된 값을 읽기만 한다(매번 세지 않음).
> `lastReadMessageId`는 방 진입 시 이동 지점·향후 읽음표시(read receipt)·정합성 복구용 truth로 함께 유지한다.

---

### 6-3. 채팅 이력 조회
| 항목 | 내용 |
|------|------|
| Method | `GET` |
| URL | `/chat/rooms/{roomId}/messages?direction={direction}&cursor={cursor}&size={size}` |
| 인증 | 필요 (방 참여자 본인) |
| 설명 | 채팅방 메시지를 **양방향 커서**로 무한 스크롤 조회한다. 방 진입 시(`initial`)에는 마지막으로 읽은 메시지를 기준으로 위(과거)·아래(최신)를 함께 보여주고, 이후 방향에 따라 더 불러온다. |

**Query Params**
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| direction | string | X | `initial`(기본) / `before`(위로, 과거) / `after`(아래로, 최신) |
| cursor | long | △ | `before`/`after`일 때 필수. 가진 메시지 중 기준 id. `before`면 `messageId < cursor`, `after`면 `messageId > cursor`. `initial`이면 무시(서버가 `lastReadMessageId`를 기준으로 사용) |
| size | int | X | 페이지 크기. 기본 20 |

**direction별 동작**
- `initial` (방 진입): 내 `lastReadMessageId`를 기준으로 **위쪽(`messageId <= lastReadMessageId`)에서 최신 `size`개 + 아래쪽(`messageId > lastReadMessageId`)에서 오래된 `size`개**를 가져와 합친다(최대 `2*size`개). 즉 마지막 읽은 지점의 위아래 맥락을 함께 보여준다.
  - 신규 입장(`lastReadMessageId = 0`)이면 위쪽이 비어 자연히 처음부터, 이미 다 읽었으면(`lastReadMessageId =` 최신) 아래쪽이 비어 자연히 맨 밑이 된다(별도 분기 불필요).
  - `anchorMessageId`에 진입 기준값(직전 `lastReadMessageId`)을 담아, 프론트가 그 아래에 "새 메시지" 구분선을 그리고 해당 지점으로 스크롤하도록 한다.
- `before` (위로 스크롤): `messageId < cursor` 인 과거 메시지를 `size`개 가져온다.
- `after` (아래로 스크롤): `messageId > cursor` 인 최신 메시지를 `size`개 가져온다.

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

> - `messages`: direction과 무관하게 항상 `messageId` 내림차순(최신 → 과거)으로 반환한다.
> - `anchorMessageId`: `initial` 조회에서만 채워지는 진입 기준값(직전 `lastReadMessageId`). `before`/`after`에서는 `null`.
> - `upCursor`: 묶음 중 **가장 오래된** messageId → 위(과거)로 더 부를 때 `before` 커서로 사용. 묶음이 비면 `null`.
> - `downCursor`: 묶음 중 **가장 최신** messageId → 아래(최신)로 더 부를 때 `after` 커서로 사용. 묶음이 비면 `null`.
> - `hasPrev`: `upCursor`보다 과거 메시지가 더 있으면 `true`. `hasNext`: `downCursor`보다 최신 메시지가 더 있으면 `true`.
> - **읽음 갱신(부수효과)**: `initial` 조회 시에만 내 `ChattingMember.lastReadMessageId`를 방의 **최신 messageId**로 업데이트하고 `unreadCount`를 `0`으로 리셋한다. `before`/`after` 스크롤은 읽음 상태를 변경하지 않는다.

**Error** (실패 시 ProblemDetail)
| code | 상태 | 설명 |
|------|------|------|
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방 |
| `FORBIDDEN_CHAT_ACCESS` | 403 | 방 참여자 본인 아님 |

---

### 6-4. 실시간 메시지 송수신 (WebSocket / STOMP)

> 분산(다중 서버) 환경에서 **Redis Pub/Sub 단일 채널 팬아웃**으로 상대방에게 전달한다. 발행 메시지를 모든 인스턴스에 전파하고, 수신자의 WS 세션을 **로컬에 가진 인스턴스만** Spring 메시지 브로커로 push한다(나머지는 자동 no-op).
> 발행 메시지는 **항상 DB에 저장(정본)** 되며, 실시간 못 받아도 수신자는 이력 조회(6-3)로 볼 수 있다.

| 구분 | 경로 | 설명 |
|------|------|------|
| 연결(Handshake) | `GET /ws` | STOMP WebSocket 연결. **로그인 직후 전역 1회**(방 진입과 무관). 세션 쿠키 동봉 필요(미인증 401). 핸드셰이크에서 Principal=memberId 설정 |
| 구독(SUBSCRIBE) | `/user/queue/messages` | **연결 직후 필수.** 내게 오는 모든 메시지/알림 수신(방 밖에서도). 룸 토픽 구독은 두지 않음 |
| 발행(SEND) | `/pub/chat/rooms/{roomId}` | 메시지 전송. 본문 `{ "content": "..." }` |

**서버 처리 흐름 (발행 시)**
1. 세션에서 `senderId` 확인 → 방 참여자 검증
2. `ChatMessage` **DB 저장(정본)** → `messageId` 채번 → 발신자 ACK. 동시에 **수신자(발신자 제외) `ChattingMember.unreadCount += 1`** (안읽음 수 비정규화 갱신)
3. 발행 인스턴스가 `(payload, recipientIds)`를 **Redis 채널(`chat.messages`)에 publish**
4. **모든 인스턴스**가 그 채널을 구독 중 → 메시지 수신
5. 각 인스턴스가 `recipientIds`에 대해 `convertAndSendToUser(수신자, "/queue/messages", payload)` 호출 → 수신자 WS 세션을 **로컬에 가진 인스턴스만** 실제 전달, 나머지는 자동 no-op

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

> 기존 방의 메시지 전송은 물품 상태와 무관하게 허용된다(완료 후 수령 장소·시간 조율용).
> 오프라인 회원 **푸시 알림(FCM/APNs)** 연동은 2차(향후) 범위.

---

## 부록 A. 예외 처리 (GlobalExceptionHandler)

실패 응답은 `@RestControllerAdvice` 의 `GlobalExceptionHandler` 에서 `ProblemDetail` 로 변환된다.

| 예외 | `title` | `status` | `detail` |
|------|---------|----------|----------|
| `BusinessException` | `errorCode.name()` | `errorCode.getStatus()` | `errorCode.getMessage()` |
| `MethodArgumentNotValidException` | `VALIDATION_FAILED` | `400` | 위반 필드 메시지를 `, ` 로 연결한 문자열 |

**예시 — 비즈니스 예외**
```json
{
  "type": "about:blank",
  "title": "REQUEST_DUPLICATED",
  "status": 409,
  "detail": "이미 요청한 물품입니다.",
  "instance": "/api/v1/foods/100/requests"
}
```

**예시 — 검증 실패**
```json
{
  "type": "about:blank",
  "title": "VALIDATION_FAILED",
  "status": 400,
  "detail": "비밀번호는 8~20자, 영문 대소문자와 특수문자를 포함해야 합니다",
  "instance": "/api/v1/members"
}
```

> 참고: 파일 업로드 용량 초과(`MaxUploadSizeExceededException`), 잘못된 JSON 본문(`HttpMessageNotReadableException`),
> 쿼리·경로 파라미터 검증(`@Validated` + `HandlerMethodValidationException`), 그리고 예상치 못한 `Exception`(500)에 대한
> 핸들러는 별도로 추가해야 위 표의 `FILE_TOO_LARGE` 등이 일관된 `ProblemDetail`로 응답된다.

---

## 부록 B. 물품 상태(status_tx) 전이

| 상태 | 설명 | 전이 조건 |
|------|------|-----------|
| `IN_PROGRESS` | 진행 중 | 물품 등록 시 초기 상태 |
| `COMPLETED` | 완료 | 승인 인원 == 정원 |
| `INCOMPLETE` | 미완료 | 물품 삭제(soft delete) 시 |
| `EXPIRED` | 만료 | 소비기한 하루 전 배치로 변경 |

## 부록 C. 요청 상태(REQUEST_FOOD.status) 전이

| 상태 | 설명 | 전이 조건 |
|------|------|-----------|
| `REQUEST` | 요청됨 | 요청 생성 시 초기 상태 |
| `APPROVED` | 수락됨 | 등록자가 수락 |
| `REJECTED` | 거절됨 | 등록자가 거절 |