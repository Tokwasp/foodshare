package com.foodservice.domain.image.store;

import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
@Profile("!prod & !s3-test")
public class StubImageStorage implements ImageStorage {

    @Override
    public ImageUploadResponse upload(MultipartFile file) {
        String stubStoredName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String accessUrl = generateAccessUrl(stubStoredName);
        return new ImageUploadResponse(accessUrl, stubStoredName);
    }

    @Override
    public String generateAccessUrl(String storedName) {
        return "https://stub-s3-bucket.com/images/" + storedName;
    }
}
