package com.artra.e2e.base;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What gets captured when a test fails, before the driver is thrown away.
 *
 * A failed assertion on its own ("expected true but was false") is close to
 * useless three days later in a CI log, so each failure leaves a small bundle
 * under {@code target/diagnostics/<test>-<browser>/}:
 *
 * <ul>
 *   <li>{@code screenshot.png} - what the browser was actually showing;</li>
 *   <li>{@code page.html} - the rendered DOM, which answers "was the element
 *       missing or just invisible?" without a re-run;</li>
 *   <li>{@code context.txt} - URL, title and the failure itself;</li>
 *   <li>{@code console.log} - browser console errors, present on Chrome and
 *       Edge (Firefox's geckodriver does not expose the browser log).</li>
 * </ul>
 *
 * Capture happens in the extension's afterEach rather than in a TestWatcher:
 * JUnit runs afterEach callbacks <em>before</em> testFailed, so a watcher would
 * only ever see an already-quit driver.
 */
final class FailureDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(FailureDiagnostics.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HHmmss");

    private FailureDiagnostics() {
    }

    static void capture(WebDriver driver, String testName, String browser, Throwable failure) {
        Path dir = Path.of(ConfigProvider.get().diagnosticsDir())
                .resolve(testName + "-" + browser + "-" + LocalDateTime.now().format(STAMP));

        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Could not create the diagnostics directory {}: {}", dir, e.toString());
            return;
        }

        // Each capture is attempted independently: a driver that has lost its
        // session still yields a useful context.txt, and a screenshot is worth
        // having even when reading the page source throws.
        writeScreenshot(driver, dir);
        write(dir.resolve("page.html"), () -> driver.getPageSource());
        write(dir.resolve("context.txt"), () -> context(driver, testName, browser, failure));
        writeConsoleLog(driver, dir);

        log.error("■ {} [{}] failed - diagnostics in {}", testName, browser, dir.toAbsolutePath());
    }

    private static void writeScreenshot(WebDriver driver, Path dir) {
        if (!(driver instanceof TakesScreenshot shooter)) {
            return;
        }
        try {
            Files.write(dir.resolve("screenshot.png"), shooter.getScreenshotAs(OutputType.BYTES));
        } catch (Exception e) {
            log.warn("Screenshot capture failed: {}", e.toString());
        }
    }

    private static String context(WebDriver driver, String testName, String browser, Throwable failure) {
        StringBuilder out = new StringBuilder()
                .append("test    : ").append(testName).append('\n')
                .append("browser : ").append(browser).append('\n')
                .append("url     : ").append(safely(driver::getCurrentUrl)).append('\n')
                .append("title   : ").append(safely(driver::getTitle)).append('\n')
                .append("\n--- failure ---\n");

        for (Throwable t = failure; t != null; t = t.getCause()) {
            out.append(t).append('\n');
            if (t.getCause() == t) {
                break;
            }
        }
        return out.toString();
    }

    private static void writeConsoleLog(WebDriver driver, Path dir) {
        try {
            List<LogEntry> entries = driver.manage().logs().get(LogType.BROWSER).getAll();
            if (entries.isEmpty()) {
                return;
            }
            StringBuilder out = new StringBuilder();
            entries.forEach(entry -> out.append(entry).append('\n'));
            Files.writeString(dir.resolve("console.log"), out.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Firefox does not implement the browser log endpoint. That is
            // expected, not a problem worth a warning on every Firefox failure.
            log.debug("Browser console log unavailable: {}", e.toString());
        }
    }

    private static void write(Path target, ThrowingSupplier<String> content) {
        try {
            Files.writeString(target, String.valueOf(content.get()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not write {}: {}", target.getFileName(), e.toString());
        }
    }

    private static String safely(ThrowingSupplier<String> supplier) {
        try {
            return String.valueOf(supplier.get());
        } catch (Exception e) {
            return "(unavailable: " + e.getClass().getSimpleName() + ")";
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
