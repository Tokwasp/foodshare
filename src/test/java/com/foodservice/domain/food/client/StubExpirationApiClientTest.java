package com.foodservice.domain.food.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StubExpirationApiClientTest {

    private final StubExpirationApiClient client = new StubExpirationApiClient();

    @Test
    @DisplayName("Stub은 오늘로부터 7일 후의 날짜를 반환한다.")
    void fetchExpirationDate_returnsFixedDate() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "food.png", "image/png", "image-content".getBytes()
        );

        // when
        LocalDate result = client.fetchExpirationDate(file);

        // then
        assertThat(result).isEqualTo(LocalDate.now().plusDays(7));
    }
}
