# OOP Java — Reference 예시

SKILL.md의 각 규칙에 대응하는 Before(나쁜 코드) → After(좋은 코드) 예시와 그 이유를 정리한다.
SKILL.md의 섹션 번호와 동일한 번호 체계를 사용한다.

순수 가독성 관련 예시(이름, 메서드 길이, early return 등)는 `readable-java` 스킬의 reference.md를 참고한다.

---

## 1. SRP — 책임 분리

### Before — 한 클래스에 모든 게 들어있는 거대 main 메서드
```java
public class MinesweeperGame {
    private static String[][] board = new String[8][10];
    private static Integer[][] landMineCounts = new Integer[8][10];
    private static boolean[][] landMines = new boolean[8][10];

    public static void main(String[] args) {
        // 1. 게임 안내 출력
        // 2. 보드 초기화
        // 3. 지뢰 배치
        // 4. 주변 지뢰 수 계산
        // 5. 입력 받기
        // 6. 좌표 파싱
        // 7. 셀 오픈 또는 깃발
        // 8. 승패 판단
        // ... 200줄
    }
}
```

### After — 책임별로 분리
```java
// 게임의 흐름만 담당
public class Minesweeper implements GameInitializable, GameRunnable { ... }

// 보드 상태와 셀 조작 책임
public class GameBoard { ... }

// 입출력 책임
public class ConsoleInputHandler implements InputHandler { ... }
public class ConsoleOutputHandler implements OutputHandler { ... }

// 셀 자신의 상태와 행위
public interface Cell { ... }
public class EmptyCell implements Cell { ... }
public class LandMineCell implements Cell { ... }
public class NumberCell implements Cell { ... }
```

### 왜
원본은 변경 이유가 너무 많다: UI 바꾸려면 main 손대고, 보드 크기 바꾸려면 main 손대고, 셀 동작 바꾸려면 main 손댄다.
각 클래스가 한 가지 변경 이유만 가지면, 수정 영향 범위가 명확해진다.

판단법: 클래스 역할을 한 문장으로 말할 때 "그리고"가 들어가면 분리 신호다.
"이 클래스는 보드를 관리하고 입력을 받고 승패를 판단한다" → 셋으로 나눠야 함.

---

## 2. Tell, Don't Ask

### Before — 객체에서 데이터를 꺼내 외부에서 판단
```java
// 호출자가 객체 내부를 알고 비교 로직을 갖는다
List<StudyCafeLockerPass> lockerPasses = studyCafeFileHandler.readLockerPasses();
StudyCafeLockerPass lockerPass = lockerPasses.stream()
    .filter(option ->
        option.getPassType() == selectedPass.getPassType()
            && option.getDuration() == selectedPass.getDuration()
    )
    .findFirst()
    .orElse(null);
```

### After — 객체에게 시킨다
```java
// 객체가 자기 일을 안다
public class StudyCafeSeatPass {
    public boolean isSameDurationType(StudyCafeLockerPass lockerPass) {
        return lockerPass.isSamePassType(this.passType)
            && lockerPass.isSameDuration(this.duration);
    }
}

public class StudyCafeLockerPass {
    public boolean isSamePassType(StudyCafePassType passType) {
        return this.passType == passType;
    }

    public boolean isSameDuration(int duration) {
        return this.duration == duration;
    }
}

// 호출부는 의도만 말한다
Optional<StudyCafeLockerPass> lockerPass = allLockerPasses.findLockerPassBy(selectedPass);
```

### 왜
Before는 호출자가 `getPassType()`, `getDuration()`을 꺼내서 비교한다. 즉 호출자가 "두 패스가 같은지 판단하는 로직"을 떠안고 있다.
이 로직이 여러 곳에서 필요하면 어디서나 같은 비교 코드가 중복되고, 비교 규칙이 바뀌면 모든 곳을 다 고쳐야 한다.

