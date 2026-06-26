package com.foodservice.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@Profile("!test & !s3-test")
@EnableRedisHttpSession
public class SessionConfig {

    /**
     * 세션 쿠키를 cross-site(프론트 도메인 ↔ API 도메인)로 전송하기 위한 설정.
     *
     * <p>프론트와 API의 도메인이 다르면 기본값 {@code SameSite=Lax} 쿠키는 전송되지 않는다.
     * 따라서 {@code SameSite=None}으로 풀어주고, 이때 브라우저 규칙상 {@code Secure}(HTTPS)가 필수다.
     * 로컬(http) 개발에서는 {@code app.cookie.same-site=Lax}, {@code app.cookie.secure=false}로 덮어쓴다.
     */
    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${app.cookie.same-site:None}") String sameSite,
            @Value("${app.cookie.secure:true}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite(sameSite);
        serializer.setUseSecureCookie(secure);
        return serializer;
    }
}
