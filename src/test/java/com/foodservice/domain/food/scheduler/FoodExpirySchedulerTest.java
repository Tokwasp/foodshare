package com.foodservice.domain.food.scheduler;

import com.foodservice.domain.food.facade.FoodFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodExpirySchedulerTest {

    @Mock
    private FoodFacade foodFacade;

    @InjectMocks
    private FoodExpiryScheduler scheduler;

    @Test
    @DisplayName("스케줄러 실행 시 현재 시각을 기준으로 foodFacade.bulkExpire가 호출된다.")
    void expireFoods_callsBulkExpireWithCurrentTime() {
        // given
        LocalDateTime before = LocalDateTime.now();
        willDoNothing().given(foodFacade).bulkExpire(org.mockito.ArgumentMatchers.any());

        // when
        scheduler.expireFoods();

        // then
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(foodFacade, times(1)).bulkExpire(captor.capture());

        LocalDateTime after = LocalDateTime.now();
        LocalDateTime capturedTime = captor.getValue();
        assertThat(capturedTime).isAfterOrEqualTo(before);
        assertThat(capturedTime).isBeforeOrEqualTo(after);
    }
}
