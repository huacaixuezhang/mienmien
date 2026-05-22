package com.mienmien.business;

import com.mienmien.business.management.infrastructure.config.BusinessJdAiProperties;
import com.mienmien.business.management.infrastructure.config.MienmienCryptoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.mienmien.business")
@EnableScheduling
@EnableConfigurationProperties({BusinessJdAiProperties.class, MienmienCryptoProperties.class})
public class BusinessApplication {
    public static void main(String[] args) {
        SpringApplication.run(BusinessApplication.class, args);
    }
}
