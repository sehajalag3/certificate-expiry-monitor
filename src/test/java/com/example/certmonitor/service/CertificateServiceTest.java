package com.example.certmonitor.service;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateStatus;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.repository.CertificateRepository;
import com.example.certmonitor.web.dto.CertificateForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Certificate service business logic")
class CertificateServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock
    private CertificateRepository repository;

    private CertificateService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setWarningThresholdDays(30);
        service = new CertificateService(repository, new ExpiryStatusCalculator(properties), FIXED_CLOCK);
    }

    @Test
    @DisplayName("Create persists the certificate and computes its status")
    void createPersistsCertificate() {
        when(repository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CertificateView view = service.create(form("Expiring certificate", TODAY, TODAY.plusDays(10)));

        assertThat(view.getName()).isEqualTo("Expiring certificate");
        assertThat(view.getStatus()).isEqualTo(CertificateStatus.EXPIRING_SOON);
        assertThat(view.getDaysRemaining()).isEqualTo(10);
        assertThat(view.getOwnerTeam()).isEqualTo("Platform Engineering");
        verify(repository).save(any(Certificate.class));
    }

    @Test
    @DisplayName("Search maps entities to views with status and days remaining")
    void searchMapsViews() {
        Certificate certificate = certificate("Legacy VPN", "vpn.example.com", TODAY.minusDays(1));
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(certificate), pageable, 1));

        Page<CertificateView> page = service.search(null, CertificateStatus.EXPIRED,
                CertificateType.TLS_SSL, EnvironmentType.PRODUCTION, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        CertificateView view = page.getContent().getFirst();
        assertThat(view.getStatus()).isEqualTo(CertificateStatus.EXPIRED);
        assertThat(view.getDaysRemaining()).isNegative();
    }

    @Test
    @DisplayName("Search with no results returns an empty page")
    void searchEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));

        assertThat(service.search("nothing", null, null, null, pageable)).isEmpty();
    }

    @Test
    @DisplayName("Find by id throws when the certificate does not exist")
    void findByIdMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(CertificateNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Update applies form values to the existing record")
    void updateExisting() {
        Certificate certificate = certificate("Old name", "old.example.com", TODAY.plusDays(100));
        when(repository.findById(1L)).thenReturn(Optional.of(certificate));
        when(repository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CertificateForm form = form("New name", TODAY, TODAY.plusDays(100));
        CertificateView updated = service.update(1L, form);

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(certificate.getOwnerTeam()).isEqualTo("Platform Engineering");
        assertThat(certificate.getHostname()).isEqualTo("cert.example.com");
    }

    @Test
    @DisplayName("Update of a missing certificate throws")
    void updateMissing() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, form("Name", TODAY, TODAY.plusDays(1))))
                .isInstanceOf(CertificateNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Delete requires an existing certificate")
    void deleteMissing() {
        when(repository.existsById(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(CertificateNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Dashboard counts are derived from repository date queries")
    void dashboardCounts() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByExpiryDateBefore(TODAY)).thenReturn(2L);
        when(repository.countByExpiryDateBetween(TODAY, TODAY.plusDays(30))).thenReturn(3L);

        DashboardCounts counts = service.dashboardCounts();

        assertThat(counts.getTotal()).isEqualTo(10);
        assertThat(counts.getExpired()).isEqualTo(2);
        assertThat(counts.getExpiringSoon()).isEqualTo(3);
        assertThat(counts.getValid()).isEqualTo(5);
        assertThat(counts.getExpiredPercent()).isEqualTo(20);
    }

    @Test
    @DisplayName("Expiry alerts include expired and soon-to-expire records")
    void expiryAlerts() {
        Certificate expired = certificate("Expired", "expired.example.com", TODAY.minusDays(4));
        Certificate soon = certificate("Soon", "soon.example.com", TODAY.plusDays(4));
        when(repository.findByExpiryDateLessThanEqualOrderByExpiryDateAsc(TODAY.plusDays(30)))
                .thenReturn(List.of(expired, soon));

        List<CertificateView> alerts = service.findExpiryAlerts();

        assertThat(alerts).hasSize(2);
        assertThat(alerts.getFirst().getStatus()).isEqualTo(CertificateStatus.EXPIRED);
        assertThat(alerts.getLast().getStatus()).isEqualTo(CertificateStatus.EXPIRING_SOON);
    }

    @Test
    @DisplayName("Editing form is populated from the stored entity")
    void toForm() {
        Certificate certificate = certificate("Stored", "stored.example.com", TODAY.plusDays(50));
        when(repository.findById(3L)).thenReturn(Optional.of(certificate));

        CertificateForm form = service.toForm(3L);

        assertThat(form.getName()).isEqualTo("Stored");
        assertThat(form.getHostname()).isEqualTo("stored.example.com");
        assertThat(form.getType()).isEqualTo(CertificateType.TLS_SSL);
        assertThat(form.getEnvironment()).isEqualTo(EnvironmentType.PRODUCTION);
    }

    private static CertificateForm form(String name, LocalDate issueDate, LocalDate expiryDate) {
        CertificateForm form = new CertificateForm();
        form.setName(name);
        form.setType(CertificateType.TLS_SSL);
        form.setHostname("cert.example.com");
        form.setIssuer("Let's Encrypt");
        form.setOwnerTeam("Platform Engineering");
        form.setEnvironment(EnvironmentType.PRODUCTION);
        form.setIssueDate(issueDate);
        form.setExpiryDate(expiryDate);
        form.setNotes("Notes");
        return form;
    }

    private static Certificate certificate(String name, String hostname, LocalDate expiryDate) {
        Certificate certificate = new Certificate();
        certificate.setName(name);
        certificate.setType(CertificateType.TLS_SSL);
        certificate.setHostname(hostname);
        certificate.setIssuer("DigiCert");
        certificate.setOwnerTeam("Platform Engineering");
        certificate.setEnvironment(EnvironmentType.PRODUCTION);
        certificate.setIssueDate(TODAY.minusDays(100));
        certificate.setExpiryDate(expiryDate);
        return certificate;
    }
}
