package com.foodservice.external;

import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import com.foodservice.domain.image.store.S3ImageStorage;
import com.foodservice.external.fixture.ImageLoad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Disabled("실제 AWS S3에 연결이 필요한 통합 테스트 - AWS credentials 및 버킷 설정 필요")
@ActiveProfiles("s3-test")
@SpringBootTest
class S3ImageStorageIntegrationTest {

    @Autowired
    private S3ImageStorage s3ImageStorage;

    @Autowired
    private S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final List<String> uploadedKeys = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String key : uploadedKeys) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        }
        uploadedKeys.clear();
    }

    @Test
    @DisplayName("JPEG 이미지를 S3에 업로드하면 presigned URL과 S3 키를 반환한다")
    void upload_jpeg_success() {
        // given
        MultipartFile file = ImageLoad.loadBchoB();

        // when
        ImageUploadResponse response = s3ImageStorage.upload(file);
        uploadedKeys.add(response.getStoredName());

        // then
        assertThat(response.getAccessUrl()).startsWith("https://");
        assertThat(response.getAccessUrl()).contains(bucket);
        assertThat(response.getStoredName()).startsWith("images/");
        assertThat(response.getStoredName()).endsWith(".jpg");
    }

    @Test
    @DisplayName("PNG 이미지를 S3에 업로드하면 presigned URL과 S3 키를 반환한다")
    void upload_png_success() {
        // given
        MultipartFile file = ImageLoad.loadStrawberry();

        // when
        ImageUploadResponse response = s3ImageStorage.upload(file);
        uploadedKeys.add(response.getStoredName());

        // then
        assertThat(response.getAccessUrl()).startsWith("https://");
        assertThat(response.getStoredName()).startsWith("images/");
        assertThat(response.getStoredName()).endsWith(".png");
    }

    @Test
    @DisplayName("S3에 업로드된 파일이 실제로 존재하는지 확인한다")
    void upload_verifyObjectExistsInS3() {
        // given
        MultipartFile file = ImageLoad.loadBchoB();

        // when
        ImageUploadResponse response = s3ImageStorage.upload(file);
        uploadedKeys.add(response.getStoredName());

        // then - HeadObject로 실제 파일 존재 여부 확인
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(response.getStoredName())
                .build();
        assertThat(s3Client.headObject(headRequest)).isNotNull();
    }

    @Test
    @DisplayName("동일한 파일을 두 번 업로드하면 서로 다른 S3 키를 가진다")
    void upload_twice_producesDifferentKeys() {
        // given
        MultipartFile file1 = ImageLoad.loadBchoB();
        MultipartFile file2 = ImageLoad.loadBchoB();

        // when
        ImageUploadResponse response1 = s3ImageStorage.upload(file1);
        ImageUploadResponse response2 = s3ImageStorage.upload(file2);
        uploadedKeys.add(response1.getStoredName());
        uploadedKeys.add(response2.getStoredName());

        // then
        assertThat(response1.getStoredName()).isNotEqualTo(response2.getStoredName());
    }

    @Test
    @DisplayName("업로드 후 삭제된 S3 키는 HeadObject 요청 시 NoSuchKeyException을 던진다")
    void delete_objectNotFoundAfterDeletion() {
        // given
        MultipartFile file = ImageLoad.loadBchoB();
        ImageUploadResponse response = s3ImageStorage.upload(file);
        String key = response.getStoredName();

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());

        // when & then
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        assertThatThrownBy(() -> s3Client.headObject(headRequest))
                .isInstanceOf(NoSuchKeyException.class);
    }
}