After는 객체에게 `isSameDurationType()`을 묻는다. 비교 규칙은 객체 안에 한 번만 적혀있다. 캡슐화의 본질.

**리팩토링 신호: getter를 호출한 직후에 if 비교가 나오면 거의 99% Tell, Don't Ask 위반이다.**

---

## 3. 도메인 객체로 책임 이동

### Before — 서비스가 직접 계산
```java
// 서비스 메서드에서 도메인 값을 꺼내 직접 계산
int totalPrice = selectedPass.getPrice();
if (lockerPass != null) {
    totalPrice += lockerPass.getPrice();
}
int discountPrice = (int) (selectedPass.getPrice() * selectedPass.getDiscountRate());
totalPrice -= discountPrice;
```

### After — PassOrder가 자기 가격 계산 책임을 가짐
```java
public class StudyCafePassOrder {
    public int getTotalPrice() {
        int lockerPassPrice = lockerPass != null ? lockerPass.getPrice() : 0;
        int totalPassPrice = seatPass.getPrice() + lockerPassPrice;
        return totalPassPrice - getDiscountPrice();
    }

    public int getDiscountPrice() {
        return seatPass.getDiscountPrice();
    }
}

// 호출부
int total = passOrder.getTotalPrice();
```

### 왜
가격 계산 규칙은 "주문"이라는 도메인 개념에 속한다. 서비스에 흩어두면 같은 계산이 여기저기 중복되고, 규칙 변경 시 누락이 생긴다.
도메인 객체에 두면 규칙이 한 곳에 모이고, 테스트도 도메인 단위로 쉬워진다.

---

## 4. Value Object

### Before — 좌표를 두 개의 int로 들고 다님
```java
private static int selectedRowIndex;
private static int selectedColIndex;

private static boolean isInvalidPosition(int row, int col) {
    return row < 0 || row >= BOARD_ROW_SIZE || col < 0 || col >= BOARD_COL_SIZE;
}

open(selectedRowIndex, selectedColIndex);
open(selectedRowIndex - 1, selectedColIndex);
open(selectedRowIndex - 1, selectedColIndex + 1);
// ... 8방향
```

### After — CellPosition VO
```java
public class CellPosition {
    private final int rowIndex;
    private final int colIndex;

    private CellPosition(int rowIndex, int colIndex) {
        if (rowIndex < 0 || colIndex < 0) {
            throw new IllegalArgumentException("올바르지 않은 좌표입니다.");
        }
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    public static CellPosition of(int rowIndex, int colIndex) {
        return new CellPosition(rowIndex, colIndex);
    }

    public CellPosition calculatePositionBy(RelativePosition relativePosition) {
        if (this.canCalculatePositionBy(relativePosition)) {
            return CellPosition.of(
                this.rowIndex + relativePosition.getDeltaRow(),
                this.colIndex + relativePosition.getDeltaCol()
            );
        }
        throw new IllegalArgumentException("움직일 수 있는 좌표가 아닙니다.");
    }

    @Override
    public boolean equals(Object o) { ... }
    @Override
    public int hashCode() { ... }
}
```

### 왜
"행 인덱스, 열 인덱스" 두 개는 사실 "좌표"라는 한 개념이다. 분리해서 들고 다니면 어디선가 둘이 어긋난다.
VO로 묶으면:
- 생성 시점 검증으로 잘못된 상태 방지
- 좌표 관련 동작(이동, 비교)을 한 곳에 모음
- `equals`/`hashCode` 덕에 Map/Set에서 안전하게 쓸 수 있음
- 불변이라 공유해도 안전

VO 체크리스트: `private final` 필드, `private` 생성자, 정적 팩토리, `equals`/`hashCode`, 변경 메서드는 새 객체 반환.

---

## 5. 일급 컬렉션

