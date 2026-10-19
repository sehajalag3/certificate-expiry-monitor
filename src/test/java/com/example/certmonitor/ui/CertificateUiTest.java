package com.example.certmonitor.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser journeys against a real Spring Boot instance.
 *
 * Run with:  mvnw.cmd test -Pui            (headless, default)
 *            mvnw.cmd test -Pui -Dui.headless=false   (visible browser)
 *
 * Requires Google Chrome. Selenium Manager resolves the matching driver automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:uitest;DB_CLOSE_DELAY=-1"
})
@DisplayName("Certificate monitor browser journeys")
class CertificateUiTest {

    private static final Logger log = LoggerFactory.getLogger(CertificateUiTest.class);
    private static final Path SCREENSHOT_DIR = Path.of("target", "selenium-screenshots");
    private static final Duration WAIT = Duration.ofSeconds(15);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;

    @RegisterExtension
    final TestExecutionExceptionHandler screenshotOnFailure = new TestExecutionExceptionHandler() {
        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            captureScreenshot(context.getDisplayName());
            throw throwable;
        }
    };

    @BeforeEach
    void setUp() {
        TestRestTemplate resetClient = restTemplate.withRedirects(
                ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW);
        HttpStatusCode status = resetClient.postForEntity("/demo/reset", null, String.class).getStatusCode();
        assertThat(status.is2xxSuccessful() || status.is3xxRedirection())
                .as("Demo data reset before UI test (POST /demo/reset responded %s)", status)
                .isTrue();

        baseUrl = "http://localhost:" + port;
        boolean headless = Boolean.parseBoolean(System.getProperty("ui.headless", "true"));
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--window-size=1440,1000",
                "--disable-gpu",
                "--no-first-run",
                "--disable-search-engine-choice-screen",
                "--remote-allow-origins=*");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, WAIT);
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    @Test
    @DisplayName("Dashboard loads with summary cards and expiry alerts")
    void dashboardLoadsWithSummaryCards() {
        driver.get(baseUrl + "/");

        assertThat(visible("card-total-value").getText()).isEqualTo("10");
        assertThat(visible("card-expired-value").getText()).isEqualTo("2");
        assertThat(driver.findElements(By.cssSelector("[data-testid='alert-row']"))).isNotEmpty();
        assertThat(visible("nav-dashboard").getAttribute("class")).contains("active");
        assertThat(driver.getTitle()).contains("Certificate Expiry Monitor");
    }

    @Test
    @DisplayName("A user can add a certificate and see it on the detail page")
    void addCertificateJourney() {
        LocalDate today = LocalDate.now();
        driver.get(baseUrl + "/certificates/new");

        typeInto("field-name", "Selenium Managed Certificate");
        new Select(visible("field-type")).selectByValue("TLS_SSL");
        typeInto("field-hostname", "selenium.example.com");
        typeInto("field-issuer", "Selenium Test CA");
        typeInto("field-owner", "QA Automation");
        new Select(visible("field-environment")).selectByValue("STAGING");
        setDate("field-issue-date", today.minusDays(10));
        setDate("field-expiry-date", today.plusDays(90));
        typeInto("field-notes", "Created by the Selenium add-certificate journey.");
        click("submit-certificate");

        wait.until(ExpectedConditions.urlMatches(".*/certificates/\\d+.*"));
        assertThat(visible("detail-name").getText()).isEqualTo("Selenium Managed Certificate");
        assertThat(visible("detail-hostname").getText()).isEqualTo("selenium.example.com");
        assertThat(visible("detail-status").getText()).isEqualTo("Valid");
        assertThat(visible("detail-owner").getText()).isEqualTo("QA Automation");
        assertThat(visible("flash-success").getText()).contains("was created");
    }

    @Test
    @DisplayName("A user can edit an existing certificate")
    void editCertificateJourney() {
        driver.get(baseUrl + "/certificates");
        typeInto("search-input", "Employee VPN");
        click("search-submit");

        WebElement row = rowFor("Employee VPN");
        row.findElement(By.cssSelector("[data-testid='row-edit']")).click();
        wait.until(ExpectedConditions.urlMatches(".*/edit"));

        assertThat(visible("field-owner").getAttribute("value")).isEqualTo("IT Security");
        WebElement owner = visible("field-owner");
        owner.clear();
        owner.sendKeys("Selenium QA");
        click("submit-certificate");

        wait.until(ExpectedConditions.urlMatches(".*/certificates/\\d+.*"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(testId("detail-name")));
        assertThat(visible("flash-success").getText()).contains("was updated");
        assertThat(visible("detail-owner").getText()).isEqualTo("Selenium QA");
    }

    @Test
    @DisplayName("Search and status filtering narrow the certificate table")
    void searchAndFilterByStatusJourney() {
        driver.get(baseUrl + "/certificates");
        assertThat(driver.findElements(By.cssSelector("[data-testid='cert-row']"))).hasSize(10);

        typeInto("search-input", "wiki");
        click("search-submit");
        rowFor("Internal Wiki");
        assertThat(driver.findElements(By.cssSelector("[data-testid='cert-row']")).size()).isEqualTo(1);

        click("search-clear");
        wait.until(ExpectedConditions.urlContains("/certificates"));
        wait.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector("[data-testid='cert-row']"), 10));

        new Select(visible("filter-status")).selectByValue("EXPIRED");
        click("search-submit");
        wait.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector("[data-testid='cert-row']"), 2));
        assertThat(driver.findElements(By.cssSelector("[data-testid='cert-row'] .status-badge")))
                .allSatisfy(badge -> assertThat(badge.getText()).isEqualTo("Expired"));
    }

    @Test
    @DisplayName("Validation errors are shown and delete requires confirmation")
    void validationAndDeleteConfirmationJourney() {
        driver.get(baseUrl + "/certificates/new");
        click("submit-certificate");

        assertThat(visible("form-error-summary").isDisplayed()).isTrue();
        assertThat(visible("error-name").getText()).contains("Name is required");
        assertThat(visible("error-hostname").getText()).contains("Hostname");

        driver.get(baseUrl + "/certificates");
        typeInto("search-input", "SMTP Relay");
        click("search-submit");
        WebElement row = rowFor("SMTP Relay");

        row.findElement(By.cssSelector("[data-testid='row-delete']")).click();
        assertThat(visible("delete-cert-name").getText()).isEqualTo("SMTP Relay");
        click("btn-cancel-delete");
        assertThat(visible("detail-name").getText()).isEqualTo("SMTP Relay");

        click("btn-delete");
        click("btn-confirm-delete");
        WebElement deletedFlash = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("flash-success")));
        assertThat(deletedFlash.getText()).contains("was deleted");

        typeInto("search-input", "SMTP Relay");
        click("search-submit");
        assertThat(visible("empty-state").isDisplayed()).isTrue();
    }

    private WebElement rowFor(String name) {
        return wait.until(driver -> {
            try {
                return driver.findElements(By.cssSelector("[data-testid='cert-row']")).stream()
                        .filter(row -> row.findElement(By.cssSelector("[data-testid='cert-name']"))
                                .getText().equals(name))
                        .findFirst()
                        .orElse(null);
            } catch (StaleElementReferenceException ex) {
                return null;
            }
        });
    }

    private WebElement visible(String testId) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(testId(testId)));
    }

    private static By testId(String testId) {
        return By.cssSelector("[data-testid='" + testId + "']");
    }

    private void typeInto(String testId, String value) {
        WebElement field = visible(testId);
        field.clear();
        field.sendKeys(value);
    }

    private void setDate(String testId, LocalDate date) {
        WebElement field = visible(testId);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                        + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                field, date.toString());
    }

    private void click(String testId) {
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(testId(testId)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
        element.click();
    }

    private void captureScreenshot(String displayName) {
        if (driver == null) {
            return;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String safeName = displayName.replaceAll("[^a-zA-Z0-9-_]", "_");
            Files.copy(source.toPath(), SCREENSHOT_DIR.resolve(safeName + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(SCREENSHOT_DIR.resolve(safeName + ".html"), driver.getPageSource());
            log.info("Saved failure screenshot for '{}' to {}", displayName, SCREENSHOT_DIR);
        } catch (Exception ex) {
            log.warn("Could not capture failure screenshot", ex);
        }
    }
}
