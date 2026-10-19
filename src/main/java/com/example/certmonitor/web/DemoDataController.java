package com.example.certmonitor.web;

import com.example.certmonitor.config.AppProperties;
import com.example.certmonitor.service.DemoDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataController {

    private final DemoDataService demoDataService;
    private final AppProperties appProperties;

    public DemoDataController(DemoDataService demoDataService, AppProperties appProperties) {
        this.demoDataService = demoDataService;
        this.appProperties = appProperties;
    }

    @PostMapping("/demo/reset")
    public String reset(RedirectAttributes redirectAttributes) {
        if (!appProperties.getDemoData().isEnabled()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Demo data is disabled in this environment.");
            return "redirect:/";
        }
        int seeded = demoDataService.reset();
        redirectAttributes.addFlashAttribute("successMessage",
                "Demo data reset. " + seeded + " sample certificates are available.");
        return "redirect:/";
    }
}
