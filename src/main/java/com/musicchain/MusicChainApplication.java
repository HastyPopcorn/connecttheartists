package com.musicchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MusicChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicChainApplication.class, args);
    }
}
