package com.foodservice.external;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class RedisIntegrationTest {

    private static final String KEY = "email:verify:test@example.com";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void tearDown() {
        stringRedisTemplate.delete(KEY);
    }

    @DisplayName("Redis에 값을 TTL과 함께 저장하고 조회한다.")
    @Disabled
    @Test
    void setAndGetWithTtl() {
        // when
        stringRedisTemplate.opsForValue().set(KEY, "123456", Duration.ofSeconds(300));

        // then
        String value = stringRedisTemplate.opsForValue().get(KEY);
        Long ttl = stringRedisTemplate.getExpire(KEY);

        assertThat(value).isEqualTo("123456");
        assertThat(ttl).isBetween(1L, 300L);
    }
}
