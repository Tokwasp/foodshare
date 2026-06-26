package com.foodservice.domain.food.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Component
@Profile({"test", "local"})
public class StubExpirationApiClient implements ExpirationApiClient {

    private static final int DEFAULT_EXPIRATION_DAYS = 7;

    @Override
    public LocalDate fetchExpirationDate(MultipartFile file) {
        return LocalDate.now().plusDays(DEFAULT_EXPIRATION_DAYS);
    }
}
