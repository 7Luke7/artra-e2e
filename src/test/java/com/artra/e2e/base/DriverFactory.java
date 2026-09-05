package com.artra.e2e.base;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

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
 * recorded, and what the session is called in the Grid UI. That is what lets a
 * single run drive Chrome, Firefox and Edge concurrently from the same JVM.
 */
public final class DriverFactory {

    private static final TestConfig CONFIG = ConfigProvider.get();

    /** Fixed so screenshots from different browsers are directly comparable and
     *  the desktop layout (which is what these tests assert) is the one
     *  rendered - Artra hides its main nav below the lg breakpoint. */
    private static final int WINDOW_WIDTH = 1920;
    private static final int WINDOW_HEIGHT = 1080;

    private DriverFactory() {
    }

    /**
     * @param browser  "chrome", "firefox" or "edge"
     * @param testName shown in the Grid UI, so a session that is still running
     *                 can be identified while watching it over noVNC
     * @param headed   true -> renders into the node's Xvfb display, which is
     *                 what noVNC streams; false -> headless
     */
    public static RemoteWebDriver create(String browser, String testName, boolean headed) {
        boolean headless = !headed;

        MutableCapabilities caps = switch (browser) {
            case "chrome" -> chrome(headless);
            case "firefox" -> firefox(headless);
            case "edge" -> edge(headless);
            default -> throw new IllegalArgumentException("Unsupported browser: " + browser);
        };

        // Artra is served over HTTPS with a certificate from Caddy's internal
        // CA (see stack/caddy/Caddyfile). Trusting it per session is the one
        // approach that works identically on all three browsers - the
        // alternative, Chrome's --unsafely-treat-insecure-origin-as-secure, has
        // no Firefox equivalent that also covers Secure cookies.
        caps.setCapability("acceptInsecureCerts", true);

        // Names the session in the Grid console, so one that is still running can
        // be matched to the test driving it. The node's display size is set by
        // SE_SCREEN_WIDTH/HEIGHT in docker-compose.yml; the window size below is
        // what the page actually lays out against.
        caps.setCapability("se:name", testName);

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
            "--incognito",
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

        return options;
    }

    private static FirefoxOptions firefox(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        if (headless) {
            options.addArguments("-headless");
        }

        options.addArguments("--width=" + WINDOW_WIDTH, "--height=" + WINDOW_HEIGHT);
        // A fresh profile per session already isolates cookies; private
        // browsing additionally stops Firefox restoring a previous session's
        // tabs when the node reuses its profile directory.
        options.addPreference("browser.privatebrowsing.autostart", true);
        // Firefox otherwise offers to save the password on every login test and
        // the doorhanger intercepts the next click.
        options.addPreference("signon.rememberSignons", false);
        options.addPreference("browser.cache.disk.enable", false);

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
            "--inprivate",
            "--no-first-run",
            "--disable-extensions",
            "--disable-features=msEdgeIdentityFre,msImplicitSignin"
        );

        return options;
    }

    private static URL hubUrl() {
        String raw = CONFIG.hubUrl();
        try {
            return URI.create(raw).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid selenium.hub.url: " + raw, e);
        }
    }
}