### Before — 리스트를 그대로 들고 다니고, 필터 로직이 외부에 있음
```java
List<StudyCafePass> studyCafePasses = studyCafeFileHandler.readStudyCafePasses();
List<StudyCafePass> hourlyPasses = studyCafePasses.stream()
    .filter(studyCafePass -> studyCafePass.getPassType() == StudyCafePassType.HOURLY)
    .toList();
```

### After
```java
public class StudyCafeSeatPasses {
    private final List<StudyCafeSeatPass> passes;

    private StudyCafeSeatPasses(List<StudyCafeSeatPass> passes) {
        this.passes = passes;
    }

    public static StudyCafeSeatPasses of(List<StudyCafeSeatPass> passes) {
        return new StudyCafeSeatPasses(passes);
    }

    public List<StudyCafeSeatPass> findPassBy(StudyCafePassType studyCafePassType) {
        return passes.stream()
            .filter(pass -> pass.isSamePassType(studyCafePassType))
            .toList();
    }
}

// 호출부
StudyCafeSeatPasses allPasses = seatPassProvider.getSeatPasses();
List<StudyCafeSeatPass> hourlyPasses = allPasses.findPassBy(StudyCafePassType.HOURLY);
```

### 왜
"이용권 목록"은 단순한 `List<Pass>`가 아니라 "조회/필터링/계산"이 가능한 도메인 개념이다.
일급 컬렉션으로 감싸면 컬렉션 관련 로직이 한 곳에 모이고, 호출부에서 stream 코드 중복이 사라진다.
컬렉션을 노출하지 않으니 외부에서 임의로 수정할 수도 없다.

이름은 복수형으로: `Cells`, `StudyCafeSeatPasses`, `Orders`.

---

## 6.1 Enum의 기본 활용

### Before
```java
// 문자 → 숫자 매핑을 거대한 switch로
switch (c) {
    case 'a': col = 0; break;
    case 'b': col = 1; break;
    case 'c': col = 2; break;
    // ...
    case 'j': col = 9; break;
    default: col = -1; break;
}

// 상태를 int로 표현하고 주석으로 의미 적기
private static int gameStatus = 0; // 0: 게임 중, 1: 승리, -1: 패배
```

### After
```java
public enum GameStatus {
    IN_PROGRESS,
    WIN,
    LOSE;
}

public enum UserAction {
    OPEN("셀 열기"),
    FLAG("깃발 꽂기"),
    UNKNOWN("알 수 없음");

    private final String description;

    UserAction(String description) {
        this.description = description;
    }
}

// 사용
if (gameBoard.isWinStatus()) { ... }
if (userAction == UserAction.OPEN) { ... }
```

### 왜
숫자 + 주석으로 상태를 표현하면 누군가 새 상태를 추가할 때 주석만 안 바꾸고 끝낼 수 있다.
enum은 컴파일러가 모든 case를 강제하고, IDE 자동완성이 후보를 다 보여준다.
상태/타입에 따른 분기는 enum의 메서드로 위임하면 if-else 자체가 사라진다 (다음 섹션).

---

## 6.2 Enum에 행위 부여 (다형성으로 분기 제거)

### Before — 타입별로 부호를 결정하는 if-else
```java
public String findCellSign(CellSnapshot snapshot) {
    if (snapshot.getStatus() == CellSnapshotStatus.EMPTY) {
        return "■";
    }
    if (snapshot.getStatus() == CellSnapshotStatus.FLAG) {
        return "⚑";
    }
    if (snapshot.getStatus() == CellSnapshotStatus.LAND_MINE) {
        return "☼";
    }
    if (snapshot.getStatus() == CellSnapshotStatus.NUMBER) {
        return String.valueOf(snapshot.getNearbyLandMineCount());
    }
    if (snapshot.getStatus() == CellSnapshotStatus.UNCHECKED) {
        return "□";
    }
    throw new IllegalArgumentException("확인할 수 없는 셀입니다.");
}
```

