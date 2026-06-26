package com.foodservice.domain.image.store;

import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {
    ImageUploadResponse upload(MultipartFile file);

    String generateAccessUrl(String storedName);
}
