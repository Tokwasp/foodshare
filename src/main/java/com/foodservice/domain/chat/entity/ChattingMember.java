package com.foodservice.domain.chat.entity;

import com.foodservice.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "member_id"}))
public class ChattingMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long chattingMemberId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false)
    private Long lastReadMessageId;

    // 안읽음 수 비정규화 캐시 — 메시지 수신 시 +1(chat-realtime), 읽음 시 0
    @Column(nullable = false)
    private Long unreadCount;

    @Builder
    private ChattingMember(Long roomId, Long memberId, ChatRole role, Long lastReadMessageId, Long unreadCount) {
        this.roomId = roomId;
        this.memberId = memberId;
        this.role = role;
        this.lastReadMessageId = lastReadMessageId;
        this.unreadCount = unreadCount != null ? unreadCount : 0L;
    }

    public void updateLastReadMessageId(Long lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    public void resetUnreadCount() {
        this.unreadCount = 0L;
    }

    public void increaseUnreadCount() {
        this.unreadCount += 1;
    }
}
