package com.foodservice.domain.food.client;

import com.foodservice.common.exception.food.ExpirationApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class ClaudeApiClientUnitTest {

    @Mock private RestClient restClient;
    @Mock private ObjectMapper om;
    @Mock private ImageCompressor imageCompressor;

    private ClaudeApiClient claudeApiClient;

    @BeforeEach
    void setUp() {
        claudeApiClient = new ClaudeApiClient(restClient, om, imageCompressor);
    }

    @Test
    @DisplayName("이미지 압축 중 예외가 발생하면 ExpirationApiException이 발생한다.")
    void fetchExpirationDate_throwsException_whenImageCompressFails() {
        MockMultipartFile file = new MockMultipartFile("file", "food.jpg", "image/jpeg", "not-an-image".getBytes());
        given(imageCompressor.compress(file)).willThrow(new ExpirationApiException());

        assertThatThrownBy(() -> claudeApiClient.fetchExpirationDate(file))
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

        assertThatThrownBy(() -> claudeApiClient.fetchExpirationDate(file))
                .isInstanceOf(ExpirationApiException.class);
    }

    @Test
    @DisplayName("요청 본문에서 model 이 messages(이미지 데이터)보다 먼저 직렬화된다.")
    void fetchExpirationDate_serializesModelBeforeMessages() {
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

        try {
            claudeApiClient.fetchExpirationDate(file);
        } catch (ExpirationApiException ignored) {
            // 응답이 null 이라 예외가 나지만, 본문 캡처가 목적이므로 무시한다.
        }

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(bodySpec).body(captor.capture());

        String json = new ObjectMapper().writeValueAsString(captor.getValue());
        assertThat(json.indexOf("\"model\"")).isGreaterThanOrEqualTo(0);
        assertThat(json.indexOf("\"model\"")).isLessThan(json.indexOf("\"messages\""));
    }
}
