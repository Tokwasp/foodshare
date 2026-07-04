---
name: repository-test
description: |
  `src/test/java` 하위에서 Repository 테스트 코드를 작성하거나 수정할 때 적용된다.
  Repository 테스트는 클래스 이름에 `Repository`가 포함된 테스트로 식별한다
  (예: `ProductRepositoryTest`, `ProductRepositoryImplTest`).

  순수 Java 가독성(readable-java)보다 본 스킬이 우선한다.
---

# Repository 테스트 작성 규칙

이 스킬은 Spring Data JPA / QueryDSL 기반 Repository를 검증하는 테스트 코드를
작성할 때 따라야 하는 규칙을 정의한다.

구체적인 코드 예시는 같은 디렉토리의 `reference.md`를 참조한다.

---

## 1. 테스트 대상 선정 — "무엇을 테스트할 것인가"

### 1-1. 테스트해야 하는 것

- **직접 작성한 쿼리 메서드**
  - `findAllBySellingStatusIn`, `findAllByProductNumberIn` 같은 **Spring Data JPA의 메서드 네이밍 쿼리**
  - `@Query`로 작성한 JPQL / native query
  - QueryDSL로 작성한 Custom Repository 메서드
- **정렬 / 페이징 / 그룹핑 등 쿼리의 의도가 결과에 영향을 주는 경우**
- **DB 제약조건이 작동하는지 검증해야 하는 케이스** (unique, null 제약 등)

### 1-2. 테스트하지 말아야 하는 것

- `save`, `findById`, `findAll`, `deleteById` 등 **Spring Data JPA가 기본 제공하는 메서드**.
  → 이것은 프레임워크를 테스트하는 것이며, 의미 없는 테스트다.
- **비즈니스 로직** — Repository 테스트의 책임이 아니다. Service 테스트에서 다룬다.

---

## 2. 테스트 클래스의 기본 구조

### 2-1. `IntegrationTestSupport`를 상속한다

Repository 테스트는 `@DataJpaTest`가 아닌 **`@SpringBootTest`를 사용한다**.
이유는 다음과 같다:

- `@DataJpaTest`는 JPA 관련 빈만 띄우므로 컨텍스트가 두 종류(`@DataJpaTest`용 +
  `@SpringBootTest`용)로 분리되어 **테스트 전체 실행 시간이 늘어난다**.
- 프로젝트 전체에서 **하나의 통합 테스트용 상위 클래스**(`IntegrationTestSupport`)를
  공유하면, 스프링 컨텍스트가 1회만 로딩되어 전체 테스트가 빨라진다.

→ 코드 예시: reference.md §0 (`IntegrationTestSupport`), §1 (상속받은 테스트 클래스)

모든 통합 테스트(Repository 테스트 포함)는 `IntegrationTestSupport`를 상속하고,
하위 클래스에는 `@Autowired`로 검증 대상 Repository를 주입한다.

### 2-2. `@Transactional`을 클래스 레벨에 붙인다

- 각 테스트 메서드가 끝나면 트랜잭션이 롤백되어 **테스트 간 데이터 격리**가 보장된다.
- `@AfterEach`에서 `deleteAll()`로 정리하는 방식은 사용하지 않는다.
  (코드가 늘어나고 누락 위험이 있다.)
- 단, **트랜잭션 전파(Propagation)나 영속성 컨텍스트의 1차 캐시가 결과에 영향을
  주는 검증**이 필요할 때는 `@Transactional`을 빼고 수동으로 정리한다.
  (Repository 테스트에서는 거의 발생하지 않는 케이스다.)

### 2-3. `@ActiveProfiles("test")`

- `IntegrationTestSupport`에 이미 붙어 있으므로 하위 테스트에 중복 선언하지 않는다.
- test 프로파일은 보통 H2 인메모리 DB를 사용하도록 구성한다.

---

## 3. 테스트 메서드 작성 규칙

### 3-1. `@DisplayName`은 한국어 문장으로 작성한다

- "**무엇을 테스트하는지**"가 아니라 "**무엇이 보장되는지**"를 적는다.
- ✅ `"원하는 판매상태를 가진 상품들을 조회한다."`
- ✅ `"가장 마지막으로 저장한 상품의 상품번호를 읽어올 때, 상품이 하나도 없는 경우에는 null을 반환한다."`
- ❌ `"findAllBySellingStatusIn 테스트"` — 메서드명을 그대로 반복하는 것은 가치가 없다.
- ❌ `"상품을 조회한다."` — 너무 모호하다.

### 3-2. Given-When-Then 주석을 명시한다

세 절을 빈 줄과 주석으로 구분한다.

→ 코드 예시: reference.md §1 (기본 패턴)

- given이 없는 테스트(예: "데이터가 하나도 없을 때 null을 반환한다")는 `// given` 절을
  생략할 수 있다. 이때 `// when`부터 시작한다. (예: reference.md §4)

### 3-3. 메서드명은 영문으로, 검증 대상 메서드명을 따른다

- `findAllBySellingStatusIn`, `findLatestProductNumber` 처럼 **Repository 메서드명과
  동일하게** 짓는다.
- 예외 케이스를 검증할 때는 의도를 덧붙인다:
  `findLatestProductNumberWhenProductIsEmpty`
- 한국어 의도는 `@DisplayName`이 담당하므로 메서드명은 코드를 읽는 사람의
  검색성을 위해 영문을 유지한다.

---

## 4. 픽스처(테스트 데이터) 작성 규칙

