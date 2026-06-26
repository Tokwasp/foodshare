package com.foodservice.common.config;

import com.foodservice.common.constant.SessionConst;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 핸드셰이크 attributes의 {@link SessionConst#LOGIN_MEMBER_ID}로 Principal(name=memberId)을 만든다.
 * 이 Principal 이름이 {@code convertAndSendToUser(memberId, ...)}의 대상 식별자가 된다(둘 다 String).
 * memberId가 없으면 null을 반환하나, 미인증 거절은 {@link SessionHandshakeAuthInterceptor}가 401로 처리한다.
 */
public class MemberHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object memberId = attributes.get(SessionConst.LOGIN_MEMBER_ID);
        if (memberId == null) {
            return null;
        }
        String name = String.valueOf(memberId);
        return () -> name;
    }
}
