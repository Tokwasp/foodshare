package com.foodservice.domain.member.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final String CODE_FORMAT = "%06d";

    public String generate() {
        return String.format(CODE_FORMAT, RANDOM.nextInt(CODE_BOUND));
    }
}
