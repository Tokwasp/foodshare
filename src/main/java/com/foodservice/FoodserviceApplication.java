package com.foodservice;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodserviceApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(FoodserviceApplication.class)
                .initializers(new DotenvApplicationInitializer())
                .run(args);
    }

}
