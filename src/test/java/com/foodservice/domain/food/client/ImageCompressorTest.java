package com.foodservice.domain.food.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCompressorTest {

    private final ImageCompressor imageCompressor = new ImageCompressor();

    @Test
    @DisplayName("큰 이미지는 긴 변이 1024 이하로 축소되어 압축된다.")
    void compress_resizesLargeImage() throws Exception {
        byte[] source = jpegOf(makeImage(3000, 2000), 0.9f);
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", source);

        byte[] result = imageCompressor.compress(file);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded).isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(1024);
    }

    @Test
    @DisplayName("디테일이 많아 용량이 큰 이미지도 게이트웨이 한도(base64 약 1MB) 안으로 압축된다.")
    void compress_keepsHeavyImageUnderGatewayLimit() throws Exception {
        // 1024px 이하 고주파(잡음) 이미지: 다운스케일로도 잘 줄지 않아 용량이 크다.
        byte[] source = jpegOf(makeNoisyImage(1024, 768), 0.95f);
        MockMultipartFile file = new MockMultipartFile("file", "noisy.jpg", "image/jpeg", source);

        byte[] result = imageCompressor.compress(file);

        int base64Length = ((result.length + 2) / 3) * 4;
        assertThat(base64Length).isLessThan(1024 * 1024);
    }

    @Test
    @DisplayName("작은 이미지는 그대로 유효한 JPEG로 압축된다.")
    void compress_smallImage() throws Exception {
        byte[] source = jpegOf(makeImage(200, 150), 0.9f);
        MockMultipartFile file = new MockMultipartFile("file", "small.jpg", "image/jpeg", source);

        byte[] result = imageCompressor.compress(file);

        assertThat(result).isNotEmpty();
        assertThat(ImageIO.read(new ByteArrayInputStream(result))).isNotNull();
    }

    private BufferedImage makeImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, (x * 255 / w) << 16 | (y * 255 / h) << 8 | 0x80);
            }
        }
        return img;
    }

    private BufferedImage makeNoisyImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(7);
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int c = rnd.nextInt(0xFFFFFF);
                for (int dy = 0; dy < 2 && y + dy < h; dy++) {
                    for (int dx = 0; dx < 2 && x + dx < w; dx++) {
                        img.setRGB(x + dx, y + dy, c);
                    }
                }
            }
        }
        return img;
    }

    private byte[] jpegOf(BufferedImage img, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
