package com.example.certmonitor.domain;

public enum CertificateStatus {
    VALID("Valid"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired");

    private final String label;

    CertificateStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
