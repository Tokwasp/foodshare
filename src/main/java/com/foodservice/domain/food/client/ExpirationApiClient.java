package com.foodservice.domain.food.client;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface ExpirationApiClient {

    /**

     * @param file 음식 이미지
     * @return AI가 판단한 소비기한 (yyyy-MM-dd)
     */
    LocalDate fetchExpirationDate(MultipartFile file);
}
