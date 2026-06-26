package com.foodservice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 프론트엔드(브라우저)에서 호출하는 CORS 정책 값.
 *
 * <p>세션 쿠키 기반 인증이라 {@code allowCredentials=true}가 필요하고, 이 경우 {@code Origin}에
 * 와일드카드 {@code "*"}를 쓸 수 없다. 그래서 정확한 도메인 + 패턴({@code allowedOriginPatterns})으로
 * 화이트리스트를 구성한다.
 *
 * @param allowedOriginPatterns 허용 Origin 패턴 (예: 운영 도메인, Vercel 프리뷰, 로컬 개발 서버)
 * @param allowedMethods        허용 HTTP 메서드
 * @param allowedHeaders        허용 요청 헤더
 * @param exposedHeaders        브라우저 JS에서 읽도록 노출할 응답 헤더
 * @param maxAgeSeconds         프리플라이트(OPTIONS) 캐시 시간(초)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @DefaultValue({
                "https://foodsharedservice-fe.vercel.app",
                "https://*.vercel.app",
                "http://localhost:3000",
                "http://localhost:5173"
        })
        List<String> allowedOriginPatterns,

        @DefaultValue({"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
        List<String> allowedMethods,

        @DefaultValue("*")
        List<String> allowedHeaders,

        @DefaultValue({"Location"})
        List<String> exposedHeaders,

        @DefaultValue("3600")
        long maxAgeSeconds
) {
}
