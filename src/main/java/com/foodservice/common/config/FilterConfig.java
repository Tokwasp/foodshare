package com.foodservice.common.config;

import com.foodservice.common.filter.AuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthenticationFilter(objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        // SessionRepositoryFilter(MIN_VALUE+50)가 request를 Redis 세션으로 감싼 뒤에
        // 인증 필터가 돌아야 getSession(false)가 Redis 세션을 읽는다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
