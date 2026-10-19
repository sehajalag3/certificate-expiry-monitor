package com.example.certmonitor.domain;

public enum EnvironmentType {
    PRODUCTION("Production"),
    STAGING("Staging"),
    DEVELOPMENT("Development"),
    QA("QA");

    private final String label;

    EnvironmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
