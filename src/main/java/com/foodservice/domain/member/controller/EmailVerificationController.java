package com.foodservice.domain.member.controller;

import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.member.dto.request.EmailSendRequest;
import com.foodservice.domain.member.dto.request.EmailVerifyRequest;
import com.foodservice.domain.member.dto.response.EmailSendResponse;
import com.foodservice.domain.member.dto.response.EmailVerifyResponse;
import com.foodservice.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/email")
public class EmailVerificationController {

    private final MemberService memberService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<EmailSendResponse>> sendCode(@Valid @RequestBody EmailSendRequest request) {
        EmailSendResponse response = memberService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response, "인증 코드를 발송했습니다."));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyCode(@Valid @RequestBody EmailVerifyRequest request) {
        EmailVerifyResponse response = memberService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(response, "이메일 인증에 성공했습니다."));
    }
}
