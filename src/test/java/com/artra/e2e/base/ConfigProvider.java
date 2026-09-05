package com.artra.e2e.base;

import org.aeonbits.owner.ConfigFactory;

/**
 * The single {@link TestConfig} instance.
 *
 * Owner's ConfigFactory.create re-reads and re-merges every source on each
 * call, so creating one per page object would parse test.properties thousands
 * of times in a parallel run. One immutable instance also means every thread
 * sees the same configuration, which matters when a test fails on one browser
 * only and the first question is whether it was configured differently.
 */
public final class ConfigProvider {

    private static final TestConfig INSTANCE = ConfigFactory.create(TestConfig.class);

    private ConfigProvider() {
    }

    public static TestConfig get() {
        return INSTANCE;
    }
}
