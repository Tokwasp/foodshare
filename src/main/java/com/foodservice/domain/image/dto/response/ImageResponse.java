package com.foodservice.domain.image.dto.response;

import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import lombok.Getter;

@Getter
public class ImageResponse {

    private final Long imageId;
    private final String accessUrl;
    private final ImageType imageType;

    private ImageResponse(Long imageId, String accessUrl, ImageType imageType) {
        this.imageId = imageId;
        this.accessUrl = accessUrl;
        this.imageType = imageType;
    }

    public static ImageResponse of(Long imageId, String accessUrl, ImageType imageType) {
        return new ImageResponse(imageId, accessUrl, imageType);
    }

    public static ImageResponse of(Image image, String accessUrl) {
        return new ImageResponse(image.getImageId(), accessUrl, image.getImageType());
    }
}
