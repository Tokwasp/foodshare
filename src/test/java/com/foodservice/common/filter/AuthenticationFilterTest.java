package com.foodservice.common.filter;

import com.foodservice.common.constant.SessionConst;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter(new ObjectMapper());

    @Test
    @DisplayName("화이트리스트 경로는 세션 없이도 필터를 통과한다")
    void whitelistPathPassesWithoutSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/foods");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("세션이 없으면 401을 반환한다")
    void noSessionReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = response.getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(401);
        assertThat((String) JsonPath.read(body, "$.title")).isEqualTo("UNAUTHORIZED");
        assertThat((String) JsonPath.read(body, "$.detail")).isEqualTo("인증이 필요합니다.");
    }

    @Test
    @DisplayName("세션은 있지만 memberId가 없으면 401을 반환한다")
    void sessionWithoutMemberIdReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.title")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("세션에 memberId가 있으면 필터를 통과한다")
    void validSessionPasses() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, 1L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }
}
