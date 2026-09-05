package com.artra.e2e.base;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Expands each {@link CrossBrowserTest} method into one invocation per browser
 * listed in BROWSERS, and hands each invocation its own {@link DriverLifecycle}
 * so no state is shared between them.
 *
 * HEADED decides, per browser, whether that invocation renders into the node's
 * Xvfb display - which is what makes it watchable over noVNC - or runs
 * headless.
 */
public class CrossBrowserExtension implements TestTemplateInvocationContextProvider {

    static final Set<String> SUPPORTED = Set.of("chrome", "firefox", "edge");

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
            ExtensionContext context) {

        Set<String> headed = Set.copyOf(splitCsv(ConfigProvider.get().headedBrowsers()));

        return configuredBrowsers().stream()
                .map(browser -> new BrowserInvocation(browser, headed.contains(browser)));
    }

    /**
     * The validated browser list.
     *
     * A typo fails loudly here rather than producing a run that quietly covers
     * two browsers instead of three - the kind of gap nobody notices until a
     * Firefox-only bug reaches production.
     */
    static List<String> configuredBrowsers() {
        List<String> browsers = splitCsv(ConfigProvider.get().browsers());
        if (browsers.isEmpty()) {
            throw new IllegalStateException(
                    "BROWSERS is empty - set it to a comma-separated list, e.g. chrome,firefox,edge");
        }
        for (String browser : browsers) {
            if (!SUPPORTED.contains(browser)) {
                throw new IllegalArgumentException(
                        "Unsupported browser '" + browser + "' in BROWSERS (supported: " + SUPPORTED + ")");
            }
        }
        return browsers;
    }

    static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    /** One run of the template: its display name and the extension that owns
     *  that run's driver. */
    private record BrowserInvocation(String browser, boolean headed)
            implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return "[" + browser + "]";
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new DriverLifecycle(browser, headed));
        }
    }
}
