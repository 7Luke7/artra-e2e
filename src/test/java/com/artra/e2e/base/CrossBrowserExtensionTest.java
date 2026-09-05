package com.artra.e2e.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A unit test for the framework itself, run by surefire before any browser
 * starts.
 *
 * The browser list is parsed from a comma-separated environment variable, which
 * is the kind of code that looks obviously correct and then silently drops a
 * browser because someone wrote "chrome, Firefox," in CI. That failure is
 * invisible - the run is green, it just covered less than it claimed - so it is
 * worth pinning down here rather than discovering it from a production bug.
 */
class CrossBrowserExtensionTest {

    @Test
    @DisplayName("Browser lists are trimmed, lower-cased and de-duplicated")
    void parsesMessyBrowserLists() {
        assertEquals(List.of("chrome", "firefox", "edge"),
                CrossBrowserExtension.splitCsv(" chrome , Firefox,EDGE , chrome ,, "));
    }

    @Test
    @DisplayName("An empty or absent list parses to nothing rather than a blank entry")
    void parsesEmptyLists() {
        assertTrue(CrossBrowserExtension.splitCsv(null).isEmpty());
        assertTrue(CrossBrowserExtension.splitCsv("").isEmpty());
        assertTrue(CrossBrowserExtension.splitCsv("  ,  ").isEmpty());
    }

    @Test
    @DisplayName("Every browser the factory can build is one the extension accepts")
    void supportedBrowsersMatchTheFactory() {
        // These two lists are edited in different files, and a browser added to
        // one but not the other fails at run time, halfway through a matrix job.
        assertEquals(java.util.Set.of("chrome", "firefox", "edge"), CrossBrowserExtension.SUPPORTED);
    }
}
