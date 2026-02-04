package com.cqlplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CqlPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CqlPlatformApplication.class, args);
    }
}
