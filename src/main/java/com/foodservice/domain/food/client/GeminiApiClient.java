package com.foodservice.domain.food.client;

import com.foodservice.common.exception.food.ExpirationApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
public class GeminiApiClient implements ExpirationApiClient {

    private static final String GMS_GEMINI_URL =
            "https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent";
    private static final String PROMPT =
            "이 음식 이미지를 분석해서 소비기한 날짜를 YYYY-MM-DD 형식으로만 답변해줘. 날짜 외 다른 텍스트는 포함하지 마.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ImageCompressor imageCompressor;

    public GeminiApiClient(
            RestClient.Builder restClientBuilder,
            String gmsKey,
            ObjectMapper objectMapper,
            ImageCompressor imageCompressor) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(GMS_GEMINI_URL)
                .defaultHeader("x-goog-api-key", gmsKey)
                .build();
        this.objectMapper = objectMapper;
        this.imageCompressor = imageCompressor;
    }

    // 테스트용 생성자 (package-private)
    GeminiApiClient(RestClient restClient, ObjectMapper objectMapper, ImageCompressor imageCompressor) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.imageCompressor = imageCompressor;
    }

    @Override
    public LocalDate fetchExpirationDate(MultipartFile file) {
        byte[] compressedBytes = imageCompressor.compress(file);
        String base64Image = Base64.getEncoder().encodeToString(compressedBytes);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", PROMPT),
                                Map.of("inlineData", Map.of(
                                        "mimeType", "image/jpeg",
                                        "data", base64Image
                                ))
                        ))
                )
        );

        try {
            String raw = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (raw == null) {
                throw new ExpirationApiException();
            }

            GeminiResponse response = objectMapper.readValue(raw, GeminiResponse.class);
            return parseDate(response);

        } catch (HttpClientErrorException e) {
           log.warn(e.getResponseBodyAsString());
            throw new ExpirationApiException(e);
        } catch (RestClientException e) {
            throw new ExpirationApiException(e);
        }
    }

    private LocalDate parseDate(GeminiResponse response) {
        try {
            String dateText = response.candidates().get(0)
                    .content().parts().get(0)
                    .text().strip();
            return LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new ExpirationApiException(e);
        }
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(List<Part> parts) {}
    record Part(String text) {}
}
