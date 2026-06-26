package com.foodservice.domain.image.repository;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ImageRepository imageRepository;

    @Test
    @DisplayName("findThumbnailCandidatesByFoodIds — 음식별 BASIC을 우선 후보로, 없으면 EXPIRED를 후보로(BASIC이 앞에) 반환하며 deleted는 제외한다.")
    void findThumbnailCandidates_prefersBasicThenFallbackExpired() {
        // given
        Long foodWithBasic = 1L;   // BASIC + EXPIRED 보유
        saveImage(foodWithBasic, ImageType.EXPIRED, "exp-with");
        saveImage(foodWithBasic, ImageType.BASIC, "basic-with");

        Long foodOnlyExpired = 2L;  // EXPIRED만 활성, BASIC은 삭제됨 → 폴백 대상
        saveImage(foodOnlyExpired, ImageType.EXPIRED, "exp-only");
        Image deletedBasic = saveImage(foodOnlyExpired, ImageType.BASIC, "basic-deleted");
        ReflectionTestUtils.setField(deletedBasic, "deleted", true);
        imageRepository.save(deletedBasic);

        // when
        List<Image> result = imageRepository.findThumbnailCandidatesByFoodIds(
                List.of(foodWithBasic, foodOnlyExpired), ImageType.BASIC);

        // then — 음식별 첫 후보(=썸네일)가 BASIC 우선, 없으면 EXPIRED
        Map<Long, ImageType> firstTypeByFood = new LinkedHashMap<>();
        for (Image image : result) {
            firstTypeByFood.putIfAbsent(image.getFoodId(), image.getImageType());
        }
        assertThat(firstTypeByFood.get(foodWithBasic)).isEqualTo(ImageType.BASIC);
        assertThat(firstTypeByFood.get(foodOnlyExpired)).isEqualTo(ImageType.EXPIRED);
        assertThat(result).noneMatch(image -> image.getStoredName().equals("basic-deleted"));
    }

    private Image saveImage(Long foodId, ImageType imageType, String storedName) {
        return imageRepository.save(Image.builder()
                .foodId(foodId)
                .originalName(storedName + ".png")
                .storedName(storedName)
                .imageType(imageType)
                .build());
    }
}
