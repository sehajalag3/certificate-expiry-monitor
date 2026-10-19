package com.example.certmonitor.service;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.domain.CertificateStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Single source of truth for computing certificate expiry status.
 *
 * Rules (documented in docs/srs.md and README):
 *   daysRemaining = days between today and the expiry date (expiry date minus today).
 *   EXPIRED       : daysRemaining < 0                  (expiry date strictly before today)
 *   EXPIRING_SOON : 0 <= daysRemaining <= threshold    (includes expiry date == today)
 *   VALID         : daysRemaining > threshold
 */
@Component
public class ExpiryStatusCalculator {

    private final AppProperties appProperties;

    public ExpiryStatusCalculator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public int warningThresholdDays() {
        return appProperties.getWarningThresholdDays();
    }

    public int daysRemaining(LocalDate expiryDate, LocalDate today) {
        return (int) ChronoUnit.DAYS.between(today, expiryDate);
    }

    public CertificateStatus statusFor(LocalDate expiryDate, LocalDate today) {
        return statusFor(expiryDate, today, appProperties.getWarningThresholdDays());
    }

    public static CertificateStatus statusFor(LocalDate expiryDate, LocalDate today, int thresholdDays) {
        if (expiryDate == null || today == null) {
            throw new IllegalArgumentException("expiryDate and today are required");
        }
        if (thresholdDays < 0) {
            throw new IllegalArgumentException("thresholdDays must be >= 0");
        }
        long days = ChronoUnit.DAYS.between(today, expiryDate);
        if (days < 0) {
            return CertificateStatus.EXPIRED;
        }
        if (days <= thresholdDays) {
            return CertificateStatus.EXPIRING_SOON;
        }
        return CertificateStatus.VALID;
    }
}
