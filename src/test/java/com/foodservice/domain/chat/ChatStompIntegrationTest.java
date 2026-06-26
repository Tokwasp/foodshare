package com.foodservice.domain.chat;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChatRoom;
import com.foodservice.domain.chat.entity.ChatMessage;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatMessageRepository;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatStompIntegrationTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChattingMemberRepository chattingMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @AfterEach
    void tearDown() {
        chatMessageRepository.deleteAllInBatch();
        chattingMemberRepository.deleteAllInBatch();
        chatRoomRepository.deleteAllInBatch();
        foodRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("발행하면 수신자의 /user/queue/messages로 메시지가 전달되고, ChatMessage가 DB에 저장되며 수신자 안읽음 수가 증가한다.")
    void publishDeliversToRecipientAndPersists() throws Exception {
        // given
        Long ownerId = saveMember("등록자");
        Long requester = saveMember("신청자");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        ChattingMember ownerMember = saveChattingMember(roomId, ownerId, ChatRole.OWNER);
        saveChattingMember(roomId, requester, ChatRole.MEMBER);

        BlockingQueue<String> received = new LinkedBlockingDeque<>();
        StompSession ownerSession = connect(ownerId);
        ownerSession.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });

        StompSession requesterSession = connect(requester);

        // when
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/pub/chat/rooms/" + roomId);
        sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
        requesterSession.send(sendHeaders, "{\"content\":\"네, 가능합니다!\"}".getBytes(StandardCharsets.UTF_8));

        // then
        String payload = received.poll(3, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("CHAT_MESSAGE");
        assertThat(payload).contains("네, 가능합니다!");
        assertThat(payload).contains("\"senderId\":" + requester);
        assertThat(payload).contains("\"roomId\":" + roomId);

        List<ChatMessage> messages = chatMessageRepository.findAll();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("네, 가능합니다!");
        assertThat(messages.get(0).getSenderId()).isEqualTo(requester);

        ChattingMember owner = chattingMemberRepository.findById(ownerMember.getChattingMemberId()).orElseThrow();
        assertThat(owner.getUnreadCount()).isEqualTo(1L);

        ownerSession.disconnect();
        requesterSession.disconnect();
    }

    @Test
    @DisplayName("미인증(memberId 없음) 핸드셰이크는 401로 거절되어 연결이 실패한다.")
    void handshakeWithoutMemberIdIsRejected() {
        // when // then
        assertThatThrownBy(this::connectWithoutMember)
                .isInstanceOf(Exception.class);
    }

    private StompSession connect(Long memberId) throws Exception {
        return openSession("ws://localhost:" + port + "/ws?memberId=" + memberId);
    }

    private StompSession connectWithoutMember() throws Exception {
        return openSession("ws://localhost:" + port + "/ws");
    }

    private StompSession openSession(String url) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new SimpleMessageConverter());
        return stompClient.connectAsync(url, new WebSocketHttpHeaders(), new StompHeaders(),
                new StompSessionHandlerAdapter() {
                }).get(3, TimeUnit.SECONDS);
    }

    private Long saveMember(String nickName) {
        return memberRepository.save(Member.builder()
                .nickName(nickName)
                .email(nickName + "@example.com")
                .password("password")
                .build()).getId();
    }

    private Long saveFood(Long ownerId, String foodName) {
        return foodRepository.save(Food.builder()
                .memberId(ownerId)
                .foodName(foodName)
                .details("상세 내용")
                .capacity(3)
                .exStatus(IN_PROGRESS)
                .expired(LocalDateTime.now().plusDays(7))
                .build()).getFoodId();
    }

    private Long saveRoom(Long foodId, Long ownerId) {
        return chatRoomRepository.save(ChatRoom.builder()
                .foodId(foodId)
                .ownerId(ownerId)
                .build()).getRoomId();
    }

    private ChattingMember saveChattingMember(Long roomId, Long memberId, ChatRole role) {
        return chattingMemberRepository.save(ChattingMember.builder()
                .roomId(roomId)
                .memberId(memberId)
                .role(role)
                .lastReadMessageId(0L)
                .unreadCount(0L)
                .build());
    }

    /**
     * 테스트 전용 인증 출처 교체: 세션 쿠키(Spring Session/Redis) 대신 {@code ?memberId=} 쿼리 파라미터를
     * 읽어 핸드셰이크 {@code attributes}에 {@link SessionConst#LOGIN_MEMBER_ID}로 심는다.
     * 운영의 실제 {@code MemberHandshakeHandler}가 이 attributes를 읽어 Principal=memberId를 설정하므로
     * 브로커·핸들러·메시지 매핑·서비스 경로는 실배선을 그대로 검증한다.
     * memberId가 없으면 401 거절 — 운영 인증 인터셉터와 동일한 거절 의미.
     *
     * <p>운영 {@code WebSocketConfig}는 인증용 {@link HandshakeInterceptor}를 빈으로 주입받아 등록해야
     * 이 @Primary 빈으로 대체된다.
     */
    @TestConfiguration
    @Profile("test")
    static class TestAuthInterceptorConfig {

        @Bean
        @Primary
        HandshakeInterceptor testAuthHandshakeInterceptor() {
            return new HandshakeInterceptor() {
                @Override
                public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
                    String memberId = UriComponentsBuilder.fromUri(request.getURI())
                            .build().getQueryParams().getFirst("memberId");
                    if (memberId == null) {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                    attributes.put(SessionConst.LOGIN_MEMBER_ID, Long.valueOf(memberId));
                    return true;
                }

                @Override
                public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                          WebSocketHandler wsHandler, Exception exception) {
                }
            };
        }
    }
}
