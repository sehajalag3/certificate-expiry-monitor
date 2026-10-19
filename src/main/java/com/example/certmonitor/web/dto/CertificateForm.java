package com.example.certmonitor.web.dto;

import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class CertificateForm {

    public static final String HOSTNAME_PATTERN =
            "^(\\*\\.)?([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$|^localhost$";

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotNull(message = "Certificate type is required")
    private CertificateType type;

    @NotBlank(message = "Hostname is required")
    @Size(max = 255, message = "Hostname must be at most 255 characters")
    @Pattern(regexp = HOSTNAME_PATTERN, message = "Hostname must be a valid domain or host name (wildcards such as *.example.com are allowed)")
    private String hostname;

    @Size(max = 100, message = "Issuer must be at most 100 characters")
    private String issuer;

    @NotBlank(message = "Owner / team is required")
    @Size(max = 80, message = "Owner / team must be at most 80 characters")
    private String ownerTeam;

    @NotNull(message = "Environment is required")
    private EnvironmentType environment;

    @NotNull(message = "Issue date is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    @NotNull(message = "Expiry date is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    @AssertTrue(message = "Expiry date must be after the issue date")
    public boolean isExpiryAfterIssue() {
        return issueDate == null || expiryDate == null || expiryDate.isAfter(issueDate);
    }

    @AssertTrue(message = "Issue date cannot be in the future")
    public boolean isIssueDateNotInFuture() {
        return issueDate == null || !issueDate.isAfter(LocalDate.now());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CertificateType getType() {
        return type;
    }

    public void setType(CertificateType type) {
        this.type = type;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    public EnvironmentType getEnvironment() {
        return environment;
    }

    public void setEnvironment(EnvironmentType environment) {
        this.environment = environment;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
