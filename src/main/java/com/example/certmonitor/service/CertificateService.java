package com.example.certmonitor.service;

import com.example.certmonitor.domain.Certificate;
import com.example.certmonitor.domain.CertificateStatus;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.repository.CertificateRepository;
import com.example.certmonitor.web.dto.CertificateForm;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class CertificateService {

    private final CertificateRepository repository;
    private final ExpiryStatusCalculator calculator;
    private final Clock clock;

    public CertificateService(CertificateRepository repository, ExpiryStatusCalculator calculator, Clock clock) {
        this.repository = repository;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<CertificateView> search(String query,
                                        CertificateStatus status,
                                        CertificateType type,
                                        EnvironmentType environment,
                                        Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        int threshold = calculator.warningThresholdDays();
        Specification<Certificate> spec = (root, cq, cb) -> cb.conjunction();

        String normalized = normalize(query);
        if (normalized != null) {
            String like = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("hostname")), like),
                    cb.like(cb.lower(root.get("issuer")), like),
                    cb.like(cb.lower(root.get("ownerTeam")), like)));
        }
        if (type != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("type"), type));
        }
        if (environment != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("environment"), environment));
        }
        if (status != null) {
            LocalDate soonEnd = today.plusDays(threshold);
            spec = spec.and((root, cq, cb) -> switch (status) {
                case EXPIRED -> cb.lessThan(root.get("expiryDate"), today);
                case EXPIRING_SOON -> cb.and(
                        cb.greaterThanOrEqualTo(root.get("expiryDate"), today),
                        cb.lessThanOrEqualTo(root.get("expiryDate"), soonEnd));
                case VALID -> cb.greaterThan(root.get("expiryDate"), soonEnd);
            });
        }

        return repository.findAll(spec, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public CertificateView findById(Long id) {
        return repository.findById(id)
                .map(this::toView)
                .orElseThrow(() -> new CertificateNotFoundException(id));
    }

    public CertificateView create(CertificateForm form) {
        Certificate certificate = new Certificate();
        apply(form, certificate);
        return toView(repository.save(certificate));
    }

    public CertificateView update(Long id, CertificateForm form) {
        Certificate certificate = repository.findById(id)
                .orElseThrow(() -> new CertificateNotFoundException(id));
        apply(form, certificate);
        return toView(repository.save(certificate));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new CertificateNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DashboardCounts dashboardCounts() {
        LocalDate today = LocalDate.now(clock);
        long total = repository.count();
        long expired = repository.countByExpiryDateBefore(today);
        long expiringSoon = repository.countByExpiryDateBetween(today, today.plusDays(calculator.warningThresholdDays()));
        long valid = Math.max(0, total - expired - expiringSoon);
        return new DashboardCounts(total, valid, expiringSoon, expired);
    }

    @Transactional(readOnly = true)
    public List<CertificateView> findExpiryAlerts() {
        LocalDate cutoff = LocalDate.now(clock).plusDays(calculator.warningThresholdDays());
        return repository.findByExpiryDateLessThanEqualOrderByExpiryDateAsc(cutoff).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public CertificateForm toForm(Long id) {
        Certificate certificate = repository.findById(id)
                .orElseThrow(() -> new CertificateNotFoundException(id));
        CertificateForm form = new CertificateForm();
        form.setName(certificate.getName());
        form.setType(certificate.getType());
        form.setHostname(certificate.getHostname());
        form.setIssuer(certificate.getIssuer());
        form.setOwnerTeam(certificate.getOwnerTeam());
        form.setEnvironment(certificate.getEnvironment());
        form.setIssueDate(certificate.getIssueDate());
        form.setExpiryDate(certificate.getExpiryDate());
        form.setNotes(certificate.getNotes());
        return form;
    }

    private CertificateView toView(Certificate certificate) {
        LocalDate today = LocalDate.now(clock);
        CertificateStatus status = calculator.statusFor(certificate.getExpiryDate(), today);
        int daysRemaining = calculator.daysRemaining(certificate.getExpiryDate(), today);
        return CertificateView.from(certificate, status, daysRemaining);
    }

    private void apply(CertificateForm form, Certificate certificate) {
        certificate.setName(trimToNull(form.getName()));
        certificate.setType(form.getType());
        certificate.setHostname(trimToNull(form.getHostname()));
        certificate.setIssuer(trimToNull(form.getIssuer()));
        certificate.setOwnerTeam(trimToNull(form.getOwnerTeam()));
        certificate.setEnvironment(form.getEnvironment());
        certificate.setIssueDate(form.getIssueDate());
        certificate.setExpiryDate(form.getExpiryDate());
        certificate.setNotes(trimToNull(form.getNotes()));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Kept for potential future specification composition. */
    static Specification<Certificate> append(Specification<Certificate> base, Specification<Certificate> extra) {
        List<Specification<Certificate>> specs = new ArrayList<>();
        if (base != null) {
            specs.add(base);
        }
        if (extra != null) {
            specs.add(extra);
        }
        return specs.stream().reduce((a, b) -> a.and(b)).orElse(null);
    }
}
