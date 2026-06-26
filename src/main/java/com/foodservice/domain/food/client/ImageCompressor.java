package com.foodservice.domain.food.client;

import com.foodservice.common.exception.food.ExpirationApiException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class ImageCompressor {

    // 처음 시도하는 최대 변(긴 쪽) 길이. 용량이 한도를 넘으면 단계적으로 줄인다.
    private static final int MAX_DIMENSION = 1024;
    // 해상도를 더 줄이지 않는 하한. 너무 작으면 날짜 인식 정확도가 떨어진다.
    private static final int MIN_DIMENSION = 512;
    // 해상도 한 단계 축소 비율.
    private static final double DIMENSION_STEP = 0.8;

    // 처음 시도하는 JPEG 품질과 하한, 한 단계 축소 폭.
    private static final float START_QUALITY = 0.75f;
    private static final float MIN_QUALITY = 0.4f;
    private static final float QUALITY_STEP = 0.1f;

    // GMS 게이트웨이로 나가는 요청 본문 크기 한도 대응.
    // 전송 시 base64 인코딩으로 약 33% 부풀기 때문에, 원본(JPEG) 바이트 기준으로 보수적인 목표치를 둔다.
    // (목표 base64 ≈ 1MB → 원본 ≈ 750KB)
    private static final int TARGET_MAX_BYTES = 750 * 1024;

    public byte[] compress(MultipartFile file) {
        try {
            BufferedImage original = ImageIO.read(file.getInputStream());
            if (original == null) {
                throw new ExpirationApiException(
                        new IOException("이미지를 읽을 수 없습니다: " + file.getOriginalFilename()));
            }

            int maxDimension = MAX_DIMENSION;
            byte[] lastResult = null;

            // 목표 용량 이하가 될 때까지 품질 → 해상도 순으로 단계적으로 줄여가며 재압축한다.
            while (true) {
                BufferedImage resized = resizeIfNeeded(original, maxDimension);

                for (float quality = START_QUALITY; ; quality -= QUALITY_STEP) {
                    lastResult = writeAsJpeg(resized, quality);
                    if (lastResult.length <= TARGET_MAX_BYTES) {
                        log.debug("이미지 압축 완료: {} bytes (maxDim={}, quality={})",
                                lastResult.length, maxDimension, quality);
                        return lastResult;
                    }
                    if (quality - QUALITY_STEP < MIN_QUALITY) {
                        break; // 품질 하한 도달 → 해상도 축소 단계로
                    }
                }

                int nextDimension = (int) (maxDimension * DIMENSION_STEP);
                if (nextDimension < MIN_DIMENSION) {
                    // 해상도·품질 하한까지 줄였는데도 목표를 못 맞춘 경우, 가능한 최소 용량 결과라도 전송한다.
                    log.warn("이미지 압축 후에도 목표 용량({} bytes)을 초과했습니다: {} bytes (file={})",
                            TARGET_MAX_BYTES, lastResult.length, file.getOriginalFilename());
                    return lastResult;
                }
                maxDimension = nextDimension;
            }
        } catch (IOException e) {
            throw new ExpirationApiException(e);
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage src, int maxDimension) {
        int w = src.getWidth();
        int h = src.getHeight();
        int maxSide = Math.max(w, h);
        if (maxSide <= maxDimension) return src;

        double scale = (double) maxDimension / maxSide;
        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    private byte[] writeAsJpeg(BufferedImage image, float quality) throws IOException {
        BufferedImage rgb = toRgb(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG ImageWriter를 찾을 수 없습니다.");
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
