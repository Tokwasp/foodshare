package com.foodservice.domain.member.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.member.dto.request.MemberCreateRequest;
import com.foodservice.domain.member.dto.request.MemberUpdateRequest;
import com.foodservice.domain.member.dto.response.MemberCreateResponse;
import com.foodservice.domain.member.dto.response.MemberInfoResponse;
import com.foodservice.domain.member.dto.response.NicknameCheckResponse;
import com.foodservice.domain.member.service.MemberService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<ApiResponse<MemberCreateResponse>> signUp(@Valid @RequestBody MemberCreateRequest request) {
        MemberCreateResponse response = memberService.signUp(request.toServiceRequest());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "회원 가입에 성공했습니다."));
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<ApiResponse<NicknameCheckResponse>> checkNickname(@RequestParam String nickName) {
        NicknameCheckResponse response = memberService.checkNicknameAvailable(nickName);
        String message = response.isAvailable() ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다.";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> getMyInfo(@SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId) {
        MemberInfoResponse response = memberService.getMyInfo(memberId);
        return ResponseEntity.ok(ApiResponse.success(response, "조회에 성공했습니다."));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> updateMyInfo(@SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
                                                                        @Valid @RequestBody MemberUpdateRequest request) {
        MemberInfoResponse response = memberService.updateMyInfo(memberId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "수정에 성공했습니다."));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyInfo(@SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId, HttpSession session) {
        memberService.deleteMember(memberId);
        session.invalidate();
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "탈퇴 처리되었습니다."));
    }
}
