package com.example.certmonitor.service;

import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.repository.CertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DemoDataService {

    private final CertificateRepository repository;

    public DemoDataService(CertificateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int seedIfEmpty() {
        if (repository.count() > 0) {
            return 0;
        }
        return seed();
    }

    @Transactional
    public int reset() {
        repository.deleteAllInBatch();
        return seed();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    private int seed() {
        LocalDate today = LocalDate.now();
        List<Certificate> certificates = List.of(
                certificate("Corporate Website", CertificateType.TLS_SSL, "www.example.com",
                        "Let's Encrypt", "Web Platform", EnvironmentType.PRODUCTION,
                        today.minusDays(365), today.minusDays(45),
                        "Renewal already overdue. Owner notified by email."),
                certificate("Customer API Gateway", CertificateType.TLS_SSL, "api.example.com",
                        "DigiCert", "Platform Engineering", EnvironmentType.PRODUCTION,
                        today.minusDays(100), today.minusDays(5),
                        "Load balancer certificate. Escalate to on-call if not renewed."),
                certificate("Partner Portal", CertificateType.TLS_SSL, "partners.example.com",
                        "GlobalSign", "Partner Success", EnvironmentType.PRODUCTION,
                        today.minusDays(300), today,
                        "Expires today. Renewal order raised with procurement."),
                certificate("Internal Wiki", CertificateType.TLS_SSL, "wiki.example.com",
                        "Let's Encrypt", "IT Operations", EnvironmentType.STAGING,
                        today.minusDays(60), today.plusDays(7),
                        "Auto-renewal is enabled but the ACME client is being migrated."),
                certificate("Billing API", CertificateType.TLS_SSL, "billing.example.com",
                        "Sectigo", "Finance Systems", EnvironmentType.PRODUCTION,
                        today.minusDays(240), today.plusDays(20),
                        "Coordinate renewal window with the finance change freeze."),
                certificate("Example Apex Domain", CertificateType.DOMAIN, "example.com",
                        "Namecheap", "Brand & Web", EnvironmentType.PRODUCTION,
                        today.minusDays(500), today.plusDays(30),
                        "Boundary demo record: expiring on the warning threshold."),
                certificate("Mobile Release Signing", CertificateType.CODE_SIGNING, "signing.example.com",
                        "DigiCert", "Mobile Team", EnvironmentType.PRODUCTION,
                        today.minusDays(30), today.plusDays(120),
                        "Used to sign Android and iOS release builds."),
                certificate("Employee VPN", CertificateType.TLS_SSL, "vpn.example.com",
                        "DigiCert", "IT Security", EnvironmentType.PRODUCTION,
                        today.minusDays(30), today.plusDays(180),
                        "Renewal handled by network security."),
                certificate("Developer Docs", CertificateType.TLS_SSL, "docs.example.com",
                        "Let's Encrypt", "Developer Experience", EnvironmentType.DEVELOPMENT,
                        today.minusDays(10), today.plusDays(365),
                        "Documentation portal, automatic renewal."),
                certificate("SMTP Relay", CertificateType.EMAIL_SMTP, "smtp.example.com",
                        "Sectigo", "IT Operations", EnvironmentType.QA,
                        today.minusDays(200), today.plusDays(60),
                        "Transactional email relay certificate."));
        repository.saveAll(certificates);
        return certificates.size();
    }

    private static Certificate certificate(String name,
                                           CertificateType type,
                                           String hostname,
                                           String issuer,
                                           String ownerTeam,
                                           EnvironmentType environment,
                                           LocalDate issueDate,
                                           LocalDate expiryDate,
                                           String notes) {
        Certificate certificate = new Certificate();
        certificate.setName(name);
        certificate.setType(type);
        certificate.setHostname(hostname);
        certificate.setIssuer(issuer);
        certificate.setOwnerTeam(ownerTeam);
        certificate.setEnvironment(environment);
        certificate.setIssueDate(issueDate);
        certificate.setExpiryDate(expiryDate);
        certificate.setNotes(notes);
        return certificate;
    }
}