### After
```java
public enum CellSignProvider implements CellSignProvidable {
    EMPTY(CellSnapshotStatus.EMPTY) {
        @Override
        public String provide(CellSnapshot cellSnapshot) {
            return EMPTY_SIGN;
        }
    },
    FLAG(CellSnapshotStatus.FLAG) {
        @Override
        public String provide(CellSnapshot cellSnapshot) {
            return FLAG_SIGN;
        }
    },
    NUMBER(CellSnapshotStatus.NUMBER) {
        @Override
        public String provide(CellSnapshot cellSnapshot) {
            return String.valueOf(cellSnapshot.getNearbyLandMineCount());
        }
    },
    // ... 나머지
    ;

    private static final String EMPTY_SIGN = "■";
    private static final String FLAG_SIGN = "⚑";

    private final CellSnapshotStatus status;

    CellSignProvider(CellSnapshotStatus status) {
        this.status = status;
    }

    public static String findCellSignFrom(CellSnapshot snapshot) {
        return Arrays.stream(values())
            .filter(provider -> provider.supports(snapshot))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("확인할 수 없는 셀입니다."))
            .provide(snapshot);
    }

    @Override
    public boolean supports(CellSnapshot cellSnapshot) {
        return cellSnapshot.isSameStatus(status);
    }
}
```

### 왜
새로운 셀 타입이 추가될 때 Before는 if-else에 한 줄을 더 끼워야 하지만, After는 enum 상수 하나만 추가하면 된다 (OCP).
각 상수가 자기 행동을 갖기 때문에 "이 타입은 어떻게 동작하는가"가 한 자리에 모인다.

---

## 6.3 다형성으로 if-else 제거 (PassType 예시)

### Before
```java
if (studyCafePassType == StudyCafePassType.HOURLY) {
    // 시간권 처리 로직
} else if (studyCafePassType == StudyCafePassType.WEEKLY) {
    // 주간권 처리 로직 (거의 똑같음)
} else if (studyCafePassType == StudyCafePassType.FIXED) {
    // 고정석 처리 로직 (사물함 추가)
}
```

### After
```java
public enum StudyCafePassType {
    HOURLY("시간 단위 이용권"),
    WEEKLY("주 단위 이용권"),
    FIXED("1인 고정석");

    private static final Set<StudyCafePassType> LOCKER_TYPES = Set.of(FIXED);

    public boolean isLockerType() {
        return LOCKER_TYPES.contains(this);
    }
}

// 흐름은 동일, 사물함 여부만 도메인 객체가 판단
private Optional<StudyCafeLockerPass> selectLockerPass(StudyCafeSeatPass selectedPass) {
    if (selectedPass.cannotUseLocker()) {
        return Optional.empty();
    }
    // ... 사물함 선택 로직
}
```

### 왜
타입별로 "거의 같은데 살짝 다른" 처리가 반복되면 공통 흐름을 뽑고 차이는 타입 자체가 알게 한다.
세 가지 타입의 분기 흐름이 사라지고, 사물함 가능 여부만 도메인이 답한다.

---

## 7. 상속보다 조합

### Before — 모든 셀이 한 클래스에 다 들어있고 플래그로 구분
```java
public class Cell {
    private boolean isLandMine;
    private int nearbyLandMineCount;
    private boolean isOpened;
    private boolean isFlagged;

    public String getSign() {
        if (isLandMine) return "☼";
        if (nearbyLandMineCount > 0) return String.valueOf(nearbyLandMineCount);
        return "■";
    }
}
```

### After — 인터페이스 + 조합
```java
public interface Cell {
    boolean isLandMine();
    boolean hasLandMineCount();
    CellSnapshot getSnapshot();
    void flag();
    void open();
    boolean isOpened();
}

public class EmptyCell implements Cell { ... }
public class LandMineCell implements Cell { ... }
public class NumberCell implements Cell {
    private final int nearbyLandMineCount;
    private final CellState cellState = CellState.initialize();
    // ...
}

// 공통 상태는 별도 객체로 추출 (상속이 아니라 조합)
public class CellState {
    private boolean isFlagged;
    private boolean isOpened;
    // ...
}
```

