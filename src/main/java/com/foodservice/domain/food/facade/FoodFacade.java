package com.foodservice.domain.food.facade;

import com.foodservice.common.exception.food.ExpirationApiException;
import com.foodservice.common.exception.food.ExpiredImageRequiredException;
import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodNotAvailableException;
import com.foodservice.common.exception.foodrequest.FoodRequestMismatchException;
import com.foodservice.common.exception.image.ImageForbiddenException;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.common.response.PageResponse;
import com.foodservice.domain.food.client.ExpirationApiClient;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.food.dto.FoodDetailResponse;
import com.foodservice.domain.food.dto.FoodListResponse;
import com.foodservice.domain.food.dto.FoodUpdateRequest;
import com.foodservice.domain.foodrequest.dto.FoodRequestListResponse;
import com.foodservice.domain.foodrequest.dto.FoodRequestResponse;
import com.foodservice.domain.foodrequest.dto.MyFoodRequestResponse;
import com.foodservice.domain.foodrequest.dto.MySentRequestResponse;
import com.foodservice.domain.foodrequest.dto.RequestApproveResponse;
import com.foodservice.domain.foodrequest.dto.RequestRejectResponse;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.service.FoodRequestService;
import com.foodservice.domain.food.service.FoodService;
import com.foodservice.domain.member.service.MemberService;
import com.foodservice.domain.image.dto.response.ImageResponse;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class FoodFacade {

    private final FoodService foodService;
    private final FoodRequestService foodRequestService;
    private final ImageService imageService;
    private final MemberService memberService;
    private final ExpirationApiClient expirationApiClient;
    private final TransactionTemplate transactionTemplate;

    public LocalDate recognizeExpirationDate(MultipartFile file) {
        imageService.validateImageFile(file); // 빈 파일/파일명 검증 (uploadToStorage와 동일 규칙)
        return expirationApiClient.fetchExpirationDate(file);
    }

    // 한 요청에 소비기한 사진(EXPIRED) + 물품 사진들(BASIC)을 함께 받아 등록한다.
    // 소비기한 값은 expired-date 단계에서 받은 request.expired를 신뢰(범위만 검증)하며, AI를 재실행하지 않는다.
    public Long registerFood(Long memberId, FoodCreateRequest request,
                             MultipartFile expiredImage, List<MultipartFile> images) {
        if (expiredImage == null || expiredImage.isEmpty()) {
            throw new ExpiredImageRequiredException();
        }
        validateExpirationDate(request.getExpired());

        // 외부 I/O(스토리지 업로드)는 트랜잭션 밖에서 처리
        String expiredOriginalName = expiredImage.getOriginalFilename();
        ImageUploadResponse expiredUpload = imageService.uploadToStorage(expiredImage);

        List<UploadedImage> basicUploads = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                basicUploads.add(new UploadedImage(imageService.uploadToStorage(image), image.getOriginalFilename()));
            }
        }

        LocalDateTime expired = request.getExpired().minusDays(1).atStartOfDay();

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Long foodId = foodService.registerFood(memberId, request, expired);
            imageService.saveImageMeta(foodId, expiredUpload, expiredOriginalName, ImageType.EXPIRED);
            for (UploadedImage basic : basicUploads) {
                imageService.saveImageMeta(foodId, basic.response(), basic.originalName(), ImageType.BASIC);
            }
            return foodId;
        }));
    }

    private record UploadedImage(ImageUploadResponse response, String originalName) {
    }

    // 물품 수정: 이름/정원/상세 + 일반 사진 추가(BASIC)·기존 사진 삭제 + 소비기한 사진 교체 검증.
    // 새 이미지 업로드(외부 I/O)는 트랜잭션 밖, 소유권·검증·DB 반영은 트랜잭션 안에서 처리한다.
    public void updateFood(Long memberId, Long foodId, FoodUpdateRequest request, List<MultipartFile> images) {
        List<UploadedImage> basicUploads = new ArrayList<>();
        if (images != null) {
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                basicUploads.add(new UploadedImage(imageService.uploadToStorage(image), image.getOriginalFilename()));
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            Food food = foodService.getFood(foodId);
            if (!food.getMemberId().equals(memberId)) {
                throw new FoodForbiddenException();
            }
            if (request.getExpiredImageId() != null) {
                imageService.validateExpiredImage(request.getExpiredImageId(), foodId);
            }
            food.update(request.getFoodName(), request.getCapacity(), request.getDetails());
            imageService.deleteImagesByIds(foodId, request.getDeleteImageIds());
            for (UploadedImage basic : basicUploads) {
                imageService.saveImageMeta(foodId, basic.response(), basic.originalName(), ImageType.BASIC);
            }
        });
    }

    // uploadToStorage를 트랜잭션 밖에서 수행 후, 소유권 검증 + DB 저장을 원자적으로 처리.
    // TODO: orphan 파일 cleanup job 필요 (스토리지 업로드 성공 후 DB 저장 실패 시)
    public Long uploadExpiredImage(Long memberId, Long foodId, MultipartFile file) {
        ImageUploadResponse uploadResponse = imageService.uploadToStorage(file);
        String originalFilename = file.getOriginalFilename();

        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            Food food = foodService.getFood(foodId);
            if (!food.getMemberId().equals(memberId)) {
                throw new FoodForbiddenException();
            }
            return imageService.saveImageMeta(foodId, uploadResponse, originalFilename, ImageType.EXPIRED);
        }));
    }

    @Transactional
    public void deleteFood(Long memberId, Long foodId) {
        foodService.deleteFood(memberId, foodId);
        imageService.deleteImagesByFoodId(foodId);
    }

    // 이미지 단건 soft delete. 소유권은 image → food → member 로 검증한다.
    @Transactional
    public void deleteImage(Long memberId, Long imageId) {
        Image image = imageService.getActiveImage(imageId);
        Food food = foodService.getFood(image.getFoodId());
        if (!food.getMemberId().equals(memberId)) {
            throw new ImageForbiddenException();
        }
        image.delete();
    }

    @Transactional(readOnly = true)
    public FoodDetailResponse getFoodDetail(Long foodId) {
        Food food = foodService.getFood(foodId);
        String ownerNickName = memberService.getNickName(food.getMemberId());
        List<ImageResponse> images = imageService.getImages(foodId);
        return FoodDetailResponse.of(food, ownerNickName, images);
    }

    @Transactional(readOnly = true)
    public List<FoodListResponse> getMyFoods(Long memberId) {
        List<Food> foods = foodService.getMyFoods(memberId);
        List<Long> foodIds = foods.stream().map(Food::getFoodId).toList();
        Map<Long, String> thumbnailUrls = imageService.getThumbnailUrls(foodIds);
        return foods.stream()
                .map(food -> FoodListResponse.of(food, thumbnailUrls))
                .toList();
    }

    // 메인 화면 히어로용: 가장 최근에 등록된 음식 N개를 대표 썸네일과 함께 반환한다.
    @Transactional(readOnly = true)
    public List<FoodListResponse> getRecentFoods(int size) {
        List<Food> foods = foodService.getRecentFoods(size);
        List<Long> foodIds = foods.stream().map(Food::getFoodId).toList();
        Map<Long, String> thumbnailUrls = imageService.getThumbnailUrls(foodIds);
        return foods.stream()
                .map(food -> FoodListResponse.of(food, thumbnailUrls))
                .toList();
    }

    public PageResponse<FoodListResponse> getFoods(ExStatus status, Pageable pageable) {
        var foodPage = foodService.getFoodPage(status, pageable);

        List<Long> foodIds = foodPage.getContent().stream()
                .map(Food::getFoodId)
                .toList();
        Map<Long, String> thumbnailUrls = imageService.getThumbnailUrls(foodIds);

        return PageResponse.of(foodPage.map(food -> FoodListResponse.of(food, thumbnailUrls)));
    }

    @Transactional
    public FoodRequestResponse requestFood(Long memberId, Long foodId) {
        Food food = foodService.getFood(foodId);
        if (food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        if (food.getExStatus() != ExStatus.IN_PROGRESS) {
            throw new FoodNotAvailableException();
        }
        Long requestId = foodRequestService.createRequest(memberId, foodId);
        return FoodRequestResponse.of(requestId);
    }

    @Transactional
    public void approveRequest(Long memberId, Long foodId, Long requestId) {
        Food food = foodService.getFood(foodId);
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        if (food.getExStatus() != ExStatus.IN_PROGRESS) {
            throw new FoodNotAvailableException();
        }
        FoodRequest request = foodRequestService.getRequest(requestId);
        if (!request.getFoodId().equals(foodId)) {
            throw new FoodRequestMismatchException();
        }
        request.approve();
        food.incrementApprovedCount();
        if (food.getExStatus() == ExStatus.COMPLETED) {
            foodRequestService.rejectPendingByFoodId(foodId);
        }
    }

    @Transactional
    public void rejectRequest(Long memberId, Long foodId, Long requestId) {
        Food food = foodService.getFood(foodId);
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        FoodRequest request = foodRequestService.getRequest(requestId);
        if (!request.getFoodId().equals(foodId)) {
            throw new FoodRequestMismatchException();
        }
        request.reject();
    }

    // 명세 4-3/4-4: requestFoodId만으로 수락/거절 (foodId는 요청에서 역참조). FE가 사용하는 평면 URL용.
    @Transactional
    public RequestApproveResponse approveRequest(Long memberId, Long requestFoodId) {
        FoodRequest request = foodRequestService.getRequest(requestFoodId);
        Food food = foodService.getFood(request.getFoodId());
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        if (food.getExStatus() != ExStatus.IN_PROGRESS) {
            throw new FoodNotAvailableException();
        }
        request.approve();
        food.incrementApprovedCount();
        if (food.getExStatus() == ExStatus.COMPLETED) {
            foodRequestService.rejectPendingByFoodId(food.getFoodId());
        }
        return RequestApproveResponse.of(requestFoodId, request.getStatus(), food.getExStatus());
    }

    @Transactional
    public RequestRejectResponse rejectRequest(Long memberId, Long requestFoodId) {
        FoodRequest request = foodRequestService.getRequest(requestFoodId);
        Food food = foodService.getFood(request.getFoodId());
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        request.reject();
        return RequestRejectResponse.of(requestFoodId, request.getStatus());
    }

    @Transactional(readOnly = true)
    public List<FoodRequestListResponse> getRequests(Long memberId, Long foodId) {
        Food food = foodService.getFood(foodId);
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        List<FoodRequest> requests = foodRequestService.getRequestsByFoodId(foodId);
        List<Long> requesterIds = requests.stream().map(FoodRequest::getMemberId).distinct().toList();
        Map<Long, String> nickNameById = memberService.getNickNames(requesterIds);
        return requests.stream()
                .map(request -> FoodRequestListResponse.of(request, nickNameById.get(request.getMemberId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MyFoodRequestResponse getMyRequest(Long memberId, Long foodId) {
        foodService.getFood(foodId);
        return MyFoodRequestResponse.of(foodRequestService.getMyRequest(memberId, foodId));
    }

    @Transactional(readOnly = true)
    public List<MySentRequestResponse> getMySentRequests(Long memberId) {
        List<FoodRequest> requests = foodRequestService.getMySentRequests(memberId);
        List<Long> foodIds = requests.stream().map(FoodRequest::getFoodId).distinct().toList();
        Map<Long, String> foodNameById = foodService.getFoodsByIds(foodIds).stream()
                .collect(java.util.stream.Collectors.toMap(Food::getFoodId, Food::getFoodName));
        // 보낸 요청 목록에도 물품 대표 썸네일을 노출 (BASIC 우선, 없으면 EXPIRED 폴백)
        Map<Long, String> thumbnailUrls = imageService.getThumbnailUrls(foodIds);
        return requests.stream()
                .map(request -> MySentRequestResponse.of(
                        request,
                        foodNameById.get(request.getFoodId()),
                        thumbnailUrls.get(request.getFoodId())))
                .toList();
    }

    @Transactional
    public void cancelRequest(Long memberId, Long foodId, Long requestId) {
        FoodRequest request = foodRequestService.getRequest(requestId);
        if (!request.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        if (!request.getFoodId().equals(foodId)) {
            throw new FoodRequestMismatchException();
        }
        request.cancel();
    }

    @Transactional
    public void bulkExpire(LocalDateTime now) {
        foodService.bulkExpire(now);
        foodRequestService.rejectPendingForExpiredFoods();
    }

    private void validateExpirationDate(LocalDate aiDate) {
        if (!aiDate.isAfter(LocalDate.now().minusDays(1))) {
            throw new ExpirationApiException();
        }
    }
}
