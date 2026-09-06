package com.artra.e2e.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the other piece of driver configuration that fails silently.
 *
 * Deciding wrongly costs a session either way and says nothing while it does
 * it: too eager, and every ordinary run pays a stray navigation and carries a
 * cookie nobody can account for; too shy, and every test in public mode times
 * out on ngrok's warning page, which is a healthy HTTP 200 that simply is not
 * the application.
 */
class TunnelInterstitialTest {

    @Test
    @DisplayName("A free ngrok domain is recognised, and yields the bare host")
    void freeTunnelIsRecognised() {
        assertEquals(Optional.of("artra-e2e.ngrok-free.dev"),
                TunnelInterstitial.tunnelHost(
                        "https://artra-e2e.ngrok-free.dev"));
        // The cookie's value is the host, so a path on the URL must not reach it.
        assertEquals(Optional.of("artra-e2e.ngrok-free.dev"),
                TunnelInterstitial.tunnelHost(
                        "https://artra-e2e.ngrok-free.dev/login"));
    }

    @Test
    @DisplayName("The older and paid ngrok suffixes count too")
    void otherTunnelSuffixesAreRecognised() {
        assertTrue(TunnelInterstitial.tunnelHost("https://x.ngrok.io").isPresent());
        assertTrue(TunnelInterstitial.tunnelHost("https://x.ngrok-free.app").isPresent());
        assertTrue(TunnelInterstitial.tunnelHost("https://x.ngrok.app").isPresent());
        assertTrue(TunnelInterstitial.tunnelHost("https://x.ngrok.dev").isPresent());
    }

    @Test
    @DisplayName("The default container address is not a tunnel")
    void containerAddressIsNotATunnel() {
        assertTrue(TunnelInterstitial.tunnelHost("http://172.19.0.9:3000").isEmpty());
        assertTrue(TunnelInterstitial.tunnelHost("http://localhost:3000").isEmpty());
    }

    @Test
    @DisplayName("A host that merely mentions ngrok is not a tunnel")
    void lookalikeHostIsNotATunnel() {
        assertTrue(TunnelInterstitial.tunnelHost("https://ngrok.artra.example").isEmpty());
        assertTrue(TunnelInterstitial.tunnelHost("https://notngrok-free.dev").isEmpty());
    }
}
