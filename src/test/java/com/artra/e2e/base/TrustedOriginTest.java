package com.artra.e2e.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the one piece of driver configuration that fails silently.
 *
 * Artra's cookies are Secure and the application is served over plain HTTP, so
 * every browser has to be told to treat its origin as trustworthy. If that
 * derivation is wrong the browser reports nothing at all - it simply discards
 * every cookie, and roughly thirty authenticated tests fail with an empty
 * cookie jar and no explanation. Cheap to pin here; expensive to debug there.
 */
class TrustedOriginTest {

    @Test
    @DisplayName("An HTTP container address needs its origin trusted")
    void httpContainerAddressNeedsTrust() {
        assertEquals(Optional.of("http://172.19.0.9:3000"),
                DriverFactory.insecureOrigin("http://172.19.0.9:3000"));
        // Firefox's preference takes a bare host - no scheme, no port.
        assertEquals(Optional.of("172.19.0.9"),
                DriverFactory.insecureHost("http://172.19.0.9:3000"));
    }

    @Test
    @DisplayName("HTTPS is already trustworthy, so nothing is declared")
    void httpsNeedsNothing() {
        assertTrue(DriverFactory.insecureOrigin("https://artra.example").isEmpty());
        assertTrue(DriverFactory.insecureHost("https://artra.example").isEmpty());
    }

    @Test
    @DisplayName("localhost is already trustworthy, so nothing is declared")
    void localhostNeedsNothing() {
        assertTrue(DriverFactory.insecureOrigin("http://localhost:3000").isEmpty());
        assertTrue(DriverFactory.insecureOrigin("http://127.0.0.1:3000").isEmpty());
    }

    @Test
    @DisplayName("A default port is left out of the origin, as browsers expect")
    void defaultPortIsOmitted() {
        assertEquals(Optional.of("http://artra.internal"),
                DriverFactory.insecureOrigin("http://artra.internal"));
    }
}
