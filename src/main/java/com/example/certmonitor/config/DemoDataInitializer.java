package com.example.certmonitor.config;

import com.example.certmonitor.service.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoDataService demoDataService;

    public DemoDataInitializer(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = demoDataService.seedIfEmpty();
        if (seeded > 0) {
            log.info("Seeded {} demo certificates", seeded);
        } else {
            log.info("Demo data seeding skipped: database already contains certificates");
        }
    }
}
