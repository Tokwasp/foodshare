package com.foodservice.domain.food.client;

import com.foodservice.common.exception.food.ExpirationApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GeminiApiClientUnitTest {

    @Mock private RestClient restClient;
    @Mock private ObjectMapper om;
    @Mock private ImageCompressor imageCompressor;

    private GeminiApiClient geminiApiClient;

    @BeforeEach
    void setUp() {
        geminiApiClient = new GeminiApiClient(restClient, om, imageCompressor);
    }

    @Test
    @DisplayName("이미지 압축 중 예외가 발생하면 ExpirationApiException이 발생한다.")
    void fetchExpirationDate_throwsException_whenImageCompressFails() {
        MockMultipartFile file = new MockMultipartFile("file", "food.jpg", "image/jpeg", "not-an-image".getBytes());
        given(imageCompressor.compress(file)).willThrow(new ExpirationApiException());

        assertThatThrownBy(() -> geminiApiClient.fetchExpirationDate(file))
                .isInstanceOf(ExpirationApiException.class);
    }

    @Test
    @DisplayName("API 응답 본문이 null이면 ExpirationApiException이 발생한다.")
    void fetchExpirationDate_throwsException_whenResponseBodyIsNull() {
        MockMultipartFile file = new MockMultipartFile("file", "food.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(imageCompressor.compress(file)).willReturn(new byte[]{1, 2, 3});

        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        given(restClient.post()).willReturn(uriSpec);
        given(uriSpec.contentType(MediaType.APPLICATION_JSON)).willReturn(bodySpec);
        given(bodySpec.body(any(Map.class))).willReturn(bodySpec);
        given(bodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(String.class)).willReturn(null);

        assertThatThrownBy(() -> geminiApiClient.fetchExpirationDate(file))
                .isInstanceOf(ExpirationApiException.class);
    }
}
