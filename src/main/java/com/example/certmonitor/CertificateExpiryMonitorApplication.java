package com.example.certmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@EnableConfigurationProperties(com.example.certmonitor.config.AppProperties.class)
public class CertificateExpiryMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CertificateExpiryMonitorApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
