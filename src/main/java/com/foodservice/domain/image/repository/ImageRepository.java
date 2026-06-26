package com.foodservice.domain.image.repository;

import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    // 단건 조회 (삭제되지 않은 이미지)
    @Query("SELECT i FROM Image i WHERE i.imageId = :imageId AND i.deleted = false")
    Optional<Image> findActiveById(@Param("imageId") Long imageId);

    // 음식 목록 썸네일 후보 일괄 조회 (N+1 방지, soft-delete 제외).
    // preferredType(BASIC)을 음식별로 앞에 정렬해, 후보가 없으면 나머지 타입(EXPIRED)으로 폴백되도록 한다.
    // 호출 측에서 음식별 첫 후보를 썸네일로 선택한다.
    @Query("SELECT i FROM Image i WHERE i.foodId IN :foodIds AND i.deleted = false " +
            "ORDER BY CASE WHEN i.imageType = :preferredType THEN 0 ELSE 1 END, i.imageId ASC")
    List<Image> findThumbnailCandidatesByFoodIds(@Param("foodIds") List<Long> foodIds,
                                                 @Param("preferredType") ImageType preferredType);

    // 특정 음식의 이미지 전체 조회 (등록 순서 보장, soft-delete 제외)
    @Query("SELECT i FROM Image i WHERE i.foodId = :foodId AND i.deleted = false ORDER BY i.imageId ASC")
    List<Image> findActiveByFoodId(@Param("foodId") Long foodId);

    // 특정 음식의 이미지 전체 soft-delete
    // flushAutomatically: 벌크 UPDATE 전 dirty 상태(food.delete() 등)를 강제 flush
    // clearAutomatically: UPDATE 후 영속성 컨텍스트 초기화로 stale 엔티티 방지
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Image i SET i.deleted = true WHERE i.foodId = :foodId")
    int softDeleteByFoodId(@Param("foodId") Long foodId);
}
