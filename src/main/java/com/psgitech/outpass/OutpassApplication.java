package com.psgitech.outpass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(considerNestedRepositories = true)
public class OutpassApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutpassApplication.class, args);
    }
}
