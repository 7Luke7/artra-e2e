package com.artra.e2e.base;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfiguration;
import org.junit.platform.engine.support.hierarchical.ParallelExecutionConfigurationStrategy;

/**
 * JUnit's thread pool is sized to the grid's capacity, not to the CPU.
 *
 * dev.sh / run.sh / ci.sh compute how many browser sessions this machine can
 * actually sustain and write it as PARALLELISM into env/artra-e2e.env. The same
 * number becomes the grid's max-sessions, so the client can never queue more
 * sessions than the grid will serve - which is what turns "the suite is flaky
 * under load" into a session timeout nobody can reproduce locally.
 *
 * minimumRunnable = 0 and maxPoolSize = parallelism keep the cap hard: without
 * them JUnit's ForkJoinPool spawns extra compensation threads whenever a test
 * blocks on Selenium I/O, which is essentially always.
 *
 * Wired up in junit-platform.properties.
 */
public class GridParallelismStrategy implements ParallelExecutionConfigurationStrategy {

    @Override
    public ParallelExecutionConfiguration createConfiguration(ConfigurationParameters parameters) {
        final int parallelism = Math.max(1, ConfigProvider.get().parallelism());

        return new ParallelExecutionConfiguration() {
            @Override
            public int getParallelism() {
                return parallelism;
            }

            @Override
            public int getMinimumRunnable() {
                return 0;
            }

            @Override
            public int getMaxPoolSize() {
                return parallelism;
            }

            @Override
            public int getCorePoolSize() {
                return parallelism;
            }

            @Override
            public int getKeepAliveSeconds() {
                return 30;
            }
        };
    }
}
