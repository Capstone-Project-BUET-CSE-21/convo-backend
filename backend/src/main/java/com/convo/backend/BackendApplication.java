package com.convo.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class BackendApplication {

    public static void main(String[] args) {
        Dotenv sharedDotenv = Dotenv.configure()
                .directory("..")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        setPropertyIfPresent("MONA_FRONTEND_URL", sharedDotenv);
        setPropertyIfPresent("FARU_FRONTEND_URL", sharedDotenv);
        setPropertyIfPresent("DEBO_FRONTEND_URL", sharedDotenv);
        setPropertyIfPresent("TABA_FRONTEND_URL", sharedDotenv);
        setPropertyIfPresent("ANIS_FRONTEND_URL", sharedDotenv);

        SpringApplication.run(BackendApplication.class, args);
    }

    private static void setPropertyIfPresent(String key, Dotenv dotenv) {
        String value = dotenv.get(key);
        if (value != null && !value.isBlank()) {
            System.setProperty(key, value);
        }
    }

}
