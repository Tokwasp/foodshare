package com.foodservice.domain.chat.entity;

import com.foodservice.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long roomId;

    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false)
    private Long ownerId;

    @Builder
    private ChatRoom(Long foodId, Long ownerId) {
        this.foodId = foodId;
        this.ownerId = ownerId;
    }
}
