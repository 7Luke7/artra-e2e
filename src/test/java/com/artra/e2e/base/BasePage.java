package com.artra.e2e.base;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.LoadableComponent;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for every page object.
 *
 * A page is identified by two things: the route it lives at and a
 * <b>marker</b> element that only that page renders. Both are needed, because
 * either alone lies on this app - Artra is client-routed, so the URL changes
 * before the new view is painted, and several views share a header, so a
 * marker alone can match while the wrong route is showing.
 *
 * Extending LoadableComponent gives {@code get()}: navigate only if we are not
 * already there. That keeps a test that walks Login -> Verify -> Landing from
 * throwing away the session it just established by re-navigating to a page the
 * browser is already on.
 */
public abstract class BasePage<T extends BasePage<T>> extends LoadableComponent<T> {

    protected final TestConfig config = ConfigProvider.get();
    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String path;
    private final By marker;

    /**
     * @param path   route this page lives at, e.g. {@code "/login"}. Pages that
     *               are reached only through a flow (the verification screens)
     *               still declare it, because it is what a failure message
     *               needs to say where the browser ended up instead.
     * @param marker locator for an element unique to this page
     */
    protected BasePage(WebDriver driver, WebDriverWait wait, String path, By marker) {
        this.driver = driver;
        this.wait = wait;
        this.path = path;
        this.marker = marker;
        PageFactory.initElements(driver, this);
    }

    // ------------------------------------------------------ LoadableComponent --

    @Override
    protected void load() {
        String url = url(path);
        log.info("▶ Navigating to {}", url);
        driver.get(url);
    }

    @Override
    protected void isLoaded() throws Error {
        if (!isOnPath()) {
            throw new AssertionError(
                    "Expected to be on " + path + " but the browser is at " + currentPath());
        }
        if (!Interactions.isPresent(driver, marker)) {
            throw new AssertionError(
                    "The marker for " + getClass().getSimpleName() + " (" + marker
                            + ") is not on screen at " + currentPath());
        }
    }

    /**
     * Waits for this page instead of failing immediately, and is what a test
     * calls after an action that navigates.
     *
     * {@code get()} would work too, but on a failure it re-runs {@code load()},
     * navigating away from the very page whose state made the test fail - and
     * the diagnostics screenshot then shows a freshly loaded page rather than
     * the broken one.
     */
    @SuppressWarnings("unchecked")
    public T waitUntilLoaded() {
        try {
            wait.until(d -> isOnPath() && Interactions.isPresent(d, marker));
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    getClass().getSimpleName() + " never appeared. Expected path " + path
                            + ", browser is at " + currentPath() + " titled '" + driver.getTitle() + "'", e);
        }
        return (T) this;
    }

    // -------------------------------------------------------------- location --

    public String path() {
        return path;
    }

    public String url(String route) {
        String base = config.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + route;
    }

    /** Path plus query of the current URL - what the app's own routing sees. */
    public String currentPath() {
        String current = driver.getCurrentUrl();
        int schemeEnd = current.indexOf("://");
        if (schemeEnd < 0) {
            return current;
        }
        int pathStart = current.indexOf('/', schemeEnd + 3);
        return pathStart < 0 ? "/" : current.substring(pathStart);
    }

    protected boolean isOnPath() {
        String current = currentPath();
        int query = current.indexOf('?');
        String withoutQuery = query < 0 ? current : current.substring(0, query);
        // Trailing slashes differ between a typed URL and a client-side
        // navigation, and no assertion here cares about the difference.
        return normalise(withoutQuery).equals(normalise(path));
    }

    private static String normalise(String route) {
        if (route.length() > 1 && route.endsWith("/")) {
            return route.substring(0, route.length() - 1);
        }
        return route;
    }

    /** Waits until the URL carries this query parameter with this value. */
    public T waitForQueryParam(String key, String value) {
        wait.until(ExpectedConditions.urlContains(key + "=" + value));
        return self();
    }

    // ------------------------------------------------------------- elements --

    protected WebElement visible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected List<WebElement> all(By locator) {
        return driver.findElements(locator);
    }

    protected String textOf(By locator) {
        return Interactions.textOf(visible(locator));
    }

    protected boolean isPresent(By locator) {
        return Interactions.isPresent(driver, locator);
    }

    /**
     * True if the locator matches nothing on screen, confirmed rather than
     * merely observed once.
     *
     * A bare {@code findElements().isEmpty()} run straight after an action is
     * the classic false pass: the element the test expects has simply not been
     * rendered yet, so "absent" and "not there yet" look identical. This waits
     * out that window, which is the correct trade - it is only paid when the
     * element really is absent, and the assertion is then trustworthy.
     */
    protected boolean isAbsent(By locator, Duration settle) {
        try {
            new WebDriverWait(driver, settle)
                    .until(ExpectedConditions.presenceOfElementLocated(locator));
            return false;
        } catch (TimeoutException e) {
            return true;
        }
    }

    public String title() {
        return driver.getTitle();
    }

    /** Text of the page's H1, which every Artra view renders. */
    public String heading() {
        return Interactions.textOf(visible(By.cssSelector("h1")));
    }

    protected void clickUntil(java.util.function.Supplier<WebElement> target,
                              org.openqa.selenium.support.ui.ExpectedCondition<?> effect,
                              String description) {
        Interactions.clickUntil(driver, wait, target, effect, description);
    }

    protected Object script(String javascript, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(javascript, args);
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }
}
