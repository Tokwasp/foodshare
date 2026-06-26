package com.foodservice.external.fixture;

import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;

public class ImageLoad {

    public static MultipartFile loadBchoB(){
        return loadMultipartFile( "jpg" , "bchob.jpg");
    }
    public static MultipartFile loadStrawberry(){
        return loadMultipartFile( "png" , "strawberry.png");
    }

    public static MultipartFile loadImage(String fileType, String fileName){
        return loadMultipartFile(fileType ,fileName);
    }

    private static MultipartFile loadMultipartFile(String  fileType ,String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource("images/" + fileName);
            byte[] content = Files.readAllBytes(resource.getFile().toPath());

            return new MockMultipartFile(
                    "image", fileName, fileType , content
            );
        } catch (IOException e){
            throw new RuntimeException("테스트 이미지를 불러오는데 실패했습니다.: " + fileName);
        }
    }
}
