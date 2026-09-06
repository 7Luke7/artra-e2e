package com.artra.e2e.base;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns one browser session for one test invocation: creates it, injects it and
 * its wait as method parameters, captures diagnostics if the test failed, and
 * quits it.
 *
 * The driver lives in JUnit's invocation-scoped Store, not in a ThreadLocal.
 * JUnit scopes that Store per invocation for free, so concurrent invocations
 * are isolated with no bookkeeping and nothing leaks when the ForkJoinPool
 * reuses a thread for the next test - the failure mode a ThreadLocal produces
 * here is a test silently driving the previous test's dead session.
 */
public class DriverLifecycle
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger log = LoggerFactory.getLogger(DriverLifecycle.class);
    private static final Namespace NAMESPACE = Namespace.create(DriverLifecycle.class);
    private static final String DRIVER_KEY = "driver";
    private static final String WAIT_KEY = "wait";
    private static final TestConfig CONFIG = ConfigProvider.get();

    private final String browser;
    private final boolean record;

    public DriverLifecycle(String browser, boolean record) {
        this.browser = browser;
        this.record = record;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        String testName = sessionName(context);
        log.info("▶ {} [{}] starting (recordVideo={})", testName, browser, record);

        RemoteWebDriver driver = DriverFactory.create(browser, testName, record);
        driver.manage().timeouts()
                .implicitlyWait(Duration.ofSeconds(CONFIG.implicitWaitSeconds()))
                .pageLoadTimeout(Duration.ofSeconds(CONFIG.pageLoadTimeoutSeconds()));

        WebDriverWait wait =
                new WebDriverWait(driver, Duration.ofSeconds(CONFIG.explicitWaitSeconds()));

        // No-op unless the app is being served through a tunnel, which is not
        // the default. See TunnelInterstitial for what it is getting past.
        TunnelInterstitial.accept(driver, CONFIG.baseUrl());

        Store store = context.getStore(NAMESPACE);
        store.put(DRIVER_KEY, driver);
        store.put(WAIT_KEY, wait);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Store store = context.getStore(NAMESPACE);
        RemoteWebDriver driver = store.remove(DRIVER_KEY, RemoteWebDriver.class);
        store.remove(WAIT_KEY);

        if (driver == null) {
            return;
        }

        try {
            context.getExecutionException().ifPresent(failure ->
                    FailureDiagnostics.capture(driver, methodName(context), browser, failure));
        } finally {
            driver.quit();
            log.info("■ {} [{}] finished", methodName(context), browser);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == WebDriver.class
                || type == RemoteWebDriver.class
                || type == WebDriverWait.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext) {
        Store store = extensionContext.getStore(NAMESPACE);

        if (parameterContext.getParameter().getType() == WebDriverWait.class) {
            return store.get(WAIT_KEY, WebDriverWait.class);
        }

        RemoteWebDriver driver = store.get(DRIVER_KEY, RemoteWebDriver.class);
        if (driver == null) {
            throw new ParameterResolutionException(
                    "No WebDriver for this invocation - beforeEach did not run");
        }
        return driver;
    }

    /** Shown in the Grid console, and used by the recorder to name the .mp4. */
    private static String sessionName(ExtensionContext context) {
        return sanitise(methodName(context) + "-" + context.getDisplayName());
    }

    private static String methodName(ExtensionContext context) {
        return context.getTestMethod()
                .map(Method::getName)
                .orElseGet(() -> context.getParent()
                        .map(ExtensionContext::getDisplayName)
                        .orElse("test"));
    }

    /** Also used for the diagnostics directory name, so anything that is not
     *  filesystem-safe has to go. */
    private static String sanitise(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "test";
        }
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
