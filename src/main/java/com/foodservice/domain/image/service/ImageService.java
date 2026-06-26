package com.foodservice.domain.image.service;

import com.foodservice.common.exception.image.EmptyFileException;
import com.foodservice.common.exception.image.ExpiredImageNotFoundException;
import com.foodservice.common.exception.image.ImageNotFoundException;
import com.foodservice.common.exception.image.InvalidFileFormatException;
import com.foodservice.domain.image.dto.response.ImageResponse;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.repository.ImageRepository;
import com.foodservice.domain.image.store.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageStorage imageStorage;
    private final ImageRepository imageRepository;

    // 빈 파일/파일명 검증. uploadToStorage 및 소비기한 인식(저장 없이 AI만) 모두에서 공통 사용.
    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileFormatException();
        }
    }

    // 파일 유효성 검사 후 스토리지 업로드만 수행 (DB 저장 없음).
    // FoodFacade에서 @Transactional 시작 전에 호출 — 트랜잭션 밖에서 외부 I/O 처리.
    public ImageUploadResponse uploadToStorage(MultipartFile file) {
        validateImageFile(file);
        return imageStorage.upload(file);
    }

    // 스토리지 업로드 결과를 DB에 저장 (트랜잭션 참여).
    // FoodFacade의 @Transactional 안에서 food 저장 후 호출 — food + image 원자성 보장.
    public Long saveImageMeta(Long foodId, ImageUploadResponse response,
                              String originalName, ImageType imageType) {
        Image image = Image.builder()
                .foodId(foodId)
                .originalName(originalName)
                .storedName(response.getStoredName())
                .imageType(imageType)
                .build();
        return imageRepository.save(image).getImageId();
    }

    public List<ImageResponse> getImages(Long foodId) {
        return imageRepository.findActiveByFoodId(foodId)
                .stream()
                .map(image -> ImageResponse.of(image, imageStorage.generateAccessUrl(image.getStoredName())))
                .toList();
    }

    public void deleteImagesByFoodId(Long foodId) {
        imageRepository.softDeleteByFoodId(foodId);
    }

    // 삭제되지 않은 단건 이미지 조회. 소유권 검증은 호출 측(Facade)에서 food를 통해 수행한다.
    public Image getActiveImage(Long imageId) {
        return imageRepository.findActiveById(imageId)
                .orElseThrow(ImageNotFoundException::new);
    }

    // 해당 음식에 속한 이미지들만 soft delete (다른 음식 이미지/이미 삭제된 것은 무시).
    public void deleteImagesByIds(Long foodId, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        for (Long imageId : imageIds) {
            imageRepository.findActiveById(imageId)
                    .filter(image -> image.getFoodId().equals(foodId))
                    .ifPresent(Image::delete);
        }
    }

    // 소비기한 사진 교체 시: 해당 imageId가 이 음식의 유효한 EXPIRED 이미지인지 검증.
    public void validateExpiredImage(Long imageId, Long foodId) {
        Image image = imageRepository.findActiveById(imageId)
                .orElseThrow(ExpiredImageNotFoundException::new);
        if (!image.getFoodId().equals(foodId) || image.getImageType() != ImageType.EXPIRED) {
            throw new ExpiredImageNotFoundException();
        }
    }

    public Map<Long, String> getThumbnailUrls(List<Long> foodIds) {
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        return imageRepository.findThumbnailCandidatesByFoodIds(foodIds, ImageType.BASIC)
                .stream()
                .collect(Collectors.toMap(
                        Image::getFoodId,
                        image -> imageStorage.generateAccessUrl(image.getStoredName()),
                        (first, second) -> first  // BASIC이 앞에 정렬됨 → 첫 후보 유지(BASIC 없으면 EXPIRED 폴백)
                ));
    }
}
