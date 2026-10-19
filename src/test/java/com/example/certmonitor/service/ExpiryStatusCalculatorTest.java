package com.example.certmonitor.service;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.domain.CertificateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Expiry status calculation")
class ExpiryStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    private final ExpiryStatusCalculator calculator = calculatorWithThreshold(30);

    private static ExpiryStatusCalculator calculatorWithThreshold(int days) {
        AppProperties properties = new AppProperties();
        properties.setWarningThresholdDays(days);
        return new ExpiryStatusCalculator(properties);
    }

    @Test
    @DisplayName("Certificate that expired yesterday is EXPIRED")
    void expiredWhenExpiryWasYesterday() {
        assertThat(calculator.statusFor(TODAY.minusDays(1), TODAY)).isEqualTo(CertificateStatus.EXPIRED);
    }

    @Test
    @DisplayName("Certificate expiring today is EXPIRING_SOON with zero days remaining")
    void expiryTodayIsExpiringSoon() {
        assertThat(calculator.statusFor(TODAY, TODAY)).isEqualTo(CertificateStatus.EXPIRING_SOON);
        assertThat(calculator.daysRemaining(TODAY, TODAY)).isZero();
    }

    @Test
    @DisplayName("Certificate expiring exactly on the warning threshold is EXPIRING_SOON")
    void expiryOnThresholdBoundaryIsExpiringSoon() {
        assertThat(calculator.statusFor(TODAY.plusDays(30), TODAY)).isEqualTo(CertificateStatus.EXPIRING_SOON);
    }

    @Test
    @DisplayName("Certificate expiring one day after the threshold is VALID")
    void expiryOneDayAfterThresholdIsValid() {
        assertThat(calculator.statusFor(TODAY.plusDays(31), TODAY)).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    @DisplayName("Threshold of zero still treats expiry today as EXPIRING_SOON")
    void zeroThresholdBoundary() {
        ExpiryStatusCalculator zeroThreshold = calculatorWithThreshold(0);
        assertThat(zeroThreshold.statusFor(TODAY, TODAY)).isEqualTo(CertificateStatus.EXPIRING_SOON);
        assertThat(zeroThreshold.statusFor(TODAY.plusDays(1), TODAY)).isEqualTo(CertificateStatus.VALID);
        assertThat(zeroThreshold.statusFor(TODAY.minusDays(1), TODAY)).isEqualTo(CertificateStatus.EXPIRED);
    }

    @Test
    @DisplayName("Days remaining is negative for expired and positive for valid certificates")
    void daysRemaining() {
        assertThat(calculator.daysRemaining(TODAY.minusDays(3), TODAY)).isEqualTo(-3);
        assertThat(calculator.daysRemaining(TODAY.plusDays(12), TODAY)).isEqualTo(12);
    }

    @Test
    @DisplayName("Negative warning threshold is rejected")
    void negativeThresholdRejected() {
        ExpiryStatusCalculator invalid = calculatorWithThreshold(-1);
        assertThatThrownBy(() -> invalid.statusFor(TODAY.plusDays(1), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thresholdDays");
    }
}
