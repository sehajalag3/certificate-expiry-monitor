package com.example.certmonitor.domain;

public enum CertificateType {
    TLS_SSL("TLS/SSL"),
    DOMAIN("Domain"),
    CODE_SIGNING("Code Signing"),
    CLIENT("Client"),
    EMAIL_SMTP("Email / SMTP"),
    OTHER("Other");

    private final String label;

    CertificateType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
