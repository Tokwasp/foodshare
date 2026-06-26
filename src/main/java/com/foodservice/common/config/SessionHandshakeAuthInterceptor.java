package com.foodservice.common.config;

import com.foodservice.common.constant.SessionConst;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 운영용 WebSocket 핸드셰이크 인증. {@code HttpSessionHandshakeInterceptor}가 복사해 둔
 * 세션 속성 {@link SessionConst#LOGIN_MEMBER_ID}가 없으면 401로 핸드셰이크를 거절한다.
 *
 * <p>테스트는 {@code @Primary} {@link HandshakeInterceptor} 빈으로 이를 대체해
 * {@code ?memberId=} 쿼리 파라미터 기반 인증을 주입한다(실제 Redis 세션이 없는 환경 대비).
 */
@Component
public class SessionHandshakeAuthInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (attributes.get(SessionConst.LOGIN_MEMBER_ID) == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
