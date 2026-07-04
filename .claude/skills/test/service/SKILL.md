---
name: service-test
description: |
  `src/test/java` 하위에서 Service 테스트 코드를 작성하거나 수정할 때 적용된다.
  Service 테스트는 클래스 이름에 `Service`가 포함된 테스트로 식별한다
  (예: `ProductServiceTest`, `OrderServiceTest`).

  순수 Java 가독성(readable-java)보다 본 스킬이 우선한다.
---

# Service 테스트 작성 규칙

이 스킬은 Spring `@Service` 빈을 검증하는 테스트 코드를 작성할 때 따라야 하는
규칙을 정의한다.

구체적인 코드 예시는 같은 디렉토리의 `reference.md`를 참조한다.

---

## 0. 가장 중요한 결정 — 통합 테스트 vs 단위 테스트

**Service 테스트는 두 가지 스타일 중 하나를 선택해야 한다.** 결정 기준을 먼저 본다.

### 0-1. 의사결정 트리

```
Service의 동작이 DB 상태에 의존하는가?
  (= Repository 호출 결과나 영속화된 데이터의 상태가 결과를 좌우하는가)
│
├─ YES → 통합 테스트 (extends IntegrationTestSupport)
│         예: ProductService.createProduct (최근 상품번호 기반 채번)
│         예: OrderService.createOrder (재고 차감, 주문 저장)
│         예: OrderStatisticsService.sendOrderStatisticsMail (주문 조회 후 메일)
│
└─ NO  → 단위 테스트 (@ExtendWith(MockitoExtension.class) + @Mock + @InjectMocks)
          - 의존성을 전부 Mock으로 두어도 검증 가치가 남아 있는가?
          - 즉, "어떤 메서드가 호출되었는가"가 곧 비즈니스 로직 검증인 경우
          예: MailService.sendMail
              (외부 시스템 호출 + 이력 저장 — 흐름 자체가 검증 대상)
```

### 0-2. 기본은 통합 테스트다

- **고민되면 통합 테스트**를 선택한다.
- 단위 테스트로 모든 의존성을 모킹하면 "구현을 테스트"하게 되어 리팩토링에 취약해진다.
- 통합 테스트가 느린 것이 문제가 되는 단계는 아직 아닐 가능성이 높다.
- 단위 테스트가 적합한 경우는 **"Mock으로 다 채워도 검증 가치가 살아 있는 경우"**로,
  주로 외부 시스템 어댑터(Mail, SMS, 결제 게이트웨이) 같은 얇은 서비스다.

---

## 1. 통합 테스트 스타일

### 1-1. 기본 구조

테스트 클래스는 `IntegrationTestSupport`를 상속하고, `@Autowired`로 검증 대상
Service와 검증에 필요한 Repository를 주입한다. 테스트 간 격리는 베이스
(`IntegrationTestSupport`)에 붙은 `@Transactional` 롤백으로 자동 보장된다.

→ 코드 예시: reference.md §1

### 1-2. `IntegrationTestSupport`를 상속한다

- Repository 테스트와 **동일한 상위 클래스**를 사용한다.
- 스프링 컨텍스트가 1회만 로딩되도록 모든 통합 테스트가 같은 부모를 공유하는 것이
  핵심이다. (`@SpringBootTest`를 각 테스트에 따로 붙이면 컨텍스트가 여러 번 뜬다.)

### 1-3. 격리는 베이스의 `@Transactional`이 담당한다 (Repository 테스트와 동일)

- `@Transactional`은 **`IntegrationTestSupport`(베이스)에 한 번** 붙어 있고, 상속하는
  모든 통합 테스트에 적용된다. 각 테스트 메서드가 끝날 때 트랜잭션이 롤백되어
  **테스트 간 데이터 격리**가 보장된다. 개별 테스트에 따로 붙이지 않는다.
- `@AfterEach` 수동 정리보다 누락 위험이 없다.

