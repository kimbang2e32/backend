package com.appad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class AppadApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppadApplication.class, args);
    }

}
