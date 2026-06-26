package com.foodservice.domain.member.service;

import com.foodservice.common.exception.member.*;
import com.foodservice.domain.member.Address;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.dto.request.MemberCreateServiceRequest;
import com.foodservice.domain.member.dto.request.MemberUpdateRequest;
import com.foodservice.domain.member.dto.response.EmailSendResponse;
import com.foodservice.domain.member.dto.response.EmailVerifyResponse;
import com.foodservice.domain.member.dto.response.LoginResponse;
import com.foodservice.domain.member.dto.response.MemberCreateResponse;
import com.foodservice.domain.member.dto.response.MemberInfoResponse;
import com.foodservice.domain.member.dto.response.NicknameCheckResponse;
import com.foodservice.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private static final String VERIFICATION_CODE_KEY_PREFIX = "email:verify:";
    private static final int VERIFICATION_CODE_EXPIRES_IN_SECONDS = 300;

    private static final String EMAIL_VERIFY_TOKEN_KEY_PREFIX = "email:verify:token:";
    private static final int EMAIL_VERIFY_TOKEN_EXPIRES_IN_SECONDS = 3600;

    private final StringRedisTemplate stringRedisTemplate;
    private final MemberRepository memberRepository;
    private final MailService mailService;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final EmailVerifyTokenGenerator emailVerifyTokenGenerator;
    private final PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailSendResponse sendVerificationCode(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new EmailDuplicatedException();
        }
        String code = verificationCodeGenerator.generate();
        saveVerificationCode(email, code);
        mailService.sendVerificationCode(email, code);
        return EmailSendResponse.of(VERIFICATION_CODE_EXPIRES_IN_SECONDS);
    }

    public EmailVerifyResponse verifyCode(String email, String requestCode) {
        String savedCode = Optional.ofNullable(getSavedVerificationCode(email))
                .orElseThrow(CodeExpiredException::new);

        if (isNotSameCode(savedCode, requestCode)) {
            throw new CodeMismatchException();
        }

        deleteVerificationCode(email);
        String token = emailVerifyTokenGenerator.generate();
        saveEmailVerifyToken(email, token);
        return EmailVerifyResponse.of(true, token);
    }

    @Transactional
    public MemberCreateResponse signUp(MemberCreateServiceRequest request) {
        validateEmailNotDuplicated(request.getEmail());
        validateNickNameNotDuplicated(request.getNickName());
        validateEmailVerified(request.getEmail(), request.getEmailVerifyToken());

        consumeEmailVerifyToken(request.getEmail());
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = memberRepository.save(request.toEntity(encodedPassword));
        return MemberCreateResponse.of(member);
    }

    public LoginResponse login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(LoginFailedException::new);

        if (member.isNotMatchPassword(passwordEncoder, password)) {
            throw new LoginFailedException();
        }

        return LoginResponse.of(member);
    }

    public NicknameCheckResponse checkNicknameAvailable(String nickName) {
        boolean available = !memberRepository.existsByNickName(nickName);
        return NicknameCheckResponse.of(available);
    }

    // 닉네임 단건 조회 (없으면 null). 물품 상세의 등록자 닉네임 표시용.
    public String getNickName(Long memberId) {
        return memberRepository.findById(memberId)
                .map(Member::getNickName)
                .orElse(null);
    }

    // 닉네임 일괄 조회 (memberId → nickName). 받은 요청 목록의 신청자 닉네임 표시용.
    public java.util.Map<Long, String> getNickNames(java.util.Collection<Long> memberIds) {
        return memberRepository.findAllById(memberIds).stream()
                .collect(java.util.stream.Collectors.toMap(Member::getId, Member::getNickName));
    }

    public MemberInfoResponse getMyInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        return MemberInfoResponse.of(member);
    }

    @Transactional
    public MemberInfoResponse updateMyInfo(Long memberId, MemberUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);

        updateNickName(member, request.getNickName());
        updateAddress(member, request.toAddress());
        return MemberInfoResponse.of(member);
    }

    @Transactional
    public void deleteMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
        member.delete();
    }

    private void updateNickName(Member member, String nickName) {
        if (isNickNameUnchanged(member, nickName)) {
            return;
        }
        validateNickNameNotDuplicated(nickName);
        member.changeNickName(nickName);
    }

    private boolean isNickNameUnchanged(Member member, String nickName) {
        return nickName == null || nickName.equals(member.getNickName());
    }

    private void updateAddress(Member member, Address address) {
        if (address == null) {
            return;
        }
        member.changeAddress(address);
    }

    private void saveVerificationCode(String email, String code) {
        stringRedisTemplate.opsForValue().set(
                VERIFICATION_CODE_KEY_PREFIX + email,
                code,
                Duration.ofSeconds(VERIFICATION_CODE_EXPIRES_IN_SECONDS)
        );
    }

    private String getSavedVerificationCode(String email) {
        return stringRedisTemplate.opsForValue().get(VERIFICATION_CODE_KEY_PREFIX + email);
    }

    private boolean isNotSameCode(String savedCode, String requestCode) {
        return !savedCode.equals(requestCode);
    }

    private void deleteVerificationCode(String email) {
        stringRedisTemplate.delete(VERIFICATION_CODE_KEY_PREFIX + email);
    }

    private void saveEmailVerifyToken(String email, String token) {
        stringRedisTemplate.opsForValue().set(
                EMAIL_VERIFY_TOKEN_KEY_PREFIX + email,
                token,
                Duration.ofSeconds(EMAIL_VERIFY_TOKEN_EXPIRES_IN_SECONDS)
        );
    }

    private void validateEmailNotDuplicated(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new EmailDuplicatedException();
        }
    }

    private void validateNickNameNotDuplicated(String nickName) {
        if (memberRepository.existsByNickName(nickName)) {
            throw new NicknameDuplicatedException();
        }
    }

    private void validateEmailVerified(String email, String requestToken) {
        String savedToken = Optional.ofNullable(getSavedEmailVerifyToken(email))
                .orElseThrow(EmailNotVerifiedException::new);

        if (isNotSameToken(savedToken, requestToken)) {
            throw new EmailNotVerifiedException();
        }
    }

    private String getSavedEmailVerifyToken(String email) {
        return stringRedisTemplate.opsForValue().get(EMAIL_VERIFY_TOKEN_KEY_PREFIX + email);
    }

    private boolean isNotSameToken(String savedToken, String requestToken) {
        return !savedToken.equals(requestToken);
    }

    private void consumeEmailVerifyToken(String email) {
        boolean isNotTokenDeleted = !stringRedisTemplate.delete(EMAIL_VERIFY_TOKEN_KEY_PREFIX + email);
        if (isNotTokenDeleted) {
            throw new EmailDuplicatedException();
        }
    }
}
