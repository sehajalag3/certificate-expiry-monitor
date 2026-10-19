package com.example.certmonitor.web;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.domain.CertificateStatus;
import com.example.certmonitor.domain.CertificateType;
import com.example.certmonitor.domain.EnvironmentType;
import com.example.certmonitor.service.CertificateService;
import com.example.certmonitor.service.CertificateView;
import com.example.certmonitor.service.ExpiryStatusCalculator;
import com.example.certmonitor.web.dto.CertificateForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/certificates")
public class CertificateController {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "name", "hostname", "issuer", "ownerTeam", "type", "environment",
            "issueDate", "expiryDate", "createdAt", "updatedAt");
    private static final List<Integer> PAGE_SIZES = List.of(5, 10, 20, 50);

    private final CertificateService certificateService;
    private final ExpiryStatusCalculator calculator;
    private final AppProperties appProperties;

    public CertificateController(CertificateService certificateService,
                                 ExpiryStatusCalculator calculator,
                                 AppProperties appProperties) {
        this.certificateService = certificateService;
        this.calculator = calculator;
        this.appProperties = appProperties;
    }

    @ModelAttribute
    public void commonAttributes(Model model) {
        model.addAttribute("warningThresholdDays", calculator.warningThresholdDays());
        model.addAttribute("demoEnabled", appProperties.getDemoData().isEnabled());
        model.addAttribute("statuses", CertificateStatus.values());
        model.addAttribute("types", CertificateType.values());
        model.addAttribute("environments", EnvironmentType.values());
        model.addAttribute("pageSizes", PAGE_SIZES);
    }

    @GetMapping
    public String list(@RequestParam(name = "q", defaultValue = "") String query,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String environment,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(defaultValue = "expiryDate") String sort,
                       @RequestParam(defaultValue = "asc") String direction,
                       Model model) {
        CertificateStatus statusFilter = parseEnum(CertificateStatus.class, status);
        CertificateType typeFilter = parseEnum(CertificateType.class, type);
        EnvironmentType environmentFilter = parseEnum(EnvironmentType.class, environment);

        int safePage = Math.max(page, 0);
        int safeSize = PAGE_SIZES.contains(size) ? size : 10;
        String safeSort = SORTABLE_FIELDS.contains(sort) ? sort : "expiryDate";
        Sort.Direction safeDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(safeDirection, safeSort));

        Page<CertificateView> certificates =
                certificateService.search(query, statusFilter, typeFilter, environmentFilter, pageable);

        model.addAttribute("certificates", certificates);
        model.addAttribute("q", query);
        model.addAttribute("statusFilter", statusFilter == null ? "" : statusFilter.name());
        model.addAttribute("typeFilter", typeFilter == null ? "" : typeFilter.name());
        model.addAttribute("environmentFilter", environmentFilter == null ? "" : environmentFilter.name());
        model.addAttribute("sort", safeSort);
        model.addAttribute("direction", safeDirection.name().toLowerCase());
        model.addAttribute("size", safeSize);
        model.addAttribute("activeNav", "certificates");
        model.addAttribute("hasFilters", !query.isBlank() || statusFilter != null
                || typeFilter != null || environmentFilter != null);
        return "certificates/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        CertificateForm form = new CertificateForm();
        form.setType(CertificateType.TLS_SSL);
        form.setEnvironment(EnvironmentType.PRODUCTION);
        form.setIssueDate(LocalDate.now());
        form.setExpiryDate(LocalDate.now().plusYears(1));
        model.addAttribute("form", form);
        model.addAttribute("pageTitle", "Add certificate");
        model.addAttribute("activeNav", "add");
        return "certificates/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CertificateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add certificate");
            model.addAttribute("activeNav", "add");
            return "certificates/form";
        }
        CertificateView created = certificateService.create(form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Certificate \"" + created.getName() + "\" was created.");
        return "redirect:/certificates/" + created.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("certificate", certificateService.findById(id));
        model.addAttribute("activeNav", "certificates");
        return "certificates/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CertificateView certificate = certificateService.findById(id);
        model.addAttribute("form", certificateService.toForm(id));
        model.addAttribute("pageTitle", "Edit certificate");
        model.addAttribute("certificateId", id);
        model.addAttribute("certificateName", certificate.getName());
        model.addAttribute("activeNav", "certificates");
        return "certificates/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") CertificateForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        CertificateView existing = certificateService.findById(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit certificate");
            model.addAttribute("certificateId", id);
            model.addAttribute("certificateName", existing.getName());
            model.addAttribute("activeNav", "certificates");
            return "certificates/form";
        }
        CertificateView updated = certificateService.update(id, form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Certificate \"" + updated.getName() + "\" was updated.");
        return "redirect:/certificates/" + updated.getId();
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirmation(@PathVariable Long id, Model model) {
        model.addAttribute("certificate", certificateService.findById(id));
        model.addAttribute("activeNav", "certificates");
        return "certificates/delete-confirm";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        CertificateView certificate = certificateService.findById(id);
        certificateService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Certificate \"" + certificate.getName() + "\" was deleted.");
        return "redirect:/certificates";
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
