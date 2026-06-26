package com.foodservice.external;

import com.foodservice.domain.food.client.ClaudeApiClient;
import com.foodservice.external.fixture.ImageLoad;
import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(initializers = DotenvApplicationInitializer.class)
@ActiveProfiles("claude-test")
@EnabledIfEnvironmentVariable(named = "RUN_CLAUDE", matches = "true")
@ExtendWith(SpringExtension.class)
class ClaudeApiClientTest {

    @Autowired
    private ClaudeApiClient claudeApiClient;

    @Test
    @DisplayName("실제 GMS Claude API로 사진의 소비기한을 검증")
    void fetchExpirationDate_BCHOB_Success() throws Exception {
        MultipartFile bchob = ImageLoad.loadBchoB();
        LocalDate result = claudeApiClient.fetchExpirationDate(bchob);
        assertThat(result).isEqualTo(LocalDate.of(2027, 4, 20));
    }

    @Test
    @DisplayName("실제 GMS Claude API로 사진의 소비기한을 검증")
    void fetchExpirationDate_Strawberry_Success() throws Exception {
        MultipartFile strawberry = ImageLoad.loadStrawberry();
        LocalDate result = claudeApiClient.fetchExpirationDate(strawberry);
        assertThat(result).isEqualTo(LocalDate.of(2025, 5, 25));
    }
}
