package com.foodservice.common.config;

import com.foodservice.domain.food.client.ClaudeApiClient;
import com.foodservice.domain.food.client.ExpirationApiClient;
import com.foodservice.domain.food.client.GeminiApiClient;
import com.foodservice.domain.food.client.ImageCompressor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Profile("!test & !s3-test")
public class GMSApiClientConfig {

    @Bean
    public ImageCompressor imageCompressor() {
        return new ImageCompressor();
    }

    @Bean
    @ConditionalOnProperty(name = "expiration.client", havingValue = "gemini")
    public GeminiApiClient geminiApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${gms.key}") String gmsKey,
            ObjectMapper objectMapper,
            ImageCompressor imageCompressor) {
        return new GeminiApiClient(restClientBuilder, gmsKey, objectMapper, imageCompressor);
    }

    @Bean
    @ConditionalOnMissingBean(ExpirationApiClient.class)
    public ClaudeApiClient claudeApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${gms.key}") String gmsKey,
            ObjectMapper objectMapper,
            ImageCompressor imageCompressor) {
        return new ClaudeApiClient(restClientBuilder, gmsKey, objectMapper, imageCompressor);

    }
}
