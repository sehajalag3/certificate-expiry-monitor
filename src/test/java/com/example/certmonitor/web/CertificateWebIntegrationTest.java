package com.example.certmonitor.web;

import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Certificate web application")
class CertificateWebIntegrationTest {

    private final LocalDate today = LocalDate.now();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificateRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                certificate("Expired Gateway", "api.expired.example.com", "Platform Engineering",
                        CertificateType.TLS_SSL, EnvironmentType.PRODUCTION, today.minusDays(3)),
                certificate("Expiring Wiki", "wiki.example.com", "IT Operations",
                        CertificateType.TLS_SSL, EnvironmentType.STAGING, today),
                certificate("Valid VPN", "vpn.example.com", "IT Security",
                        CertificateType.TLS_SSL, EnvironmentType.PRODUCTION, today.plusDays(100))));
    }

    @Test
    @DisplayName("Dashboard renders summary cards with correct counts")
    void dashboardCounts() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(containsString("data-testid=\"card-total-value\">3<")))
                .andExpect(content().string(containsString("data-testid=\"card-valid-value\">1<")))
                .andExpect(content().string(containsString("data-testid=\"card-expiring-value\">1<")))
                .andExpect(content().string(containsString("data-testid=\"card-expired-value\">1<")))
                .andExpect(content().string(containsString("data-testid=\"alert-row\"")))
                .andExpect(content().string(containsString("Expired Gateway")));
    }

    @Test
    @DisplayName("Search filters the certificate table by hostname")
    void searchByHostname() throws Exception {
        mockMvc.perform(get("/certificates").param("q", "vpn.example"))
                .andExpect(status().isOk())
                .andExpect(view().name("certificates/list"))
                .andExpect(content().string(containsString("Valid VPN")))
                .andExpect(content().string(not(containsString("Expired Gateway"))));
    }

    @Test
    @DisplayName("Status filter shows only matching certificates")
    void filterByStatus() throws Exception {
        mockMvc.perform(get("/certificates").param("status", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Expired Gateway")))
                .andExpect(content().string(not(containsString("Valid VPN"))))
                .andExpect(content().string(not(containsString("Expiring Wiki"))));
    }

    @Test
    @DisplayName("Type and environment filters can be combined")
    void filterByTypeAndEnvironment() throws Exception {
        mockMvc.perform(get("/certificates")
                        .param("type", "TLS_SSL")
                        .param("environment", "STAGING"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Expiring Wiki")))
                .andExpect(content().string(not(containsString("Valid VPN"))));
    }

    @Test
    @DisplayName("Invalid filters are ignored instead of failing the page")
    void invalidFilterFallsBack() throws Exception {
        mockMvc.perform(get("/certificates").param("status", "NOT_A_STATUS").param("type", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-testid=\"cert-row\"")));
    }

    @Test
    @DisplayName("Valid create request redirects to the detail page and persists data")
    void createCertificate() throws Exception {
        mockMvc.perform(post("/certificates")
                        .param("name", "New Selenium Certificate")
                        .param("type", "TLS_SSL")
                        .param("hostname", "created.example.com")
                        .param("issuer", "Let's Encrypt")
                        .param("ownerTeam", "QA Automation")
                        .param("environment", "QA")
                        .param("issueDate", today.minusDays(5).toString())
                        .param("expiryDate", today.plusDays(90).toString())
                        .param("notes", "Created through the web form"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/certificates/*"));

        assertThat(repository.findAll())
                .extracting(Certificate::getName)
                .contains("New Selenium Certificate");
    }

    @Test
    @DisplayName("Invalid create request returns the form with field errors")
    void invalidCreateIsRejected() throws Exception {
        mockMvc.perform(post("/certificates")
                        .param("name", "")
                        .param("type", "TLS_SSL")
                        .param("hostname", "not a hostname")
                        .param("ownerTeam", "")
                        .param("environment", "PRODUCTION")
                        .param("issueDate", today.toString())
                        .param("expiryDate", today.minusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("certificates/form"))
                .andExpect(content().string(containsString("Name is required")))
                .andExpect(content().string(containsString("Owner / team is required")))
                .andExpect(content().string(containsString("Hostname must be a valid domain")))
                .andExpect(content().string(containsString("Expiry date must be after the issue date")))
                .andExpect(content().string(containsString("data-testid=\"form-error-summary\"")));

        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Edit form is populated and update persists the changes")
    void editCertificate() throws Exception {
        Certificate certificate = repository.findAll().stream()
                .filter(c -> c.getName().equals("Valid VPN"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/certificates/{id}/edit", certificate.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("certificates/form"))
                .andExpect(content().string(containsString("IT Security")));

        mockMvc.perform(post("/certificates/{id}", certificate.getId())
                        .param("name", "Valid VPN")
                        .param("type", "TLS_SSL")
                        .param("hostname", "vpn.example.com")
                        .param("issuer", "DigiCert")
                        .param("ownerTeam", "SRE")
                        .param("environment", "PRODUCTION")
                        .param("issueDate", today.minusDays(40).toString())
                        .param("expiryDate", today.plusDays(120).toString())
                        .param("notes", "Updated by test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/certificates/*"));

        assertThat(repository.findById(certificate.getId()).orElseThrow().getOwnerTeam()).isEqualTo("SRE");
    }

    @Test
    @DisplayName("Delete requires a confirmation page and then removes the record")
    void deleteCertificate() throws Exception {
        Certificate certificate = repository.findAll().stream()
                .filter(c -> c.getName().equals("Expiring Wiki"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/certificates/{id}/delete", certificate.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("certificates/delete-confirm"))
                .andExpect(content().string(containsString("Expiring Wiki")))
                .andExpect(content().string(containsString("data-testid=\"btn-confirm-delete\"")));

        mockMvc.perform(post("/certificates/{id}/delete", certificate.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/certificates"));

        assertThat(repository.findById(certificate.getId())).isEmpty();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Missing certificate returns a safe 404 page")
    void missingCertificate404() throws Exception {
        mockMvc.perform(get("/certificates/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(content().string(containsString("Not found")));
    }

    @Test
    @DisplayName("Exception view lists expired and expiring certificates")
    void exceptionView() throws Exception {
        mockMvc.perform(get("/exceptions"))
                .andExpect(status().isOk())
                .andExpect(view().name("exceptions"))
                .andExpect(content().string(containsString("Expired Gateway")))
                .andExpect(content().string(containsString("Expiring Wiki")))
                .andExpect(content().string(not(containsString("Valid VPN"))));
    }

    @Test
    @DisplayName("Actuator health endpoint reports UP")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    private static Certificate certificate(String name,
                                           String hostname,
                                           String owner,
                                           CertificateType type,
                                           EnvironmentType environment,
                                           LocalDate expiryDate) {
        Certificate certificate = new Certificate();
        certificate.setName(name);
        certificate.setType(type);
        certificate.setHostname(hostname);
        certificate.setIssuer("Test Issuer");
        certificate.setOwnerTeam(owner);
        certificate.setEnvironment(environment);
        certificate.setIssueDate(LocalDate.now().minusDays(200));
        certificate.setExpiryDate(expiryDate);
        certificate.setNotes("Integration test fixture");
        return certificate;
    }
}
