package com.foodservice.domain.member.service;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.exception.member.*;
import com.foodservice.domain.member.Address;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.dto.request.AddressRequest;
import com.foodservice.domain.member.dto.request.MemberCreateServiceRequest;
import com.foodservice.domain.member.dto.request.MemberUpdateRequest;
import com.foodservice.domain.member.dto.response.MemberInfoResponse;
import com.foodservice.domain.member.dto.response.EmailSendResponse;
import com.foodservice.domain.member.dto.response.EmailVerifyResponse;
import com.foodservice.domain.member.dto.response.LoginResponse;
import com.foodservice.domain.member.dto.response.MemberCreateResponse;
import com.foodservice.domain.member.dto.response.NicknameCheckResponse;
import com.foodservice.domain.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MemberServiceTest extends IntegrationTestSupport {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown(){
        memberRepository.deleteAllInBatch();
    }

    @DisplayName("가입되지 않은 이메일로 인증 코드 발송을 요청하면 인증 메일을 전송한다.")
    @Test
    void sendVerificationCode() {
        // given
        String email = "user@example.com";
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        given(stringRedisTemplate.opsForValue()).willReturn(mock(ValueOperations.class));

        // when
        memberService.sendVerificationCode(email);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("이미 가입된 이메일로 인증 코드 발송을 요청하면 예외가 발생한다.")
    @Test
    void sendVerificationCodeWithDuplicatedEmail() {
        // given
        String email = "user@example.com";
        memberRepository.save(createMember(email, "닉네임"));

        // when // then
        assertThatThrownBy(() -> memberService.sendVerificationCode(email))
                .isInstanceOf(EmailDuplicatedException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @DisplayName("인증 코드 발송에 성공하면 코드를 Redis에 TTL 5분으로 저장하고 만료 시간을 반환한다.")
    @Test
    void sendVerificationCodeStoresCodeInRedis() {
        // given
        String email = "user@example.com";
        final int TTL = 300;

        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        EmailSendResponse response = memberService.sendVerificationCode(email);

        // then
        assertThat(response.getExpiresIn()).isEqualTo(TTL);
        verify(valueOperations).set(eq("email:verify:" + email), anyString(), eq(Duration.ofSeconds(TTL)));
    }

    @DisplayName("인증 코드가 일치하면 인증 토큰을 발급하고 1시간 TTL로 Redis에 저장한다.")
    @Test
    void verifyCode() {
        // given
        String email = "user@example.com";
        String code = "123456";
        final int TOKEN_TTL = 3600;

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:" + email)).willReturn(code);

        // when
        EmailVerifyResponse response = memberService.verifyCode(email, code);

        // then
        assertThat(response.isVerified()).isTrue();
        assertThat(response.getEmailVerifyToken()).isNotBlank();
        verify(stringRedisTemplate).delete("email:verify:" + email);
        verify(valueOperations).set(eq("email:verify:token:" + email),
                anyString(), eq(Duration.ofSeconds(TOKEN_TTL)));
    }

    @DisplayName("인증 코드가 일치하지 않으면 예외가 발생한다.")
    @Test
    void verifyCodeWithMismatch() {
        // given
        String email = "user@example.com";
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:" + email)).willReturn("123456");

        // when // then
        assertThatThrownBy(() -> memberService.verifyCode(email, "000000"))
                .isInstanceOf(CodeMismatchException.class)
                .hasMessage("인증 코드가 일치하지 않습니다.");
    }

    @DisplayName("인증 코드가 만료되었거나 없으면 예외가 발생한다.")
    @Test
    void verifyCodeWithExpired() {
        // given
        String email = "user@example.com";
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:" + email)).willReturn(null);

        // when // then
        assertThatThrownBy(() -> memberService.verifyCode(email, "123456"))
                .isInstanceOf(CodeExpiredException.class)
                .hasMessage("인증 코드가 만료되었습니다.");
    }

    @DisplayName("유효한 인증 토큰으로 회원 가입하면 회원이 저장되고 회원 ID가 반환된다.")
    @Test
    void signUp() {
        // given
        String email = "user@example.com";
        String nickName = "닉네임";
        String token = "valid-token";

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:token:" + email)).willReturn(token);
        given(stringRedisTemplate.delete("email:verify:token:" + email)).willReturn(true);

        MemberCreateServiceRequest request = createSignUpRequest(email, nickName, token);

        // when
        MemberCreateResponse response = memberService.signUp(request);

        // then
        assertThat(response.getMemberId()).isNotNull();
        Member savedMember = memberRepository.findById(response.getMemberId()).orElseThrow();
        assertThat(savedMember)
                .extracting(Member::getEmail, Member::getNickName)
                .containsExactly(email, nickName);
    }

    @DisplayName("회원 가입 시 인증 토큰이 이미 소비되었으면(동시성) 예외가 발생한다.")
    @Test
    void signUpWithAlreadyConsumedToken() {
        // given
        String email = "user@example.com";
        String token = "valid-token";

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:token:" + email)).willReturn(token);
        given(stringRedisTemplate.delete("email:verify:token:" + email)).willReturn(false);

        MemberCreateServiceRequest request = createSignUpRequest(email, "닉네임", token);

        // when // then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(EmailDuplicatedException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @DisplayName("만료된 토큰으로 회원 가입하면 예외가 발생한다.")
    @Test
    void signUpWithExpiredToken() {
        // given
        String email = "user@example.com";
        String expiredToken = "expired-token";

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:token:" + email)).willReturn(null);

        MemberCreateServiceRequest request = createSignUpRequest(email, "닉네임", expiredToken);

        // when // then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("이메일 인증이 완료되지 않았습니다.");
    }

    @DisplayName("인증되지 않은 토큰으로 회원 가입하면 예외가 발생한다.")
    @Test
    void signUpWithMismatchedToken() {
        // given
        String email = "user@example.com";
        String savedToken = "saved-token";
        String unValidToken = "un-valid-token";

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email:verify:token:" + email)).willReturn(savedToken);

        MemberCreateServiceRequest request = createSignUpRequest(email, "닉네임", unValidToken);

        // when // then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("이메일 인증이 완료되지 않았습니다.");
    }

    @DisplayName("이미 가입된 이메일로 회원 가입하면 예외가 발생한다.")
    @Test
    void signUpWithDuplicatedEmail() {
        // given
        String savedEmail = "user@example.com";
        memberRepository.save(createMember(savedEmail, "기존닉네임"));

        MemberCreateServiceRequest request = createSignUpRequest(savedEmail, "새닉네임", "valid-token");

        // when // then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(EmailDuplicatedException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");
    }

    @DisplayName("이미 사용 중인 닉네임으로 회원 가입하면 예외가 발생한다.")
    @Test
    void signUpWithDuplicatedNickName() {
        // given
        String savedNickname = "닉네임";
        memberRepository.save(createMember("other@example.com", savedNickname));

        MemberCreateServiceRequest request = createSignUpRequest("user@example.com", savedNickname, "valid-token");

        // when // then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(NicknameDuplicatedException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");
    }

    @DisplayName("이메일과 비밀번호가 일치하면 로그인에 성공하고 회원 정보를 반환한다.")
    @Test
    void login() {
        // given
        String email = "user@example.com";
        String nickName = "닉네임";
        String password = "Passw0rd!@";
        Member savedMember = memberRepository.save(createMember(email, nickName, password));

        // when
        LoginResponse response = memberService.login(email, password);

        // then
        assertThat(response)
                .extracting(LoginResponse::getMemberId, LoginResponse::getNickName)
                .containsExactly(savedMember.getId(), nickName);
    }

    @DisplayName("존재하지 않는 이메일로 로그인하면 예외가 발생한다.")
    @Test
    void loginWithNotExistEmail() {
        // given
        String notExistEmail = "user@example.com";
        String password = "Passw0rd!@";

        // when // then
        assertThatThrownBy(() -> memberService.login(notExistEmail, password))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패하고 예외가 발생한다.")
    @Test
    void loginWithWrongPassword() {
        // given
        String email = "user@example.com";
        String password = "Passw0rd!@";
        String wrongPassword = "WrongPass!@";
        memberRepository.save(createMember(email, "닉네임", password));

        // when // then
        assertThatThrownBy(() -> memberService.login(email, wrongPassword))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    @DisplayName("사용 중이지 않은 닉네임이면 사용 가능(available=true)을 반환한다.")
    @Test
    void checkNicknameAvailable() {
        // given
        String nickName = "사용가능닉네임";

        // when
        NicknameCheckResponse response = memberService.checkNicknameAvailable(nickName);

        // then
        assertThat(response.isAvailable()).isTrue();
    }

    @DisplayName("이미 사용 중인 닉네임이면 사용 불가(available=false)를 반환한다.")
    @Test
    void checkNicknameUnavailable() {
        // given
        String nickName = "닉네임";
        memberRepository.save(createMember("user@example.com", nickName));

        // when
        NicknameCheckResponse response = memberService.checkNicknameAvailable(nickName);

        // then
        assertThat(response.isAvailable()).isFalse();
    }

    @DisplayName("로그인한 회원의 상세 정보를 주소를 포함해 조회한다.")
    @Test
    void getMyInfo() {
        // given
        Member member = memberRepository.save(createMember("user@example.com", "닉네임", "서울시 ...", "101동 202호"));

        // when
        MemberInfoResponse response = memberService.getMyInfo(member.getId());

        // then
        assertThat(response)
                .extracting("memberId", "email", "nickName")
                .containsExactly(member.getId(), "user@example.com", "닉네임");
        assertThat(response.getAddress())
                .extracting("roadAddress", "detailAddress")
                .containsExactly("서울시 ...", "101동 202호");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @DisplayName("닉네임과 주소를 함께 수정하면 회원 정보가 변경된다.")
    @Test
    void updateMyInfo() {
        // given
        String newNickName = "새닉네임";
        String newRoadAddress = "부산시 ...";
        String newDetailAddress = "33동 44호";
        Member member = memberRepository.save(createMember("user@example.com", "닉네임", "서울시 ...", "101동 202호"));
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .nickName(newNickName)
                .address(AddressRequest.builder()
                        .roadAddress(newRoadAddress)
                        .detailAddress(newDetailAddress)
                        .build())
                .build();

        // when
        memberService.updateMyInfo(member.getId(), request);

        // then
        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updated.getNickName()).isEqualTo(newNickName);
        assertThat(updated.getAddress())
                .extracting("roadAddress", "detailAddress")
                .containsExactly(newRoadAddress, newDetailAddress);
    }

    @DisplayName("이미 사용 중인 닉네임으로 수정하면 예외가 발생한다.")
    @Test
    void updateMyInfoWithDuplicatedNickName() {
        // given
        String duplicatedNickName = "사용중닉네임";
        memberRepository.save(createMember("other@example.com", duplicatedNickName));
        Member member = memberRepository.save(createMember("user@example.com", "내닉네임"));
        MemberUpdateRequest request = MemberUpdateRequest.builder()
                .nickName(duplicatedNickName)
                .build();

        // when // then
        assertThatThrownBy(() -> memberService.updateMyInfo(member.getId(), request))
                .isInstanceOf(NicknameDuplicatedException.class)
                .hasMessage("이미 사용 중인 닉네임입니다.");
    }

    @DisplayName("회원을 탈퇴하면 soft delete 처리되어 deleted가 true가 되고 데이터는 유지된다.")
    @Test
    void deleteMember() {
        // given
        Member member = memberRepository.save(createMember("user@example.com", "닉네임"));

        // when
        memberService.deleteMember(member.getId());

        // then
        Member deleted = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
    }

    @DisplayName("존재하지 않는 회원 ID로 탈퇴하면 예외가 발생한다.")
    @Test
    void deleteMemberWithNotExist() {
        // given
        Long notExistId = 999L;

        // when // then
        assertThatThrownBy(() -> memberService.deleteMember(notExistId))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessage("회원을 찾을 수 없습니다.");
    }

    private Member createMember(String email, String nickName) {
        return Member.builder()
                .email(email)
                .nickName(nickName)
                .password("Passw0rd!@")
                .address(Address.of("서울시 ...", "101동 202호"))
                .build();
    }

    private Member createMember(String email, String nickName, String roadAddress, String detailAddress) {
        return Member.builder()
                .email(email)
                .nickName(nickName)
                .password("Passw0rd!@")
                .address(Address.of(roadAddress, detailAddress))
                .build();
    }

    private Member createMember(String email, String nickName, String password) {
        return Member.builder()
                .email(email)
                .nickName(nickName)
                .password(passwordEncoder.encode(password))
                .address(Address.of("서울시 ...", "101동 202호"))
                .build();
    }

    private MemberCreateServiceRequest createSignUpRequest(String email, String nickName, String emailVerifyToken) {
        return MemberCreateServiceRequest.builder()
                .email(email)
                .emailVerifyToken(emailVerifyToken)
                .password("Passw0rd!@")
                .nickName(nickName)
                .roadAddress("서울시 ...")
                .detailAddress("101동 202호")
                .build();
    }
}
