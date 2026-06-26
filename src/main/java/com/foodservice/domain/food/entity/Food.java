package com.foodservice.domain.food.entity;

import com.foodservice.common.exception.food.CapacityTooSmallException;
import com.foodservice.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Food extends BaseEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long foodId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String foodName;

    @Column(nullable = false)
    private String details;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer approvedCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExStatus exStatus = IN_PROGRESS;

    @Column(nullable = false)
    private LocalDateTime expired;

    @Version
    private Long version;

    // 지역 필터링용 (행정구역 문자열, 예: "서울시 강남구")
    // TODO: 위치 기반 정렬 기능 추가 시 활용
    @Column
    private String region;

    public void expire() {
        this.exStatus = ExStatus.EXPIRED;
    }

    // 이름/정원/상세 수정. 정원은 현재 승인 인원보다 작게 변경할 수 없다.
    public void update(String foodName, Integer capacity, String details) {
        if (capacity < this.approvedCount) {
            throw new CapacityTooSmallException();
        }
        this.foodName = foodName;
        this.capacity = capacity;
        this.details = details;
    }

    public void incrementApprovedCount() {
        this.approvedCount++;
        if (this.approvedCount >= this.capacity) {
            this.exStatus = ExStatus.COMPLETED;
        }
    }

    @Builder
    private Food(Long memberId, String foodName, String details, Integer capacity, Integer approvedCount, ExStatus exStatus, LocalDateTime expired, String region) {
        this.memberId = memberId;
        this.foodName = foodName;
        this.details = details;
        this.capacity = capacity;
        this.approvedCount = approvedCount != null ? approvedCount : 0;
        this.exStatus = exStatus != null ? exStatus : IN_PROGRESS;
        this.expired = expired;
        this.region = region;
    }
}
