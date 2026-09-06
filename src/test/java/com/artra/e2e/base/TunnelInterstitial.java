package com.artra.e2e.base;

import java.net.URI;
import java.util.Optional;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets a session past the warning page a free ngrok tunnel puts in front of the
 * application.
 *
 * <h2>Why this is needed</h2>
 *
 * In public mode ({@code ./run.sh public=on}) the stack is served through an
 * ngrok domain, because that is the only kind of origin Google will render its
 * sign-in button on. On a free ngrok account every <em>document</em> request
 * from something that looks like a browser is answered by ngrok itself with an
 * "You are about to visit..." page (ERR_NGROK_6024) rather than by the
 * application. Sub-resources and API calls are let through, so the failure is
 * not a network error: the browser lands on a perfectly healthy page that
 * simply is not Artra, and every test times out looking for a marker that was
 * never going to appear.
 *
 * <h2>How it is dismissed</h2>
 *
 * The page's "Visit Site" button sets a cookie, {@code abuse_interstitial},
 * whose value is the tunnel's host, and reloads. Setting the same cookie
 * ourselves is the whole of the bypass, and it is worth preferring over the
 * documented {@code ngrok-skip-browser-warning} request header: a header on a
 * top-level navigation cannot be set through WebDriver at all, and the nearest
 * substitute - CDP - exists in Chrome and Edge but not in Firefox. A cookie is
 * plain WebDriver and behaves identically in all three.
 *
 * {@code SameSite=None} is not incidental. The Google sign-in flow finishes
 * with a cross-site form POST from accounts.google.com to
 * {@code /api/auth/google}, and a Lax cookie is withheld from exactly that
 * request - which would put the warning page in the middle of the callback,
 * the one navigation in the suite that cannot simply be retried.
 *
 * Does nothing at all when the application is not behind a tunnel, which is the
 * default.
 */
final class TunnelInterstitial {

    private static final Logger log = LoggerFactory.getLogger(TunnelInterstitial.class);

    private static final String COOKIE = "abuse_interstitial";

    private TunnelInterstitial() {
    }

    /**
     * Pre-accepts the warning page for this session, if there is one to accept.
     *
     * Costs one navigation, once, at session start - and only in public mode.
     */
    static void accept(RemoteWebDriver driver, String baseUrl) {
        Optional<String> host = tunnelHost(baseUrl);
        if (host.isEmpty()) {
            return;
        }

        // A cookie can only be set for the document the browser is currently
        // on, so the warning page has to be visited before it can be skipped.
        // It answers 200 with its own HTML, so this navigation always succeeds.
        driver.get(baseUrl);

        try {
            driver.manage().addCookie(cookie(host.get(), "None"));
        } catch (WebDriverException e) {
            // Not every driver accepts the sameSite field. Falling back leaves
            // the ordinary navigations working and only the cross-site OAuth
            // callback exposed, which beats failing the session outright.
            log.debug("SameSite=None rejected for {}; retrying without it", COOKIE, e);
            driver.manage().addCookie(cookie(host.get(), null));
        }
    }

    private static Cookie cookie(String host, String sameSite) {
        Cookie.Builder builder = new Cookie.Builder(COOKIE, host)
                .path("/")
                .isSecure(true);
        if (sameSite != null) {
            builder.sameSite(sameSite);
        }
        return builder.build();
    }

    /**
     * The host to accept, or empty when the base URL is not a tunnel.
     *
     * Matched on the domain suffix rather than on "is this HTTPS", because the
     * cookie is meaningless anywhere else and a stray one on a real deployment
     * would be a puzzle for whoever found it.
     *
     * Package-private and taking the URL as an argument so it can be unit
     * tested - like the trusted-origin derivation next to it, this fails by
     * doing nothing rather than by raising anything.
     */
    static Optional<String> tunnelHost(String baseUrl) {
        String host;
        try {
            host = URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (host == null) {
            return Optional.empty();
        }

        String lower = host.toLowerCase();
        // The reserved-domain suffixes ngrok has issued: .ngrok.io is the
        // original, .ngrok-free.* what a free account gets today, .ngrok.app
        // and .ngrok.dev what a paid one does.
        boolean tunnelled = lower.endsWith(".ngrok.io")
                || lower.endsWith(".ngrok-free.app")
                || lower.endsWith(".ngrok-free.dev")
                || lower.endsWith(".ngrok.app")
                || lower.endsWith(".ngrok.dev");

        return tunnelled ? Optional.of(host) : Optional.empty();
    }
}
