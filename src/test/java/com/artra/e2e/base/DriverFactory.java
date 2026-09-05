package com.artra.e2e.base;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Builds one remote session on the grid.
 *
 * Everything here is per-session and decided by the caller
 * ({@link CrossBrowserExtension}), not globally: which browser, whether it is
 * recorded, and what the session is called. That is what lets a single run
 * drive Chrome, Firefox and Edge concurrently from the same JVM.
 *
 * <h2>Why each browser is told to trust the application's origin</h2>
 *
 * Artra marks its session and verification cookies {@code Secure}. A browser
 * only stores those from an origin it considers <em>potentially trustworthy</em>
 * - HTTPS, or localhost. The application is served over plain HTTP at a
 * container address, which is neither, so by default every cookie it sets is
 * discarded silently: no error, no console warning, just a sign-in that never
 * sticks and thirty tests failing for a reason nothing logs.
 *
 * Each engine has its own way to declare an origin trustworthy anyway, and all
 * three are applied below from the configured base URL, so changing
 * TEST_BASE_URL cannot leave the setting pointing at the old address.
 */
public final class DriverFactory {

    private static final TestConfig CONFIG = ConfigProvider.get();

    /** Fixed so screenshots and recordings from different browsers are directly
     *  comparable, and so the desktop layout - the one the tests assert on - is
     *  what gets rendered. Artra hides its main navigation below `lg`. */
    private static final int WINDOW_WIDTH = 1920;
    private static final int WINDOW_HEIGHT = 1080;

    private DriverFactory() {
    }

    /**
     * @param browser     "chrome", "firefox" or "edge"
     * @param testName    shown in the Grid console and used by the recorder to
     *                    name the .mp4 (se:name)
     * @param recordVideo true -> the grid starts a recorder alongside the
     *                    browser, and the browser runs headed so there is
     *                    something to record; false -> headless
     */
    public static RemoteWebDriver create(String browser, String testName, boolean recordVideo) {
        // Headless renders nothing to the node's display, so a recorded
        // headless session produces a blank video. Recording implies headed.
        boolean headless = !recordVideo;

        MutableCapabilities caps = switch (browser) {
            case "chrome" -> chrome(headless);
            case "firefox" -> firefox(headless);
            case "edge" -> edge(headless);
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        };

        caps.setCapability("se:name", testName);
        caps.setCapability("se:recordVideo", recordVideo);

        return new RemoteWebDriver(hubUrl(), caps);
    }

    private static ChromeOptions chrome(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        // EAGER returns on DOMContentLoaded instead of waiting for every image
        // and font. Artra lazy-loads course thumbnails, so COMPLETE would make
        // each navigation wait on assets no assertion looks at.
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments(
            "--window-size=" + WINDOW_WIDTH + "," + WINDOW_HEIGHT,
            // The grid's browser containers get a small default /dev/shm, and
            // Chrome crashes rather than degrades when it runs out.
            "--disable-dev-shm-usage",
            "--no-sandbox",
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-default-apps",
            "--disable-save-password-bubble",
            "--disable-features=Translate,InterestFeedContentSuggestions",
            // Artra is a client-routed SPA; a restored bfcache page replays a
            // stale DOM after browser-back and makes navigation assertions flap.
            "--disable-features=BackForwardCache",
            "--disable-back-forward-cache"
        );

        // Chromium ignores --unsafely-treat-insecure-origin-as-secure unless a
        // profile directory is given as well. No --incognito for the same
        // reason; isolation comes from the container being thrown away after
        // every session, which is stronger than a private window anyway.
        insecureOrigin().ifPresent(origin -> options.addArguments(
            "--unsafely-treat-insecure-origin-as-secure=" + origin,
            "--user-data-dir=/tmp/artra-e2e-profile"));

        return options;
    }

    private static FirefoxOptions firefox(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        if (headless) {
            options.addArguments("-headless");
        }

        options.addArguments("--width=" + WINDOW_WIDTH, "--height=" + WINDOW_HEIGHT);
        // Firefox otherwise offers to save the password on every sign-in test,
        // and the doorhanger intercepts the next click.
        options.addPreference("signon.rememberSignons", false);
        options.addPreference("browser.cache.disk.enable", false);

        // Firefox's equivalent of Chromium's flag. It takes bare hosts, not
        // origins - no scheme, no port.
        insecureHost().ifPresent(host ->
            options.addPreference("dom.securecontext.allowlist", host));

        return options;
    }

    private static EdgeOptions edge(boolean headless) {
        EdgeOptions options = new EdgeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments(
            "--window-size=" + WINDOW_WIDTH + "," + WINDOW_HEIGHT,
            "--disable-dev-shm-usage",
            "--no-sandbox",
            "--no-first-run",
            "--disable-extensions",
            "--disable-features=msEdgeIdentityFre,msImplicitSignin"
        );

        insecureOrigin().ifPresent(origin -> options.addArguments(
            "--unsafely-treat-insecure-origin-as-secure=" + origin,
            "--user-data-dir=/tmp/artra-e2e-profile"));

        return options;
    }

    private static Optional<String> insecureOrigin() {
        return insecureOrigin(CONFIG.baseUrl());
    }

    private static Optional<String> insecureHost() {
        return insecureHost(CONFIG.baseUrl());
    }

    /**
     * The application's origin, when it needs declaring as trustworthy.
     *
     * Empty for an HTTPS base URL, and for localhost - a browser already
     * treats both as trustworthy, and passing the flag anyway would be a
     * setting that outlives the reason for it.
     *
     * Package-private and taking the URL as an argument so it can be unit
     * tested: getting this wrong does not fail loudly, it just makes every
     * authenticated test fail with an empty cookie jar.
     */
    static Optional<String> insecureOrigin(String baseUrl) {
        URI uri = parse(baseUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) || isLocalhost(uri.getHost())) {
            return Optional.empty();
        }
        return Optional.of(uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
    }

    /** The bare host, which is the form Firefox's allowlist preference takes. */
    static Optional<String> insecureHost(String baseUrl) {
        return insecureOrigin(baseUrl).map(origin -> parse(baseUrl).getHost());
    }

    private static boolean isLocalhost(String host) {
        return host == null
            || "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host);
    }

    private static URI parse(String baseUrl) {
        try {
            return URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid TEST_BASE_URL: " + baseUrl, e);
        }
    }

    private static URL hubUrl() {
        String raw = CONFIG.hubUrl();
        try {
            return URI.create(raw).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid SELENIUM_HUB_URL: " + raw, e);
        }
    }
}
