package com.foodservice.domain.food.scheduler;

import com.foodservice.domain.food.facade.FoodFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class FoodExpiryScheduler {

    private final FoodFacade foodFacade;

    // 매 분 0초마다 실행 — 필요 시 cron 조정
    /** TODO :
    @Scheduled는 각 애플리케이션 인스턴스마다 동작하므로, 수평 확장(2대 이상) 시 매 분 모든 인스턴스가 동시에 bulkExpire를 실행합니다.
    운영에서 다중 인스턴스를 쓸 계획이라면 ShedLock 같은 분산 락으로 단일 실행을 보장하는 것을 권장
    참고로 bulkExpire가 예외를 던지면 해당 분 실행만 실패하고 다음 분에 재시도되므로 동작 자체는 안전합니다.
     **/
    @Scheduled(cron = "0 * * * * *")
    public void expireFoods() {
        foodFacade.bulkExpire(LocalDateTime.now());
    }
}
