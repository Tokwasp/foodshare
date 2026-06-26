package com.foodservice.domain.image.store;

import com.foodservice.common.exception.image.ImageStorageException;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
@Profile({"prod", "s3-test"})
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public ImageUploadResponse upload(MultipartFile file) {
        String s3Key = buildS3Key(file.getOriginalFilename());
        putObject(file, s3Key);
        String accessUrl = generatePresignedGetUrl(s3Key);
        return new ImageUploadResponse(accessUrl, s3Key);
    }

    @Override
    public String generateAccessUrl(String storedName) {
        return generatePresignedGetUrl(storedName);
    }

    private String generatePresignedGetUrl(String s3Key) {
        return s3Presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofDays(7))
                        .getObjectRequest(g -> g
                                .bucket(bucket)
                                .key(s3Key)))
                .url()
                .toString();
    }

    private String buildS3Key(String originalFilename) {
        String ext = extractExtension(originalFilename);
        return "images/" + UUID.randomUUID() + ext;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) return "";
        String ext = filename.substring(dotIndex);
        // 영숫자와 점만 허용
        return ext.matches("\\.[a-zA-Z0-9]+") ? ext : "";
    }

    private void putObject(MultipartFile file, String s3Key) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException | SdkException e) {
            throw new ImageStorageException(e);
        }
    }
}