### 왜
"빈 셀이라는 게 셀이긴 한데..." 같이 본질이 다른 종류를 한 클래스에 플래그로 욱여넣으면 분기 코드가 사방에서 자란다.
인터페이스로 역할을 정의하고 구현체를 나누면 각 타입의 행동이 그 클래스 안에서 완결된다.

공통 상태(`isOpened`, `isFlagged`)는 부모 클래스로 상속받지 말고 `CellState`로 추출해서 각 셀이 필드로 들고 있다 (has-a).
상속은 본질적으로 같은 종류일 때만, 조합은 "이 객체가 어떤 능력을 갖고 있다"일 때 쓴다.

---

## 8. OCP — 확장에 열려있고 수정에 닫혀있다

### Before
```java
// 게임 레벨 추가하려면 main 코드 수정 필요
public static final int BOARD_ROW_SIZE = 8;
public static final int BOARD_COL_SIZE = 10;
public static final int LAND_MINE_COUNT = 10;
```

### After
```java
public interface GameLevel {
    int getRowSize();
    int getColSize();
    int getLandMineCount();
}

public class Beginner implements GameLevel {
    @Override public int getRowSize() { return 8; }
    @Override public int getColSize() { return 10; }
    @Override public int getLandMineCount() { return 10; }
}

public class Advanced implements GameLevel {
    @Override public int getRowSize() { return 20; }
    @Override public int getColSize() { return 24; }
    @Override public int getLandMineCount() { return 99; }
}

// 사용 시 외부에서 주입
public class GameConfig {
    private final GameLevel gameLevel;
    // ...
}
```

### 왜
새 난이도가 추가될 때 기존 코드를 손대지 않고 새 클래스만 추가한다.
"수정"이 아니라 "확장"으로 기능이 늘어나기 때문에 기존 동작을 망가뜨릴 위험이 없다.

단, **확장 가능성이 명백하지 않으면 미리 추상화하지 않는다**. 두 번째 종류가 등장할 때 추상화한다 (YAGNI).

---

## 9. DIP — 추상에 의존

### Before
```java
public class StudyCafePassMachine {
    public void run() {
        // 구현체에 직접 의존
        StudyCafeFileHandler studyCafeFileHandler = new StudyCafeFileHandler();
        List<StudyCafePass> studyCafePasses = studyCafeFileHandler.readStudyCafePasses();
        // ...
    }
}
```

### After
```java
public interface SeatPassProvider {
    StudyCafeSeatPasses getSeatPasses();
}

// 구현체 1: 파일
public class SeatPassFileReader implements SeatPassProvider {
    @Override
    public StudyCafeSeatPasses getSeatPasses() {
        // 파일에서 읽기
    }
}

// 구현체 2: DB든 API든 자유롭게 추가 가능
public class SeatPassApiReader implements SeatPassProvider { ... }

public class StudyCafePassMachine {
    private final SeatPassProvider seatPassProvider;

    public StudyCafePassMachine(SeatPassProvider seatPassProvider, ...) {
        this.seatPassProvider = seatPassProvider;
    }
}
```

### 왜
고수준 모듈(PassMachine)이 저수준 구현(FileHandler)에 직접 의존하면, 데이터 소스가 바뀌면 PassMachine을 수정해야 한다.
인터페이스를 두면 PassMachine은 "Provider가 데이터를 준다"는 추상만 알면 되고, 어떤 구현체를 쓸지는 외부에서 결정한다.
테스트할 때 가짜 Provider를 주입하기도 쉬워진다.

---

## 10. 클래스 내 멤버 선언 순서 (도메인 객체 버전)

