package com.foodservice.external;

import com.foodservice.domain.member.service.MailService;
import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@ContextConfiguration(initializers = DotenvApplicationInitializer.class)
@Transactional
@ActiveProfiles("test")
@SpringBootTest
class MailServiceIntegrationTest {

    @Value("${spring.mail.username}")
    private String mailUsername;

    @Autowired
    private MailService mailService;

    @DisplayName("실제 Gmail SMTP로 인증 코드 메일을 발송한다.")
    @Disabled
    @Test
    void sendVerificationCode() {
        // given
        assertThat(mailUsername).as("MAIL_USERNAME 환경변수가 필요합니다.").isNotBlank();

        // when // then
        assertThatNoException()
                .isThrownBy(() -> mailService.sendVerificationCode(mailUsername, "123456"));
    }
}