> ⚠️ **1차 캐시 거짓 통과 주의.** 저장한 엔티티를 **같은 트랜잭션에서 재조회**해 DB
> 상태를 검증하면, 영속성 컨텍스트 1차 캐시가 응답하므로 실제로는 영속화되지 않는
> 매핑/저장 버그를 **못 잡는다**. DB 상태를 재조회로 검증하는 테스트에서는:
> - `entityManager.flush()` + `clear()`로 1차 캐시를 비운 뒤 재조회하거나,
> - **그 테스트 클래스만 `@Transactional(propagation = NOT_SUPPORTED)`로 override**해
>   롤백 격리를 끄고 `@AfterEach`로 수동 정리한다(§1-4).
>
> Repository 스킬 §2-2의 탈출구("전파·1차 캐시가 결과에 영향을 주면 `@Transactional`을
> 빼고 수동 정리")와 동일한 원칙이다.

### 1-4. 롤백 격리를 끈 테스트만 `@AfterEach`로 수동 정리한다

§1-3의 함정 때문에 **`@Transactional(propagation = NOT_SUPPORTED)`로 롤백을 끈 경우**,
격리를 위해 `@AfterEach`로 직접 정리한다.

→ 코드 예시: reference.md §2 (여러 Repository를 정리하는 케이스)

**규칙:**

- **`deleteAllInBatch()`를 사용**한다. `deleteAll()`이 아니다.
  - `deleteAll()`은 엔티티를 1건씩 조회 후 삭제 → N+1 쿼리 발생, 느림
  - `deleteAllInBatch()`는 단일 `DELETE` 쿼리 → 빠름, 테스트 격리에 충분
- **삭제 순서를 외래키 참조 관계에 맞게** 작성한다. 자식 테이블 → 부모 테이블 순.
  - 예: `OrderProduct`(자식) → `Order`, `Product` → 독립적인 `Stock`

### 1-5. 외부 의존성은 `IntegrationTestSupport`의 `@MockBean`으로 격리

- 메일 발송, SMS, 결제 등 **외부 시스템 호출**은 절대 실제로 발생시키지 않는다.
- `IntegrationTestSupport`에 `@MockBean`으로 한 번만 선언하면 모든 통합 테스트가
  공유한다.

→ 코드 예시: reference.md §0 (`IntegrationTestSupport`의 `@MockBean` 선언),
§3 (테스트 메서드에서 stubbing하는 방법)

---

## 2. 단위 테스트 스타일

### 2-1. 기본 구조

`@ExtendWith(MockitoExtension.class)`로 Mockito를 활성화하고, 모든 의존성을
`@Mock`으로 만들어 `@InjectMocks` 대상에 주입한다.

→ 코드 예시: reference.md §4 (`MailServiceTest`)

### 2-2. 스프링 컨텍스트를 띄우지 않는다

- `@SpringBootTest` 없음, `extends IntegrationTestSupport` 없음.
- `@ExtendWith(MockitoExtension.class)`로 Mockito만 활성화한다.
- **빠른 피드백**이 가장 큰 장점이다.

### 2-3. 모든 의존성은 `@Mock`, 테스트 대상은 `@InjectMocks`

- `@Mock`으로 만든 Mock들이 `@InjectMocks` 대상의 생성자/필드에 자동 주입된다.
- 생성자가 `@RequiredArgsConstructor` 등으로 만들어져 있어야 매끄럽게 주입된다.

---

## 3. Mockito stubbing 규칙 (두 스타일 공통)

### 3-1. BDD 스타일을 우선한다

```java
// ✅ 권장
given(mailSendClient.sendEmail(anyString(), anyString(), anyString(), anyString()))
    .willReturn(true);

// ❌ 비권장 (작동은 하지만 BDD 스타일이 더 가독성이 좋다)
when(mailSendClient.sendEmail(anyString(), anyString(), anyString(), anyString()))
    .thenReturn(true);
```

- `given().willReturn()` 패턴은 `// given` 주석과 의미가 맞아 **Given-When-Then 구조와
  자연스럽게 어울린다**.
- import: `import static org.mockito.BDDMockito.given;`
- **예외:** 통합 테스트에서 `@MockBean`을 stubbing할 때는 `when().thenReturn()`을
  쓰는 경우가 있다. 일관성 차원에서 새 코드를 작성할 때는 `given().willReturn()`을
  쓴다.

### 3-2. stubbing 위치는 given 절 안에 둔다

- `// given` 절 안에 stubbing이 들어간다.
- stubbing이 길어지면 `// stubbing` 같은 별도 주석을 붙여 구분해도 좋다.

### 3-3. 호출 검증은 `verify`로

```java
verify(mailSendHistoryRepository, times(1)).save(any(MailSendHistory.class));
```

- **상태 검증이 가능하면 상태 검증을 우선**한다 (`assertThat`).
- `verify`는 "호출 자체가 의미 있는 검증"일 때만 사용한다. (예: 메일 이력 저장)
- 호출 횟수를 검증하지 않는다면 `verify`를 굳이 쓰지 않는다.

---

## 4. 테스트 메서드 작성 규칙

### 4-1. `@DisplayName`은 한국어 문장으로 "보장되는 것"을 적는다

Repository 스킬과 동일한 원칙이지만, Service 테스트는 **비즈니스 의도**를 담아야 한다:

- ✅ `"신규 상품을 등록한다. 상품번호는 가장 최근 상품의 상품번호에서 1 증가한 값이다."`
- ✅ `"재고가 부족한 상품으로 주문을 생성하려는 경우 예외가 발생한다."`
- ✅ `"결제완료 주문들을 조회하여 매출 통계 메일을 전송한다."`
- ❌ `"createOrder 테스트"` — 메서드명 반복은 가치 없음
- ❌ `"주문을 생성한다."` — 어떤 조건의 주문 생성인지 불명확

### 4-2. Given-When-Then 주석을 명시한다

세 절을 빈 줄과 주석으로 구분한다.

→ 코드 예시: reference.md §1 (정상 케이스), §2 (예외 케이스)

- **예외 검증**은 when과 then이 한 줄에 묶이므로 `// when // then` 으로 표기한다
  (3번 controller-test의 MockMvc 패턴과 동일한 의도).

### 4-3. 메서드명

- 검증 대상 Service 메서드명을 따른다: `createOrder`, `createProduct`, `sendOrderStatisticsMail`
- 변형 케이스는 의도를 덧붙인다:
  - `createOrderWithStock` (재고 차감이 발생하는 케이스)
  - `createOrderWithNoStock` (재고 부족 예외)
  - `createOrderWithDuplicateProductNumbers` (중복 입력)
  - `createProductWhenProductsIsEmpty` (빈 상태 분기)

---

## 5. 픽스처 작성 규칙

### 5-1. Service 테스트는 종종 여러 픽스처가 필요하다

Service는 여러 도메인을 협력시키므로 픽스처가 늘어난다. 그래도 원칙은 동일:

- 헬퍼 메서드는 **테스트 클래스 맨 아래**에 모은다.
- 정적 팩토리 메서드가 있으면 그것을 우선 사용 (`Stock.create(...)`).
- **테스트 클래스 간 공유 픽스처 클래스(`TestFixture`)는 만들지 않는다.** 픽스처는
  국지적으로 관리한다.

### 5-2. 헬퍼 메서드는 **테스트의 의도에 맞춰** 시그니처를 좁힌다

`Product`는 5개 필드가 있지만, 테스트의 관심이 type/productNumber/price뿐이라면
세 개만 파라미터로 받고 나머지는 헬퍼 내부에 고정값으로 숨긴다.

→ 코드 예시: reference.md §1 (`createProduct` 헬퍼 메서드)

- 검증과 무관한 필드(`sellingStatus`, `name`)는 헬퍼 내부에 고정값으로 숨긴다.
- 호출부 가독성이 크게 올라간다: `createProduct(HANDMADE, "001", 1000)`

### 5-3. 시간이 결과에 영향을 주면 외부에서 주입한다

```java
LocalDateTime registeredDateTime = LocalDateTime.now();
// ...
OrderResponse orderResponse = orderService.createOrder(request, registeredDateTime);
```

- Service가 `LocalDateTime.now()`를 내부에서 호출하면 테스트가 불안정해진다.
- **시간은 파라미터로 받도록 설계**하고, 테스트에서 명시적으로 주입한다.
- 경계값 테스트(예: 통계 메일 — 자정 직전/직후)는 `LocalDateTime.of(2023, 3, 5, 0, 0)`처럼
  **고정된 시간**을 사용한다.

---

## 6. AssertJ 검증 규칙

### 6-1. Service 테스트는 응답 + DB 상태를 둘 다 검증한다

Service는 부수효과(DB 변경)를 만드는 경우가 많다. **응답 객체만 검증하지 말고,
DB 상태도 확인**한다.

→ 코드 예시: reference.md §1 (응답과 DB 상태를 모두 검증하는 전형적인 케이스)

검증 순서:
1. 응답 객체 검증 (`assertThat(response).extracting(...).contains(...)`)
2. Repository로 DB 상태 조회 후 컬렉션 검증 (`hasSize` + `extracting` + `containsExactlyInAnyOrder`)

### 6-2. 컬렉션 검증은 `hasSize` + `extracting` + `containsExactlyInAnyOrder` 패턴

Repository 스킬과 동일하다.

### 6-3. 단일 객체 검증은 `extracting(...).contains(...)`

```java
assertThat(orderResponse)
        .extracting("registeredDateTime", "totalPrice")
        .contains(registeredDateTime, 4000);
```

- 객체 하나의 여러 필드를 한 번에 검증할 때 유용하다.

### 6-4. 예외 검증은 `assertThatThrownBy`

```java
assertThatThrownBy(() -> orderService.createOrder(request, registeredDateTime))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("재고가 부족한 상품이 있습니다.");
```

- **예외 타입과 메시지를 둘 다** 검증한다.
- 메시지는 하드코딩보다는, 메시지가 변경 가능성이 큰 경우 상수로 추출하는 것을 고려한다.

---

## 7. 엣지 케이스와 분기 커버리지

Service는 비즈니스 분기가 많으므로 **분기마다 테스트**를 작성한다. 예시:

| Service 메서드 | 정상 케이스 | 엣지 케이스들 |
|---|---|---|
| `createProduct` | 기존 상품이 있을 때 채번 | 상품이 하나도 없을 때 `"001"` 부여 |
| `createOrder` | 일반 주문 생성 | 중복 상품번호 / 재고 차감 / 재고 부족 예외 |
| `sendOrderStatisticsMail` | 결제완료 주문 합산 | 시간 경계 (전날 23:59:59, 당일 00:00, 당일 23:59:59, 다음날 00:00) |

`sendOrderStatisticsMail` 예시처럼 **경계값(시간, 수량 등)을 의도적으로 픽스처에
포함**시켜 쿼리/로직의 경계 처리가 올바른지 검증한다.

---

## 8. DTO 분리 — Controller Request vs Service Request

Controller의 Request DTO와 Service의 Request DTO를 분리한다:

- `ProductCreateRequest` — Controller에서 받는 HTTP 요청
- `ProductCreateServiceRequest` — Service가 받는 입력

**Service 테스트에서는 `*ServiceRequest`를 사용**한다. Controller의 Request DTO를
Service 테스트에서 직접 사용하지 않는다 (계층 결합도 증가).

→ 코드 예시: reference.md §1 (`ProductCreateServiceRequest` 사용)

---

## 9. import 규칙

- AssertJ: `import static org.assertj.core.api.Assertions.*;`
- Mockito BDD: `import static org.mockito.BDDMockito.given;`
- Mockito argument matchers: `import static org.mockito.ArgumentMatchers.any;` /
  `anyString` 등
- Mockito verify: `import static org.mockito.Mockito.*;`
- 도메인 enum: `import static ...ProductSellingStatus.*;` 식으로 static import

---

## 10. 체크리스트

Service 테스트를 작성/수정한 뒤 아래를 확인한다:

### 공통

- [ ] 통합 vs 단위 테스트 결정이 0-1 의사결정 트리에 맞는가?
- [ ] `@DisplayName`이 한국어 문장으로 비즈니스 의도를 설명하는가?
- [ ] given / when / then 주석이 빈 줄과 함께 구분되어 있는가?
- [ ] 정상 케이스 외에 분기/엣지 케이스가 별도 테스트로 분리되어 있는가?
- [ ] 예외 검증은 `assertThatThrownBy`로 타입과 메시지를 둘 다 검증하는가?
- [ ] 헬퍼 메서드가 테스트 클래스 맨 아래에 있는가?
- [ ] Service 테스트에서 `ServiceRequest`(또는 도메인 DTO)를 사용하고 있는가?

### 통합 테스트인 경우

- [ ] `IntegrationTestSupport`를 상속하는가? (격리는 베이스의 `@Transactional`이 담당)
- [ ] 저장→재조회로 DB 상태를 검증한다면, 1차 캐시 거짓 통과를 막았는가?
      (`flush()`+`clear()` 또는 `@Transactional(propagation = NOT_SUPPORTED)` + `@AfterEach`)
- [ ] 롤백을 끈 테스트만 `@AfterEach`에서 `deleteAllInBatch()`로 정리하는가?
      (삭제 순서는 자식→부모 외래키 순)
- [ ] 외부 시스템 의존성이 `@MockBean`으로 격리되어 있는가?
- [ ] 응답뿐 아니라 DB 상태도 검증하는가?

### 단위 테스트인 경우

- [ ] `@ExtendWith(MockitoExtension.class)`를 사용하는가?
- [ ] 모든 의존성이 `@Mock`이고, 테스트 대상이 `@InjectMocks`인가?
- [ ] stubbing이 BDD 스타일(`given().willReturn()`)인가?
- [ ] Mock으로 다 채워도 검증 가치가 남아 있는 경우가 맞는가? (아니라면 통합으로 재고)
