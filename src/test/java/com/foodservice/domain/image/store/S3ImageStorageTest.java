package com.foodservice.domain.image.store;

import com.foodservice.common.exception.image.ImageStorageException;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3ImageStorage s3ImageStorage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3ImageStorage, "bucket", "food-share-images");
    }

    @Test
    @DisplayName("파일을 S3에 업로드하면 presigned GET URL과 S3 키를 반환한다")
    void upload_success() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "strawberry.jpg", "image/jpeg", "fake-image-data".getBytes()
        );

        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        given(presignedRequest.url()).willReturn(
                new URL("https://food-share-images.s3.ap-northeast-2.amazonaws.com/images/uuid_strawberry.jpg?X-Amz-Signature=abc")
        );
        given(s3Presigner.presignGetObject(any(Consumer.class))).willReturn(presignedRequest);

        // when
        ImageUploadResponse response = s3ImageStorage.upload(file);

        // then
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3Presigner).presignGetObject(any(Consumer.class));
        assertThat(response.getAccessUrl()).contains("s3.ap-northeast-2.amazonaws.com");
        assertThat(response.getStoredName()).startsWith("images/").endsWith(".jpg");
    }

    @Test
    @DisplayName("S3 업로드 실패 시 ImageStorageException을 던진다")
    void upload_throws_whenS3Fails() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", "fake-image-data".getBytes()
        );
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(S3Exception.builder().message("S3 error").statusCode(500).build());

        // when & then
        assertThatThrownBy(() -> s3ImageStorage.upload(file))
                .isInstanceOf(ImageStorageException.class);
    }
}
