package com.example.certmonitor.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Min(value = 0, message = "app.warning-threshold-days must be zero or greater")
    private int warningThresholdDays = 30;
    private final DemoData demoData = new DemoData();

    public int getWarningThresholdDays() {
        return warningThresholdDays;
    }

    public void setWarningThresholdDays(int warningThresholdDays) {
        this.warningThresholdDays = warningThresholdDays;
    }

    public DemoData getDemoData() {
        return demoData;
    }

    public static class DemoData {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
