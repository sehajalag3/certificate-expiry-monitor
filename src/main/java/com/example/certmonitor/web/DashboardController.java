package com.example.certmonitor.web;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.service.CertificateService;
import com.example.certmonitor.service.DashboardCounts;
import com.example.certmonitor.service.ExpiryStatusCalculator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class DashboardController {

    private final CertificateService certificateService;
    private final ExpiryStatusCalculator calculator;
    private final AppProperties appProperties;

    public DashboardController(CertificateService certificateService,
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
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        DashboardCounts counts = certificateService.dashboardCounts();
        model.addAttribute("counts", counts);
        model.addAttribute("alerts", certificateService.findExpiryAlerts());
        model.addAttribute("activeNav", "dashboard");
        return "dashboard";
    }

    @GetMapping("/exceptions")
    public String exceptions(Model model) {
        DashboardCounts counts = certificateService.dashboardCounts();
        model.addAttribute("counts", counts);
        model.addAttribute("alerts", certificateService.findExpiryAlerts());
        model.addAttribute("activeNav", "exceptions");
        return "exceptions";
    }
}
