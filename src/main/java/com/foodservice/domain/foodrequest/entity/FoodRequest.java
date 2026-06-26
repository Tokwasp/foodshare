package com.foodservice.domain.foodrequest.entity;

import com.foodservice.common.exception.foodrequest.InvalidRequestStatusException;
import com.foodservice.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "REQUEST_FOOD")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class FoodRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long requestFoodId;

    @Column(nullable = false)
    private Long foodId;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodRequestStatus status = FoodRequestStatus.REQUEST;

    @Builder
    private FoodRequest(Long foodId, Long memberId) {
        this.foodId = foodId;
        this.memberId = memberId;
    }

    public void approve() {
        if (this.status != FoodRequestStatus.REQUEST) {
            throw new InvalidRequestStatusException();
        }
        this.status = FoodRequestStatus.APPROVED;
    }

    public void reject() {
        if (this.status != FoodRequestStatus.REQUEST) {
            throw new InvalidRequestStatusException();
        }
        this.status = FoodRequestStatus.REJECTED;
    }

    public void cancel() {
        if (this.status != FoodRequestStatus.REQUEST) {
            throw new InvalidRequestStatusException();
        }
        this.delete();
    }
}
