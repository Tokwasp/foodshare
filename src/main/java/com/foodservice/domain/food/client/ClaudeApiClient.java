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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ClaudeApiClient implements ExpirationApiClient {

    private static final String GMS_CLAUDE_URL =
            "https://gms.ssafy.io/gmsapi/api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 100;
    private static final String PROMPT =
            "이 음식 이미지를 분석해서 소비기한 날짜를 YYYY-MM-DD 형식으로만 답변해줘. 날짜 외 다른 텍스트는 포함하지 마.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ImageCompressor imageCompressor;

    public ClaudeApiClient(
            RestClient.Builder restClientBuilder,
            String gmsKey,
            ObjectMapper objectMapper,
            ImageCompressor imageCompressor) {
        this.restClient = restClientBuilder.clone()
                .baseUrl(GMS_CLAUDE_URL)
                .defaultHeader("x-api-key", gmsKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
        this.objectMapper = objectMapper;
        this.imageCompressor = imageCompressor;
    }

    // 테스트용 생성자 (package-private)
    ClaudeApiClient(RestClient restClient, ObjectMapper objectMapper, ImageCompressor imageCompressor) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.imageCompressor = imageCompressor;
    }

    @Override
    public LocalDate fetchExpirationDate(MultipartFile file) {
        byte[] compressedBytes = imageCompressor.compress(file);
        String base64Image = Base64.getEncoder().encodeToString(compressedBytes);

        // 주의: Map.of 는 키 순서를 보장하지 않아 model 이 거대한 base64 이미지(messages) 뒤로
        // 직렬화될 수 있다. GMS 게이트웨이는 요청 본문 앞부분만 읽어 model 을 추출하므로,
        // 이미지가 크면 model 을 못 찾아 "Model not found in request" 400 을 반환한다.
        // → LinkedHashMap 으로 model 을 항상 본문 맨 앞에 고정한다.
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "base64");
        source.put("media_type", "image/jpeg");
        source.put("data", base64Image);

        Map<String, Object> imageBlock = new LinkedHashMap<>();
        imageBlock.put("type", "image");
        imageBlock.put("source", source);

        Map<String, Object> textBlock = new LinkedHashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", PROMPT);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(imageBlock, textBlock));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("max_tokens", MAX_TOKENS);
        requestBody.put("messages", List.of(message));

        try {
            String raw = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (raw == null) {
                throw new ExpirationApiException();
            }

            ClaudeResponse response = objectMapper.readValue(raw, ClaudeResponse.class);
            return parseDate(response);

        } catch (HttpClientErrorException e) {
            log.warn(e.getResponseBodyAsString());
            throw new ExpirationApiException(e);
        } catch (RestClientException e) {
            throw new ExpirationApiException(e);
        }
    }

    private LocalDate parseDate(ClaudeResponse response) {
        try {
            String dateText = response.content().get(0).text().strip();
            return LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new ExpirationApiException(e);
        }
    }

    record ClaudeResponse(List<ContentBlock> content) {}
    record ContentBlock(String type, String text) {}
}
