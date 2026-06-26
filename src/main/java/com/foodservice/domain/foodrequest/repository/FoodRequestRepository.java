package com.foodservice.domain.foodrequest.repository;

import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

@Repository
public interface FoodRequestRepository extends JpaRepository<FoodRequest, Long> {

    // deleted=false 조건 의도적 추가: 향후 신청 철회(soft-delete) 기능 도입 시 철회된 신청은 재신청 가능하도록 허용
    @Query("SELECT COUNT(fr) > 0 FROM FoodRequest fr WHERE fr.foodId = :foodId AND fr.memberId = :memberId AND fr.status = :status AND fr.deleted = false")
    boolean existsDuplicateRequest(@Param("foodId") Long foodId, @Param("memberId") Long memberId, @Param("status") FoodRequestStatus status);

    @Query("SELECT fr FROM FoodRequest fr WHERE fr.requestFoodId = :requestFoodId AND fr.deleted = false")
    Optional<FoodRequest> findActiveById(@Param("requestFoodId") Long requestFoodId);

    // 회원이 보낸 요청 목록 (철회 제외, 최신순)
    @Query("SELECT fr FROM FoodRequest fr WHERE fr.memberId = :memberId AND fr.deleted = false ORDER BY fr.requestFoodId DESC")
    List<FoodRequest> findActiveByMemberId(@Param("memberId") Long memberId);

    // 재신청 허용 정책으로 동일 (foodId, memberId)에 deleted=false 행이 복수 존재할 수 있음
    // → 최신 신청 1건만 반환하도록 ORDER BY DESC + Pageable(limit 1) 적용
    @Query("SELECT fr FROM FoodRequest fr WHERE fr.foodId = :foodId AND fr.memberId = :memberId AND fr.deleted = false ORDER BY fr.requestFoodId DESC")
    List<FoodRequest> findActiveByFoodIdAndMemberId(@Param("foodId") Long foodId, @Param("memberId") Long memberId, Pageable pageable);

    // 등록자가 받은 신청 목록 조회 - 전체 상태(REQUEST/APPROVED/REJECTED) 반환 (히스토리 관리 목적)
    // TODO: 신청 건수가 많아질 경우 Pageable 도입 검토
    @Query("SELECT fr FROM FoodRequest fr WHERE fr.foodId = :foodId AND fr.deleted = false ORDER BY fr.requestFoodId ASC")
    List<FoodRequest> findActiveByFoodId(@Param("foodId") Long foodId);

    // 만료된 음식(EXPIRED)에 걸린 REQUEST 상태 신청 일괄 거절
    // 같은 트랜잭션 내 bulkExpire() 실행 후 호출되므로 서브쿼리로 EXPIRED 음식 확인 가능
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FoodRequest fr SET fr.status = 'REJECTED' " +
           "WHERE fr.status = 'REQUEST' " +
           "AND fr.foodId IN (SELECT f.foodId FROM Food f WHERE f.exStatus = 'EXPIRED' AND f.deleted = false)")
    int rejectPendingForExpiredFoods();

    // 정원 도달(COMPLETED)된 음식의 잔여 REQUEST 상태 신청 일괄 거절
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FoodRequest fr SET fr.status = 'REJECTED' " +
           "WHERE fr.status = 'REQUEST' AND fr.foodId = :foodId")
    int rejectPendingByFoodId(@Param("foodId") Long foodId);
}
