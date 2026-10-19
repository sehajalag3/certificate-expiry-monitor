package com.example.certmonitor.repository;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateStatus;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.service.CertificateService;
import com.example.certmonitor.service.CertificateView;
import com.example.certmonitor.service.ExpiryStatusCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("Certificate persistence and search specifications")
class CertificateRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Autowired
    private CertificateRepository repository;

    private CertificateService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setWarningThresholdDays(30);
        service = new CertificateService(repository, new ExpiryStatusCalculator(properties), FIXED_CLOCK);
        repository.deleteAll();
        repository.saveAll(List.of(
                certificate("Expired Gateway", "api.expired.example.com", CertificateType.TLS_SSL,
                        EnvironmentType.PRODUCTION, "Acme Platform", TODAY.minusDays(3)),
                certificate("Soon Wiki", "wiki.example.com", CertificateType.TLS_SSL,
                        EnvironmentType.STAGING, "Acme IT", TODAY),
                certificate("Soon Billing", "billing.example.com", CertificateType.DOMAIN,
                        EnvironmentType.PRODUCTION, "Finance", TODAY.plusDays(30)),
                certificate("Valid VPN", "vpn.example.com", CertificateType.TLS_SSL,
                        EnvironmentType.PRODUCTION, "Security", TODAY.plusDays(31))));
    }

    @Test
    @DisplayName("Date range counts match the status boundaries")
    void countsByDateRange() {
        assertThat(repository.countByExpiryDateBefore(TODAY)).isEqualTo(1);
        assertThat(repository.countByExpiryDateBetween(TODAY, TODAY.plusDays(30))).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Status filter uses inclusive boundary dates")
    void statusFilter() {
        Page<CertificateView> expired = service.search(null, CertificateStatus.EXPIRED, null, null,
                PageRequest.of(0, 10));
        Page<CertificateView> soon = service.search(null, CertificateStatus.EXPIRING_SOON, null, null,
                PageRequest.of(0, 10));
        Page<CertificateView> valid = service.search(null, CertificateStatus.VALID, null, null,
                PageRequest.of(0, 10));

        assertThat(expired.getContent()).extracting(CertificateView::getName)
                .containsExactly("Expired Gateway");
        assertThat(soon.getContent()).extracting(CertificateView::getName)
                .containsExactlyInAnyOrder("Soon Wiki", "Soon Billing");
        assertThat(valid.getContent()).extracting(CertificateView::getName)
                .containsExactly("Valid VPN");
    }

    @Test
    @DisplayName("Search matches name, hostname, issuer and owner case-insensitively")
    void searchMatchesMultipleFields() {
        Page<CertificateView> byOwner = service.search("acme", null, null, null, PageRequest.of(0, 10));
        Page<CertificateView> byHostname = service.search("vpn.example", null, null, null, PageRequest.of(0, 10));

        assertThat(byOwner.getTotalElements()).isEqualTo(2);
        assertThat(byHostname.getContent()).extracting(CertificateView::getName).containsExactly("Valid VPN");
    }

    @Test
    @DisplayName("Type and environment filters combine")
    void typeAndEnvironmentFilters() {
        Page<CertificateView> result = service.search(null, null, CertificateType.TLS_SSL,
                EnvironmentType.PRODUCTION, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(CertificateView::getName)
                .containsExactlyInAnyOrder("Expired Gateway", "Valid VPN");
    }

    @Test
    @DisplayName("Pagination and sorting are applied to search results")
    void paginationAndSorting() {
        Page<CertificateView> firstPage = service.search(null, null, null, null,
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "expiryDate")));
        Page<CertificateView> secondPage = service.search(null, null, null, null,
                PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "expiryDate")));

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getContent()).extracting(CertificateView::getName)
                .containsExactly("Expired Gateway", "Soon Wiki");
        assertThat(secondPage.getContent()).extracting(CertificateView::getName)
                .containsExactly("Soon Billing", "Valid VPN");
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    private static Certificate certificate(String name,
                                           String hostname,
                                           CertificateType type,
                                           EnvironmentType environment,
                                           String owner,
                                           LocalDate expiryDate) {
        Certificate certificate = new Certificate();
        certificate.setName(name);
        certificate.setType(type);
        certificate.setHostname(hostname);
        certificate.setIssuer("Test Issuer");
        certificate.setOwnerTeam(owner);
        certificate.setEnvironment(environment);
        certificate.setIssueDate(TODAY.minusDays(200));
        certificate.setExpiryDate(expiryDate);
        certificate.setNotes("Seed for repository test");
        return certificate;
    }
}
