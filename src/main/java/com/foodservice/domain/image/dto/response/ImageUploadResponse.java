package com.foodservice.domain.image.dto.response;

import lombok.Getter;

@Getter
public class ImageUploadResponse {
    private final String accessUrl;
    private final String storedName;

    public ImageUploadResponse(String accessUrl, String storedName) {
        this.accessUrl = accessUrl;
        this.storedName = storedName;
    }
}