### After
```java
public class StudyCafeSeatPass implements StudyCafePass {

    // 1. 인스턴스 필드 (모두 private final)
    private final StudyCafePassType passType;
    private final int duration;
    private final int price;
    private final double discountRate;

    // 2. 생성자 (private)
    private StudyCafeSeatPass(StudyCafePassType passType, int duration, int price, double discountRate) {
        this.passType = passType;
        this.duration = duration;
        this.price = price;
        this.discountRate = discountRate;
    }

    // 3. 정적 팩토리 메서드
    public static StudyCafeSeatPass of(StudyCafePassType passType, int duration, int price, double discountRate) {
        return new StudyCafeSeatPass(passType, duration, price, discountRate);
    }

    // 4. public 비즈니스 메서드 (행위)
    public boolean cannotUseLocker() {
        return this.passType.isNotLockerType();
    }

    public boolean isSameDurationType(StudyCafeLockerPass lockerPass) {
        return lockerPass.isSamePassType(this.passType)
            && lockerPass.isSameDuration(this.duration);
    }

    public boolean isSamePassType(StudyCafePassType passType) {
        return this.passType == passType;
    }

    public int getDiscountPrice() {
        return (int) (this.price * this.discountRate);
    }

    // 5. getter (정말 필요한 것만, 맨 아래)
    @Override
    public StudyCafePassType getPassType() {
        return passType;
    }

    @Override
    public int getDuration() {
        return duration;
    }

    @Override
    public int getPrice() {
        return price;
    }
}
```

### 왜
위에서 아래로 읽었을 때 "이 클래스가 무엇이고 → 어떻게 만들어지고 → 무엇을 할 수 있고 → 어떤 정보를 노출하는가" 순으로 자연스럽게 흐른다.
도메인 객체에서는 **비즈니스 행위가 먼저 눈에 들어와야** 객체의 역할이 강조된다. getter가 위에 있으면 객체가 "데이터 컨테이너"처럼 보인다.

도메인 객체의 getter는 정말 필요한 것만 노출한다 (View 렌더링, 영속화 등). getter가 늘어날수록 Tell Don't Ask 위반의 통로가 된다.

---

## 종합 체크리스트 — 도메인 코드 작성 후 마지막 점검

다음 항목 중 하나라도 걸리면 SKILL.md의 해당 섹션을 다시 본다.

| 신호 | 해당 규칙 |
|---|---|
| 클래스 역할 설명에 "그리고"가 들어감 | 1 (SRP) |
| getter 호출 직후 if 비교 | 2 (Tell Don't Ask) |
| 서비스 메서드 안에 getter + 계산이 연속 | 3 (도메인 책임 이동) |
| 같은 비즈니스 계산이 두 군데 이상 서비스에 있음 | 3 |
| 원시 타입 두 개 이상이 항상 같이 다님 | 4 (VO) |
| 그 값 묶음에 검증 규칙이 있음 | 4 (VO) |
| `List<X>`를 그대로 들고 다니며 stream 필터 중복 | 5 (일급 컬렉션) |
| 매직 넘버 + "0: 진행 중, 1: 승리" 같은 의미 주석 | 6.1 (Enum) |
| `type == A` 비교가 여러 군데 | 6.2, 6.3 (Enum 다형성) |
| "거의 같은데 살짝 다른" 분기가 타입별로 반복 | 6.3 |
| 부모 클래스에 `instanceof` 분기가 있음 | 7 (조합) |
| 자식이 부모 메서드 일부만 의미가 있음 | 7 (조합) |
| 새 종류 추가할 때 기존 if-else를 수정해야 함 | 8 (OCP) |
| 클래스 안에서 `new XxxFileHandler()` 같은 구체 생성 | 9 (DIP) |
| 도메인 객체의 getter가 비즈니스 행위보다 많음 | 10 (멤버 순서/getter 최소화) |

> 가독성 관련 신호(이름, 매직 넘버, 메서드 길이 등)는 `readable-java` 스킬의 체크리스트를 본다.
