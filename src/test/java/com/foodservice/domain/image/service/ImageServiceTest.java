package com.foodservice.domain.image.service;

import com.foodservice.common.exception.image.EmptyFileException;
import com.foodservice.common.exception.image.InvalidFileFormatException;
import com.foodservice.domain.image.dto.response.ImageResponse;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.repository.ImageRepository;
import com.foodservice.domain.image.store.ImageStorage;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.foodservice.domain.image.ImageFixture.createMultipartFile;
import static com.foodservice.domain.image.ImageFixture.createUploadResponse;
import static com.foodservice.domain.image.entity.ImageType.BASIC;
import static com.foodservice.domain.image.entity.ImageType.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private ImageRepository imageRepository;

    @InjectMocks
    private ImageService imageService;

    @Test
    @DisplayName("uploadToStorage — 유효한 파일이면 스토리지에 업로드하고 응답을 반환한다.")
    void uploadToStorage_success() {
        // given
        MockMultipartFile mockFile = createMultipartFile();
        ImageUploadResponse mockResponse = createUploadResponse(mockFile);
        given(imageStorage.upload(mockFile)).willReturn(mockResponse);

        // when
        ImageUploadResponse result = imageService.uploadToStorage(mockFile);

        // then
        verify(imageStorage, times(1)).upload(mockFile);
        assertThat(result.getAccessUrl()).isEqualTo(mockResponse.getAccessUrl());
        assertThat(result.getStoredName()).isEqualTo(mockResponse.getStoredName());
    }

    @Test
    @DisplayName("uploadToStorage — 파일이 비어 있으면 EmptyFileException이 발생한다.")
    void uploadToStorage_fail_emptyFile() {
        // given
        MockMultipartFile empty = new MockMultipartFile("file", "", "image/png", new byte[0]);

        // when & then
        Assertions.assertThatThrownBy(() -> imageService.uploadToStorage(empty))
                .isInstanceOf(EmptyFileException.class);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("uploadToStorage — 파일 이름이 null이면 InvalidFileFormatException이 발생한다.")
    void uploadToStorage_fail_invalidFilename() {
        // given
        MockMultipartFile nullNameFile = new MockMultipartFile("file", null, "image/png", "data".getBytes());

        // when & then
        Assertions.assertThatThrownBy(() -> imageService.uploadToStorage(nullNameFile))
                .isInstanceOf(InvalidFileFormatException.class);
        then(imageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("saveImageMeta — 스토리지 응답으로 Image 엔티티를 DB에 저장한다.")
    void saveImageMeta_success() {
        // given
        Long foodId = 100L;
        MockMultipartFile mockFile = createMultipartFile();
        ImageUploadResponse mockResponse = createUploadResponse(mockFile);
        given(imageRepository.save(any(Image.class))).willAnswer(returnsFirstArg());

        // when
        imageService.saveImageMeta(foodId, mockResponse, "test-image.png", BASIC);

        // then
        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageRepository, times(1)).save(captor.capture());

        Image saved = captor.getValue();
        assertThat(saved.getFoodId()).isEqualTo(foodId);
        assertThat(saved.getOriginalName()).isEqualTo("test-image.png");
        assertThat(saved.getStoredName()).isEqualTo(mockResponse.getStoredName());
        assertThat(saved.getImageType()).isEqualTo(BASIC);
    }

    @Test
    @DisplayName("getImages — 활성 이미지를 imageId/accessUrl/imageType 구조로 반환한다.")
    void getImages_success() {
        // given
        Long foodId = 100L;
        Image basic = Image.builder()
                .foodId(foodId).originalName("a.png").storedName("stored-basic").imageType(BASIC).build();
        Image expired = Image.builder()
                .foodId(foodId).originalName("e.png").storedName("stored-expired").imageType(EXPIRED).build();
        ReflectionTestUtils.setField(basic, "imageId", 10L);
        ReflectionTestUtils.setField(expired, "imageId", 12L);
        given(imageRepository.findActiveByFoodId(foodId)).willReturn(List.of(basic, expired));
        given(imageStorage.generateAccessUrl("stored-basic")).willReturn("https://cdn/basic.png");
        given(imageStorage.generateAccessUrl("stored-expired")).willReturn("https://cdn/exp.png");

        // when
        List<ImageResponse> result = imageService.getImages(foodId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getImageId()).isEqualTo(10L);
        assertThat(result.get(0).getAccessUrl()).isEqualTo("https://cdn/basic.png");
        assertThat(result.get(0).getImageType()).isEqualTo(BASIC);
        assertThat(result.get(1).getImageId()).isEqualTo(12L);
        assertThat(result.get(1).getImageType()).isEqualTo(EXPIRED);
    }

    @Test
    @DisplayName("getThumbnailUrls — 음식별 첫 후보(BASIC 우선, 없으면 EXPIRED)를 썸네일 URL로 매핑한다.")
    void getThumbnailUrls_prefersBasic_fallbackExpired() {
        // given
        Long foodA = 1L;  // BASIC 보유
        Long foodB = 2L;  // EXPIRED만 보유 → 폴백
        Image basicA = Image.builder().foodId(foodA).originalName("a").storedName("a-basic").imageType(BASIC).build();
        Image expiredB = Image.builder().foodId(foodB).originalName("b").storedName("b-exp").imageType(EXPIRED).build();
        // 리포지토리는 음식별 BASIC이 앞에 오도록 정렬된 후보를 반환한다(foodA=BASIC, foodB=EXPIRED 폴백)
        given(imageRepository.findThumbnailCandidatesByFoodIds(List.of(foodA, foodB), BASIC))
                .willReturn(List.of(basicA, expiredB));
        given(imageStorage.generateAccessUrl("a-basic")).willReturn("https://cdn/a.png");
        given(imageStorage.generateAccessUrl("b-exp")).willReturn("https://cdn/b.png");

        // when
        Map<Long, String> result = imageService.getThumbnailUrls(List.of(foodA, foodB));

        // then — foodA는 BASIC, foodB는 EXPIRED 폴백
        assertThat(result).containsEntry(foodA, "https://cdn/a.png");
        assertThat(result).containsEntry(foodB, "https://cdn/b.png");
    }

    @Test
    @DisplayName("getThumbnailUrls — foodIds가 비어 있으면 빈 맵을 반환하고 리포지토리를 호출하지 않는다.")
    void getThumbnailUrls_emptyFoodIds() {
        // when
        Map<Long, String> result = imageService.getThumbnailUrls(List.of());

        // then
        assertThat(result).isEmpty();
        then(imageRepository).shouldHaveNoInteractions();
    }
}