### 4-1. 빌더보다 정적 팩토리 메서드를 우선한다

도메인 객체가 `static create(...)` 같은 정적 팩토리 메서드를 제공한다면 그것을 사용한다:

```java
Stock stock1 = Stock.create("001", 1);  // ✅
```

→ 정적 팩토리 메서드를 활용한 전체 예시: reference.md §5

제공하지 않는다면 테스트 클래스 내부에 `private` 헬퍼 메서드를 둔다.

→ 빌더 기반 헬퍼 메서드 예시: reference.md §1 (`createProduct` 헬퍼)

### 4-2. 헬퍼 메서드의 위치

- 테스트 클래스의 **맨 아래**에 모은다.
- 여러 테스트 클래스에서 공통으로 쓰는 픽스처는 별도의 `*TestFixture` 클래스로
  추출하지 않는다 — 픽스처는 각 테스트 클래스가 **국지적으로** 관리한다.
  (다른 곳에서 픽스처를 바꾸면 의도치 않게 테스트가 깨지는 결합도가 생긴다.)

### 4-3. 픽스처는 테스트 의도가 드러나도록 구성한다

- 검증하려는 조건(예: `SELLING` 상태)과 검증하지 않는 조건(`HOLD`, `STOP_SELLING`)을
  **둘 다 포함**시켜야 쿼리 필터링이 실제로 동작하는지 검증할 수 있다.
- 한 테스트에 3개 정도의 데이터를 두는 것이 가독성 면에서 적정선이다.

→ 매칭 데이터와 비매칭 데이터를 모두 포함한 픽스처 예시: reference.md §1

---

## 5. AssertJ 검증 규칙

### 5-1. 컬렉션 검증은 `hasSize` + `extracting` + `containsExactlyInAnyOrder` 패턴

```java
assertThat(products).hasSize(2)
        .extracting("productNumber", "name", "sellingStatus")
        .containsExactlyInAnyOrder(
                tuple("001", "아메리카노", SELLING),
                tuple("002", "카페라떼", HOLD)
        );
```

- `hasSize`로 **개수를 먼저** 확인한다 (의도하지 않은 데이터가 섞이지 않았음을 보장).
- `extracting`으로 **검증하려는 필드만** 뽑는다. 모든 필드를 비교하지 않는다.
- 순서가 보장되지 않는 쿼리에는 `containsExactlyInAnyOrder`를 쓴다.
- **순서가 의미 있는 쿼리**(`ORDER BY`가 있는 경우)에만 `containsExactly`를 쓴다.

### 5-2. 단일 값 검증

```java
assertThat(latestProductNumber).isEqualTo(targetProductNumber);
assertThat(latestProductNumber).isNull();
```

- `isEqualTo`로 비교할 기대값은 **변수에 담아서** 사용한다 (given 절의 값과
  연결되는 것이 명시적으로 보이도록).

### 5-3. 사용하지 말 것

- ❌ JUnit5의 `Assertions.assertEquals` — 일관성을 위해 AssertJ만 사용한다.
- ❌ Hamcrest matchers — 마찬가지로 사용하지 않는다.

---

## 6. 엣지 케이스 다루기

다음 경우들은 **반드시 별도 테스트로** 작성한다:

- **빈 결과** — 조건에 맞는 데이터가 하나도 없을 때 빈 리스트 / null이 반환되는지
  (`findLatestProductNumberWhenProductIsEmpty` 사례)
- **NULL 입력** — 메서드가 NULL을 받을 수 있다면 어떻게 동작하는지
- **경계값** — IN 절에 빈 리스트가 들어왔을 때, LIMIT이 적용되는 경계 등

엣지 케이스 테스트의 `@DisplayName`은 **"~할 때, ~한다"** 패턴을 사용한다:
> `"가장 마지막으로 저장한 상품의 상품번호를 읽어올 때, 상품이 하나도 없는 경우에는 null을 반환한다."`

→ 엣지 케이스 테스트 예시: reference.md §4

---

## 7. import 규칙

- AssertJ는 static import: `import static org.assertj.core.api.Assertions.*;`
- 도메인 enum도 static import로 가독성을 높인다:
  ```java
  import static sample.cafekiosk.spring.domain.product.ProductSellingStatus.*;
  import static sample.cafekiosk.spring.domain.product.ProductType.HANDMADE;
  ```
- 와일드카드 import는 enum 값처럼 **같은 타입의 여러 값을 한 번에 가져올 때만** 사용한다.

---

## 8. 체크리스트

Repository 테스트를 작성/수정한 뒤 아래를 확인한다:

- [ ] 테스트 대상이 Spring Data JPA 기본 메서드가 아닌, 직접 작성한 쿼리인가?
- [ ] `IntegrationTestSupport`를 상속했는가?
- [ ] `@Transactional`이 클래스에 붙어 있는가?
- [ ] `@DisplayName`이 한국어 문장으로 "무엇이 보장되는지"를 설명하는가?
- [ ] given / when / then 주석이 빈 줄과 함께 구분되어 있는가?
- [ ] 픽스처에 검증 대상과 비대상이 모두 포함되어 있는가?
- [ ] `hasSize` + `extracting` + `containsExactlyInAnyOrder` 패턴을 따랐는가?
- [ ] 빈 결과 / NULL 등 엣지 케이스가 별도 테스트로 분리되어 있는가?
- [ ] AssertJ만 사용하고 있는가? (JUnit assertEquals, Hamcrest 없음)
