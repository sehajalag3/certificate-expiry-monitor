package com.example.certmonitor.web;

import com.example.certmonitor.repository.CertificateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:demodata;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@DisplayName("Demo data seeding and reset")
class DemoDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CertificateRepository repository;

    @Test
    @DisplayName("Demo seed creates ten certificates with mixed statuses")
    void seedCreatesDemoData() {
        assertThat(repository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("Dashboard shows demo alerts and a reset action")
    void dashboardShowsDemoAlerts() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-testid=\"card-total-value\">10<")))
                .andExpect(content().string(containsString("data-testid=\"btn-reset-demo\"")))
                .andExpect(content().string(containsString("Corporate Website")));
    }

    @Test
    @DisplayName("POST /demo/reset replaces certificate data with the demo seed")
    void resetDemoData() throws Exception {
        repository.deleteAll();

        mockMvc.perform(post("/demo/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(repository.count()).isEqualTo(10);
    }
}
