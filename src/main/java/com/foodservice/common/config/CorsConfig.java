package com.foodservice.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 브라우저 프론트엔드(다른 Origin)에서의 호출을 허용하기 위한 CORS 설정.
 *
 * <p>이 서비스는 Spring Security 필터체인 없이 커스텀 {@link com.foodservice.common.filter.AuthenticationFilter}
 * ({@code HIGHEST_PRECEDENCE + 100})로 인증한다. 프리플라이트(OPTIONS)는 세션 쿠키 없이 오므로 인증 필터에
 * 먼저 닿으면 401로 막힌다. 그래서 {@link CorsFilter}를 {@code HIGHEST_PRECEDENCE}로 등록해
 * 인증 필터보다 먼저 프리플라이트에 응답·종료하도록 한다.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 세션 쿠키를 cross-site로 주고받아야 하므로 credentials 허용
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setExposedHeaders(corsProperties.exposedHeaders());
        config.setMaxAge(corsProperties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        // 인증 필터(HIGHEST_PRECEDENCE + 100)보다 먼저 프리플라이트를 처리하도록 최우선 순위로 등록
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
