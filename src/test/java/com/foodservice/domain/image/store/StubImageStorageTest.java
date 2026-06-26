package com.foodservice.domain.image.store;

import com.foodservice.domain.image.ImageFixture;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class StubImageStorageTest {
    private final StubImageStorage stubImageStorage = new StubImageStorage();

    @Test
    @DisplayName("파일을 업로드하면 URL을 반환한다.")
    void uploadSuccess() throws Exception {
        // given
        MockMultipartFile mockFile = ImageFixture.createMultipartFile();
        // when
        ImageUploadResponse resultUrl = stubImageStorage.upload(mockFile);
        // then
        assertThat(resultUrl.getAccessUrl()).startsWith("https://stub-s3-bucket.com/images/");
        assertThat(resultUrl.getAccessUrl()).contains("test-image.png");
    }
}