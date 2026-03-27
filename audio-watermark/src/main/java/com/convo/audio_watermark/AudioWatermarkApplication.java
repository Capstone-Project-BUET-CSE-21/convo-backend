package com.convo.audio_watermark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class AudioWatermarkApplication {

    public static void main(String[] args) {
        // Load the .env file
        Dotenv dotenv = Dotenv.load();

        // Set Spring datasource properties from .env
        System.setProperty("spring.datasource.url", dotenv.get("DB_URL"));
        System.setProperty("spring.datasource.username", dotenv.get("DB_USER"));
        System.setProperty("spring.datasource.password", dotenv.get("DB_PASS"));

        SpringApplication.run(AudioWatermarkApplication.class, args);
    }

}