package com.foodservice.domain.image;

import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

public class ImageFixture {
    public static MockMultipartFile createMultipartFile(){
        return new MockMultipartFile("file", "test-image.png", "image/png", "dummy image data".getBytes());
    }

    public static ImageUploadResponse createUploadResponse(MockMultipartFile file){
        String stubStoredName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String expectedUrl = "https://stub-s3-bucket.com/images/" + stubStoredName;

        return new ImageUploadResponse(expectedUrl, stubStoredName);
    }

}
