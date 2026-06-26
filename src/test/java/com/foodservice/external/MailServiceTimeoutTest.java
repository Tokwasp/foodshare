package com.foodservice.external;

import com.foodservice.common.exception.member.MailSendException;
import com.foodservice.domain.member.service.MailService;
import org.junit.jupiter.api.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Disabled
class MailServiceTimeoutTest {

    private static final int TIMEOUT_MILLIS = 5000;

    private ServerSocket blackholeServer;
    private final List<Socket> acceptedSockets = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        blackholeServer = new ServerSocket(0);
        Thread acceptThread = new Thread(this::acceptAndHold);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Socket socket : acceptedSockets) {
            socket.close();
        }
        blackholeServer.close();
    }

    @DisplayName("SMTP 서버가 응답하지 않으면 timeout 대기 후 MailSendException이 발생한다.")
    @Timeout(10)
    @Test
    void sendVerificationCodeFailsAfterTimeout() {
        // given
        MailService mailService = new MailService(createBlackholeMailSender());

        // when
        long start = System.currentTimeMillis();
        Throwable thrown = catchThrowable(() -> mailService.sendVerificationCode("test@example.com", "123456"));
        long elapsed = System.currentTimeMillis() - start;

        // then
        assertThat(thrown).isInstanceOf(MailSendException.class);
        assertThat(elapsed)
                .as("즉시 실패가 아니라 실제 timeout(5초) 대기 후 실패해야 한다")
                .isGreaterThanOrEqualTo(TIMEOUT_MILLIS - 500);
    }

    private JavaMailSenderImpl createBlackholeMailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(blackholeServer.getLocalPort());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        properties.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
        properties.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));
        return sender;
    }

    private void acceptAndHold() {
        while (!blackholeServer.isClosed()) {
            try {
                acceptedSockets.add(blackholeServer.accept());
            } catch (IOException e) {
                return;
            }
        }
    }
}
