package com.foodservice.domain.member.service;

import com.foodservice.common.exception.member.MailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private static final String SUBJECT = "[음식 나눔 서비스] 이메일 인증 코드";

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String email, String code) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(email);
            helper.setSubject(SUBJECT);
            helper.setText(buildHtml(code), true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new MailSendException(e);
        }
    }

    private String buildHtml(String code) {
        return """
                <div style="max-width:480px;margin:0 auto;font-family:sans-serif;border:1px solid #eee;border-radius:8px;padding:32px">
                  <h2 style="margin:0 0 16px">이메일 인증</h2>
                  <p style="color:#555">아래 인증 코드를 입력해 회원가입을 완료하세요. (5분 내 유효)</p>
                  <div style="font-size:32px;font-weight:700;letter-spacing:8px;text-align:center;background:#f5f6f8;border-radius:8px;padding:20px;margin:24px 0">%s</div>
                  <p style="color:#999;font-size:12px">본인이 요청하지 않았다면 이 메일을 무시하세요.</p>
                </div>
                """.formatted(code);
    }
}
