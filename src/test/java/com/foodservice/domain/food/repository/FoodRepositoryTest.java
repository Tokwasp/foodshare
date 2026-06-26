package com.foodservice.domain.food.repository;

import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.foodservice.domain.food.entity.ExStatus.COMPLETED;
import static com.foodservice.domain.food.entity.ExStatus.EXPIRED;
import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@ActiveProfiles("test")
class FoodRepositoryTest {

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Test
    @DisplayName("IN_PROGRESS 상태이고 삭제되지 않은 음식만 조회된다.")
    void findActiveByMemberIdAndStatus_returnsOnlyActiveAndNotDeleted() {
        // given
        Long memberId = 1L;
        saveFood(memberId, IN_PROGRESS);
        saveFood(memberId, IN_PROGRESS);
        saveFood(memberId, COMPLETED);      // 다른 상태 - 조회 제외

        // when
        List<Food> result = foodRepository.findActiveByMemberIdAndStatus(memberId, IN_PROGRESS);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(f -> f.getExStatus() == IN_PROGRESS);
    }

    @Test
    @DisplayName("다른 memberId의 음식은 조회에 포함되지 않는다.")
    void findActiveByMemberIdAndStatus_doesNotReturnOtherMemberFoods() {
        // given
        saveFood(1L, IN_PROGRESS);
        saveFood(2L, IN_PROGRESS);

        // when
        List<Food> result = foodRepository.findActiveByMemberIdAndStatus(1L, IN_PROGRESS);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("만료 시각이 지난 IN_PROGRESS 음식만 EXPIRED로 변경된다.")
    void bulkExpire_updatesOnlyExpiredInProgress() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Food expired1 = saveFoodWithExpired(IN_PROGRESS, now.minusHours(1));  // 만료 - 변경 대상
        Food expired2 = saveFoodWithExpired(IN_PROGRESS, now.minusHours(1));  // 만료 - 변경 대상
        Food valid    = saveFoodWithExpired(IN_PROGRESS, now.plusHours(1));   // 유효 - 제외
        Food other    = saveFoodWithExpired(COMPLETED,   now.minusHours(1));  // 다른 상태 - 제외

        // when
        int updated = foodRepository.bulkExpire(now);

        // then
        assertThat(updated).isEqualTo(2);
        assertThat(foodRepository.findById(expired1.getFoodId()).get().getExStatus()).isEqualTo(EXPIRED);
        assertThat(foodRepository.findById(expired2.getFoodId()).get().getExStatus()).isEqualTo(EXPIRED);
        assertThat(foodRepository.findById(valid.getFoodId()).get().getExStatus()).isEqualTo(IN_PROGRESS);
        assertThat(foodRepository.findById(other.getFoodId()).get().getExStatus()).isEqualTo(COMPLETED);
    }

    @Test
    @DisplayName("deleted=true인 음식은 만료 벌크 업데이트에서 제외된다.")
    void bulkExpire_excludesDeletedFoods() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Food deletedFood = saveFoodWithExpired(IN_PROGRESS, now.minusHours(1));
        ReflectionTestUtils.setField(deletedFood, "deleted", true);
        foodRepository.save(deletedFood);

        // when
        int updated = foodRepository.bulkExpire(now);

        // then
        assertThat(updated).isZero();
        assertThat(foodRepository.findById(deletedFood.getFoodId()).get().getExStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    @DisplayName("만료 대상이 없으면 0을 반환한다.")
    void bulkExpire_returnsZeroWhenNothingToExpire() {
        // given
        LocalDateTime now = LocalDateTime.now();
        saveFoodWithExpired(IN_PROGRESS, now.plusHours(1));  // 아직 유효

        // when
        int updated = foodRepository.bulkExpire(now);

        // then
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("IN_PROGRESS 음식 목록이 페이징되어 반환된다.")
    void findPageByStatus_returnsPaged() {
        // given
        for (int i = 0; i < 5; i++) {
            saveFood(1L, IN_PROGRESS);
        }
        saveFood(1L, COMPLETED);  // 다른 상태 - 제외

        Pageable pageable = PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "foodId"));

        // when
        Page<Food> result = foodRepository.findPageByStatus(IN_PROGRESS, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(f -> f.getExStatus() == IN_PROGRESS);
    }

    @Test
    @DisplayName("deleted=true인 음식은 목록 조회에서 제외된다.")
    void findPageByStatus_excludesDeleted() {
        // given
        Food active = saveFood(1L, IN_PROGRESS);
        Food deleted = saveFood(1L, IN_PROGRESS);
        ReflectionTestUtils.setField(deleted, "deleted", true);
        foodRepository.save(deleted);

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Food> result = foodRepository.findPageByStatus(IN_PROGRESS, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getFoodId()).isEqualTo(active.getFoodId());
    }

    @Test
    @DisplayName("삭제되지 않은 음식을 foodId로 단건 조회한다.")
    void findActiveById_returnsFood() {
        // given
        Food saved = saveFood(1L, IN_PROGRESS);

        // when
        Optional<Food> result = foodRepository.findActiveById(saved.getFoodId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getFoodId()).isEqualTo(saved.getFoodId());
    }

    @Test
    @DisplayName("deleted=true인 음식은 단건 조회에서 제외된다.")
    void findActiveById_excludesDeleted() {
        // given
        Food deleted = saveFood(1L, IN_PROGRESS);
        ReflectionTestUtils.setField(deleted, "deleted", true);
        foodRepository.save(deleted);

        // when
        Optional<Food> result = foodRepository.findActiveById(deleted.getFoodId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("food.delete() 후 softDeleteByFoodId() 호출 시 Food의 deleted=true가 DB에 정상 반영된다.")
    void deleteFood_flushesBeforeBulkUpdate() {
        // given
        Food food = saveFood(1L, IN_PROGRESS);
        Image image = imageRepository.save(Image.builder()
                .foodId(food.getFoodId())
                .originalName("img.png")
                .storedName("stored-img.png")
                .imageType(ImageType.BASIC)
                .build());

        // when — FoodFacade.deleteFood() 실제 실행 흐름 재현
        food.delete();                                                  // dirty
        imageRepository.softDeleteByFoodId(food.getFoodId());          // flushAutomatically → food flush 후 image bulk update

        // then
        Food reloaded = foodRepository.findById(food.getFoodId()).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();

        Image reloadedImage = imageRepository.findById(image.getImageId()).orElseThrow();
        assertThat(reloadedImage.isDeleted()).isTrue();
    }

    private Food saveFood(Long memberId, ExStatus exStatus) {
        return foodRepository.save(Food.builder()
                .memberId(memberId)
                .foodName("테스트 음식")
                .details("상세 내용")
                .capacity(3)
                .exStatus(exStatus)
                .expired(LocalDateTime.now().plusDays(7))
                .build());
    }

    private Food saveFoodWithExpired(ExStatus exStatus, LocalDateTime expiredAt) {
        return foodRepository.save(Food.builder()
                .memberId(1L)
                .foodName("테스트 음식")
                .details("상세 내용")
                .capacity(3)
                .exStatus(exStatus)
                .expired(expiredAt)
                .build());
    }
}
