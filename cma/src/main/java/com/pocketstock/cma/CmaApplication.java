package com.pocketstock.cma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class CmaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CmaApplication.class, args);
    }
}
