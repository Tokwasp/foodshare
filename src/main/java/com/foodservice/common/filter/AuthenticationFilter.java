package com.foodservice.common.filter;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<PermitEntry> WHITELIST = List.of(
            new PermitEntry("POST", "/api/v1/auth/email/send"),
            new PermitEntry("POST", "/api/v1/auth/email/verify"),
            new PermitEntry("POST", "/api/v1/members"),
            new PermitEntry("GET", "/api/v1/members/nickname/check"),
            new PermitEntry("POST", "/api/v1/auth/login"),
            new PermitEntry("GET", "/api/v1/foods"),
            new PermitEntry("GET", "/api/v1/foods/*")
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isWhitelisted(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            writeUnauthorized(request, response);
            return;
        }

        Object memberId = session.getAttribute(SessionConst.LOGIN_MEMBER_ID);
        if (memberId == null) {
            writeUnauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return WHITELIST.stream()
                .anyMatch(entry -> entry.method().equals(method) && PATH_MATCHER.match(entry.pattern(), uri));
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
        problem.setTitle(errorCode.name());
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private record PermitEntry(String method, String pattern) {
    }
}
