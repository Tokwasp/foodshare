package com.foodservice.domain.food.dto;

import java.time.LocalDate;

public record ExpirationDateResponse(LocalDate expired) {
    public static ExpirationDateResponse of(LocalDate date) {
        return new ExpirationDateResponse(date);
    }
}
