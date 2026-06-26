package com.foodservice.domain.foodrequest.repository;

import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import com.foodservice.common.exception.foodrequest.InvalidRequestStatusException;
import static com.foodservice.domain.foodrequest.entity.FoodRequestStatus.REQUEST;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FoodRequestRepositoryTest {

    @Autowired
    private FoodRequestRepository foodRequestRepository;

    @Test
    @DisplayName("같은 foodId + memberId + REQUEST 상태인 신청이 존재하면 true를 반환한다.")
    void existsDuplicateRequest_returnsTrue() {
        // given
        foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        boolean exists = foodRequestRepository.existsDuplicateRequest(1L, 2L, REQUEST);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("신청이 없으면 false를 반환한다.")
    void existsDuplicateRequest_returnsFalse() {
        // when
        boolean exists = foodRequestRepository.existsDuplicateRequest(1L, 2L, REQUEST);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("삭제되지 않은 신청을 requestFoodId로 단건 조회한다.")
    void findActiveById_returnsRequest() {
        // given
        FoodRequest saved = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        Optional<FoodRequest> result = foodRequestRepository.findActiveById(saved.getRequestFoodId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(REQUEST);
    }

    @Test
    @DisplayName("approve() 호출 시 status가 APPROVED로 변경된다.")
    void approve_changesStatusToApproved() {
        // given
        FoodRequest saved = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        saved.approve();
        foodRequestRepository.save(saved);

        // then
        FoodRequest reloaded = foodRequestRepository.findById(saved.getRequestFoodId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FoodRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("reject() 호출 시 status가 REJECTED로 변경된다.")
    void reject_changesStatusToRejected() {
        // given
        FoodRequest saved = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        saved.reject();
        foodRequestRepository.save(saved);

        // then
        FoodRequest reloaded = foodRequestRepository.findById(saved.getRequestFoodId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FoodRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("findActiveByFoodId - 해당 음식의 삭제되지 않은 신청 목록을 반환한다.")
    void findActiveByFoodId_returnsActiveRequests() {
        // given
        foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());
        foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(3L).build());
        foodRequestRepository.save(FoodRequest.builder().foodId(2L).memberId(2L).build());

        // when
        List<FoodRequest> result = foodRequestRepository.findActiveByFoodId(1L);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getFoodId().equals(1L));
    }

    @Test
    @DisplayName("findActiveByFoodId - deleted=true인 신청은 결과에서 제외된다.")
    void findActiveByFoodId_excludesDeletedRequests() {
        // given
        FoodRequest active = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());
        FoodRequest deleted = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(3L).build());
        ReflectionTestUtils.setField(deleted, "deleted", true);
        foodRequestRepository.save(deleted);

        // when
        List<FoodRequest> result = foodRequestRepository.findActiveByFoodId(1L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRequestFoodId()).isEqualTo(active.getRequestFoodId());
    }

    @Test
    @DisplayName("cancel() 호출 시 deleted=true가 되어 findActiveById에서 제외된다.")
    void cancel_softDeletesRequest() {
        // given
        FoodRequest saved = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        saved.cancel();
        foodRequestRepository.save(saved);

        // then
        Optional<FoodRequest> result = foodRequestRepository.findActiveById(saved.getRequestFoodId());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cancel() — APPROVED 상태에서 호출 시 InvalidRequestStatusException이 발생한다.")
    void cancel_throws_whenStatusIsApproved() {
        // given
        FoodRequest saved = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());
        saved.approve();

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(saved::cancel)
                .isInstanceOf(InvalidRequestStatusException.class);
    }

    @Test
    @DisplayName("findActiveByFoodIdAndMemberId — 해당 신청자의 활성 신청을 반환한다.")
    void findActiveByFoodIdAndMemberId_returnsRequest() {
        // given
        foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());

        // when
        List<FoodRequest> result = foodRequestRepository.findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findActiveByFoodIdAndMemberId — 취소(deleted=true)된 신청은 반환하지 않는다.")
    void findActiveByFoodIdAndMemberId_excludesCancelled() {
        // given
        FoodRequest request = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());
        request.cancel();
        foodRequestRepository.save(request);

        // when
        List<FoodRequest> result = foodRequestRepository.findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActiveByFoodIdAndMemberId — 거절 후 재신청 시 최신 1건(REQUEST)만 반환한다.")
    void findActiveByFoodIdAndMemberId_returnsLatestWhenReapplied() {
        // given
        FoodRequest first = foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build());
        first.reject();
        foodRequestRepository.save(first);
        foodRequestRepository.save(FoodRequest.builder().foodId(1L).memberId(2L).build()); // 재신청

        // when
        List<FoodRequest> result = foodRequestRepository.findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(FoodRequestStatus.REQUEST);
    }
}
