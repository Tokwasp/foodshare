package com.foodservice.domain.food.repository;

import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FoodRepository extends JpaRepository<Food, Long> {

    // PESSIMISTIC_WRITE: 행 자체에 FOR UPDATE 락을 걸어 TOCTOU 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Food f WHERE f.memberId = :memberId AND f.exStatus = :exStatus AND f.deleted = false")
    List<Food> findActiveByMemberIdAndStatus(@Param("memberId") Long memberId, @Param("exStatus") ExStatus exStatus);

    // 단건 조회 (삭제되지 않은 음식)
    @Query("SELECT f FROM Food f WHERE f.foodId = :foodId AND f.deleted = false")
    Optional<Food> findActiveById(@Param("foodId") Long foodId);

    // 회원이 등록한 물품 목록 (상태 무관, 삭제 제외, 최신순)
    @Query("SELECT f FROM Food f WHERE f.memberId = :memberId AND f.deleted = false ORDER BY f.foodId DESC")
    List<Food> findActiveByMemberId(@Param("memberId") Long memberId);

    // 특정 상태 음식 목록 (페이징)
    @Query("SELECT f FROM Food f WHERE f.exStatus = :exStatus AND f.deleted = false")
    Page<Food> findPageByStatus(@Param("exStatus") ExStatus exStatus, Pageable pageable);

    // 전체 상태 음식 목록 (삭제 제외, 페이징) — status 미지정 조회용
    @Query("SELECT f FROM Food f WHERE f.deleted = false")
    Page<Food> findActivePage(Pageable pageable);

    // 만료 대상 벌크 UPDATE — 1차 캐시 불일치 방지를 위해 clearAutomatically = true
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Food f SET f.exStatus = com.foodservice.domain.food.entity.ExStatus.EXPIRED " +
           "WHERE f.exStatus = com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS " +
           "AND f.expired < :now AND f.deleted = false")
    int bulkExpire(@Param("now") LocalDateTime now);
}
