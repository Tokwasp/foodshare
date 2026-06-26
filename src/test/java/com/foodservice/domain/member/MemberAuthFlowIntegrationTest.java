package com.foodservice.domain.member;

import com.foodservice.domain.member.service.MailService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MemberAuthFlowIntegrationTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Passw0rd!@";
    private static final String NICK_NAME = "테스터";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    @TestConfiguration
    static class TestSessionConfig {
        @Bean
        SessionRepository<MapSession> sessionRepository() {
            return new MapSessionRepository(new ConcurrentHashMap<>());
        }
    }

    @BeforeEach
    void setUpRedisStub() {
        redisStore.clear();
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);

        // valueOps.set메소드를 스터빙할 때, 파라미터로 들어온 첫번째 인자(key), 두번째 인자(value)를 레디스스토어에 넣는다.
        willAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).given(valueOps).set(anyString(), anyString(), ArgumentMatchers.any(Duration.class));

        given(valueOps.get(anyString()))
                .willAnswer(invocation -> redisStore.get(invocation.getArgument(0)));

        given(stringRedisTemplate.delete(anyString()))
                .willAnswer(invocation -> redisStore.remove(invocation.getArgument(0)) != null);
    }

    @Test
    @DisplayName("이메일 인증부터 로그인까지 전 과정이 성공한다")
    void emailVerificationToLoginSucceeds() throws Exception {
        // 1. 인증코드 발송 → 메일 mock에서 코드 캡처
        mockMvc.perform(post("/api/v1/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isOk());

        // 호출이된 코드 값을 캡처하는 코드
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationCode(eq(EMAIL), codeCaptor.capture());
        String code = codeCaptor.getValue();

        // 2. 인증코드 검증 → emailVerifyToken 추출
        String verifyResponse = mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String emailVerifyToken = JsonPath.read(verifyResponse, "$.data.emailVerifyToken");
        System.out.println("emailVerifyToken = " + emailVerifyToken);
        // 3. 회원가입
        Map<String, Object> signUpBody = Map.of(
                "email", EMAIL,
                "emailVerifyToken", emailVerifyToken,
                "password", PASSWORD,
                "nickName", NICK_NAME,
                "address", Map.of("roadAddress", "서울시 강남구 테헤란로", "detailAddress", "101동 202호")
        );
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberId").exists());

        // 4. 로그인
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").exists())
                .andExpect(jsonPath("$.data.nickName").value(NICK_NAME))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."));
    }
}
