package com.example.certmonitor.service;

import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateStatus;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model used by templates. Implemented as a plain immutable class (not a record)
 * so Thymeleaf / SpringEL property access via standard JavaBean getters is guaranteed.
 */
public class CertificateView {

    private final Long id;
    private final String name;
    private final CertificateType type;
    private final String typeLabel;
    private final String hostname;
    private final String issuer;
    private final String ownerTeam;
    private final EnvironmentType environment;
    private final String environmentLabel;
    private final LocalDate issueDate;
    private final LocalDate expiryDate;
    private final String notes;
    private final CertificateStatus status;
    private final String statusLabel;
    private final int daysRemaining;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private CertificateView(Long id, String name, CertificateType type, String typeLabel, String hostname,
                            String issuer, String ownerTeam, EnvironmentType environment, String environmentLabel,
                            LocalDate issueDate, LocalDate expiryDate, String notes, CertificateStatus status,
                            String statusLabel, int daysRemaining, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.typeLabel = typeLabel;
        this.hostname = hostname;
        this.issuer = issuer;
        this.ownerTeam = ownerTeam;
        this.environment = environment;
        this.environmentLabel = environmentLabel;
        this.issueDate = issueDate;
        this.expiryDate = expiryDate;
        this.notes = notes;
        this.status = status;
        this.statusLabel = statusLabel;
        this.daysRemaining = daysRemaining;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CertificateView from(Certificate c, CertificateStatus status, int daysRemaining) {
        return new CertificateView(
                c.getId(),
                c.getName(),
                c.getType(),
                c.getType().getLabel(),
                c.getHostname(),
                c.getIssuer(),
                c.getOwnerTeam(),
                c.getEnvironment(),
                c.getEnvironment().getLabel(),
                c.getIssueDate(),
                c.getExpiryDate(),
                c.getNotes(),
                status,
                status.getLabel(),
                daysRemaining,
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CertificateType getType() {
        return type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public EnvironmentType getEnvironment() {
        return environment;
    }

    public String getEnvironmentLabel() {
        return environmentLabel;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getNotes() {
        return notes;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public int getDaysRemaining() {
        return daysRemaining;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired() {
        return status == CertificateStatus.EXPIRED;
    }
}
