package com.foodservice.domain.image.controller;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.constant.SessionConst;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ImageControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ImageRepository imageRepository;

    private MockHttpSession loginSession(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    private Food createAndSaveFood(Long memberId) {
        Food food = Food.builder()
                .memberId(memberId)
                .foodName("테스트 음식")
                .details("테스트 상세")
                .capacity(2)
                .exStatus(ExStatus.IN_PROGRESS)
                .expired(LocalDateTime.now().plusDays(3))
                .build();
        return foodRepository.save(food);
    }

    private Image saveImage(Long foodId) {
        return imageRepository.save(Image.builder()
                .foodId(foodId)
                .originalName("img.png")
                .storedName("stored-img")
                .imageType(ImageType.BASIC)
                .build());
    }

    @Test
    @DisplayName("DELETE /api/v1/images/{imageId} — 소유자가 삭제하면 200과 메시지를 반환하고 soft delete된다.")
    void deleteImage_success() throws Exception {
        Long memberId = 1L;
        Food food = createAndSaveFood(memberId);
        Image image = saveImage(food.getFoodId());

        mockMvc.perform(delete("/api/v1/images/{imageId}", image.getImageId())
                        .session(loginSession(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("이미지가 삭제되었습니다."));

        assertThat(imageRepository.findById(image.getImageId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/v1/images/{imageId} — 소유자가 아니면 403 FORBIDDEN_IMAGE_ACCESS를 반환한다.")
    void deleteImage_forbidden_returns403() throws Exception {
        Food food = createAndSaveFood(1L);
        Image image = saveImage(food.getFoodId());

        mockMvc.perform(delete("/api/v1/images/{imageId}", image.getImageId())
                        .session(loginSession(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("FORBIDDEN_IMAGE_ACCESS"));
    }

    @Test
    @DisplayName("DELETE /api/v1/images/{imageId} — 존재하지 않는 이미지면 404 IMAGE_NOT_FOUND를 반환한다.")
    void deleteImage_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/images/{imageId}", 999L)
                        .session(loginSession(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("IMAGE_NOT_FOUND"));
    }

    @Test
    @DisplayName("음식 소유자가 이미지를 업로드하면 201과 imageId를 반환한다.")
    void uploadImage_success() throws Exception {
        Long memberId = 1L;
        Food food = createAndSaveFood(memberId);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image data".getBytes());

        mockMvc.perform(multipart("/api/v1/foods/expired-date/{foodId}", food.getFoodId())
                        .file(file)
                        .session(loginSession(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data").isNumber())
                .andExpect(jsonPath("$.message").value("이미지 업로드가 완료되었습니다."));

        assertThat(imageRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(img -> assertThat(img.getImageType()).isEqualTo(ImageType.EXPIRED));
    }

    @Test
    @DisplayName("음식 소유자가 아닌 멤버가 업로드하면 403을 반환한다.")
    void uploadImage_forbidden_returns403() throws Exception {
        Food food = createAndSaveFood(1L);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image data".getBytes());

        mockMvc.perform(multipart("/api/v1/foods/expired-date/{foodId}", food.getFoodId())
                        .file(file)
                        .session(loginSession(2L))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("존재하지 않는 foodId로 업로드하면 404를 반환한다.")
    void uploadImage_foodNotFound_returns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "image data".getBytes());

        mockMvc.perform(multipart("/api/v1/foods/expired-date/{foodId}", 999L)
                        .file(file)
                        .session(loginSession(1L))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("FOOD_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("존재하지 않는 음식입니다."));
    }

    @Test
    @DisplayName("파일이 비어있으면 400을 반환한다.")
    void uploadImage_emptyFile_returns400() throws Exception {
        Food food = createAndSaveFood(1L);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/v1/foods/expired-date/{foodId}", food.getFoodId())
                        .file(emptyFile)
                        .session(loginSession(1L))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("EMPTY_FILE"))
                .andExpect(jsonPath("$.detail").value("업로드할 파일이 존재하지 않습니다."));
    }

    @Test
    @DisplayName("파일 이름이 없으면 400을 반환한다.")
    void uploadImage_noFilename_returns400() throws Exception {
        Food food = createAndSaveFood(1L);
        MockMultipartFile noNameFile = new MockMultipartFile("file", null, "image/png", "data".getBytes());

        mockMvc.perform(multipart("/api/v1/foods/expired-date/{foodId}", food.getFoodId())
                        .file(noNameFile)
                        .session(loginSession(1L))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("INVALID_FILE_FORMAT"))
                .andExpect(jsonPath("$.detail").value("올바르지 않은 파일 형식입니다."));
    }
}
