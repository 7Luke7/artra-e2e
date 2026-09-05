package com.artra.e2e.base;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.LoadPolicy;
import org.aeonbits.owner.Config.LoadType;
import org.aeonbits.owner.Config.Sources;

/**
 * Every knob the suite has, resolved in one place.
 *
 * The @Sources order is the whole point: environment variables beat system
 * properties, which beat the checked-in defaults in test.properties. That is
 * what lets the same image run unchanged against a local stack, a colleague's
 * stack and CI - only the environment differs - and it is why no value below
 * is ever read with System.getenv() somewhere else in the codebase.
 *
 * Values with no @DefaultValue are secrets on purpose. They are read through
 * {@link Secrets}, which fails with an actionable message instead of letting a
 * null reach Selenium and surface twenty steps later as a blank field.
 */
/*
 * Keys are UPPER_SNAKE_CASE throughout, including the ones that only ever come
 * from test.properties. Dotted keys read more naturally in a properties file
 * but cannot be set as environment variables, so any setting named that way is
 * silently un-overridable from docker-compose or from CI - which is exactly
 * where an override is most often needed.
 */
@LoadPolicy(LoadType.MERGE)
@Sources({
    "system:env",
    "system:properties",
    "classpath:test.properties"
})
public interface TestConfig extends Config {

    // ---------------------------------------------------------------- grid --

    /** Selenium Grid router. Inside the compose network this is the hub's
     *  service name; on a host-run debug session, http://localhost:4444/wd/hub. */
    @Key("SELENIUM_HUB_URL")
    @DefaultValue("http://selenium-hub:4444/wd/hub")
    String hubUrl();

    /**
     * Comma-separated browsers to run every @CrossBrowserTest against,
     * e.g. "chrome,firefox,edge". Written into env/artra-e2e.env by the
     * startup scripts and set directly by the CI matrix.
     */
    @Key("BROWSERS")
    @DefaultValue("chrome")
    String browsers();

    /**
     * Comma-separated subset of BROWSERS to run <b>headed</b> instead of
     * headless, e.g. "chrome,firefox".
     *
     * A headed session paints into the node's Xvfb display, which the grid's
     * node images stream over noVNC - so a headed browser can be watched live
     * at http://localhost:7900 (chrome), :7901 (firefox), :7902 (edge) while
     * the test runs. That is the debugging tool here; nothing else changes.
     */
    @Key("HEADED")
    @DefaultValue("")
    String headedBrowsers();

    /**
     * Total grid capacity, which is also JUnit's thread-pool size - see
     * {@link GridParallelismStrategy}. Computed by the startup scripts from
     * physical cores and free RAM.
     */
    @Key("PARALLELISM")
    @DefaultValue("1")
    int parallelism();

    // --------------------------------------------------- application under test --

    /**
     * Where the browsers reach Artra. HTTPS on purpose: Artra sets its session
     * and verification cookies with the Secure attribute, and a browser drops
     * those over plain HTTP on any host that is not localhost - which a
     * containerised browser never is. See stack/caddy/Caddyfile.
     */
    @Key("TEST_BASE_URL")
    @DefaultValue("https://artra.test")
    String baseUrl();

    // ------------------------------------------------------------- mailbox --

    /**
     * Mailpit's HTTP API. Artra's test profile sends through Mailpit's SMTP
     * port, so the suite reads verification codes and reset links from a real
     * inbox without any dependency on an external mail provider.
     */
    @Key("MAILPIT_URL")
    @DefaultValue("http://mailpit:8025")
    String mailpitUrl();

    /** How long an email gets to arrive before the wait gives up. */
    @Key("MAIL_WAIT_SECONDS")
    @DefaultValue("45")
    int mailWaitSeconds();

    // ------------------------------------------------------------ database --

    /** JDBC URL of the application database, used only for fixture setup and
     *  teardown - never to assert something the UI could show instead. */
    @Key("DATABASE_JDBC_URL")
    @DefaultValue("jdbc:postgresql://postgres:5432/artra")
    String databaseUrl();

    @Key("DATABASE_USER")
    @DefaultValue("artra")
    String databaseUser();

    /** No default: supplied by env/secrets.env locally, by a repository secret
     *  in CI. */
    @Key("DATABASE_PASSWORD")
    String databasePassword();

    // ----------------------------------------------------------- fixtures --

    /** Email of the seeded student account (stack/db/init/02-seed.sql). */
    @Key("SEED_STUDENT_EMAIL")
    @DefaultValue("student@artra.test")
    String seedStudentEmail();

    /** Password of every seeded account. No default - it is a credential, even
     *  for a throwaway fixture, and belongs in env/secrets.env. */
    @Key("SEED_USER_PASSWORD")
    String seedUserPassword();

    // ------------------------------------------------------------- timing --

    /** Deliberately 0. Mixing implicit and explicit waits makes every
     *  findElements() call that is meant to return empty block for the implicit
     *  timeout instead, which is how "the suite got slow" usually starts. */
    @Key("IMPLICIT_WAIT_SECONDS")
    @DefaultValue("0")
    int implicitWaitSeconds();

    @Key("PAGE_LOAD_TIMEOUT_SECONDS")
    @DefaultValue("60")
    int pageLoadTimeoutSeconds();

    @Key("EXPLICIT_WAIT_SECONDS")
    @DefaultValue("30")
    int explicitWaitSeconds();

    // -------------------------------------------------------- diagnostics --

    /** Where failure screenshots, page dumps and console logs are written.
     *  Archived per run by run.sh and uploaded as a CI artifact. */
    @Key("DIAGNOSTICS_DIR")
    @DefaultValue("target/diagnostics")
    String diagnosticsDir();
}
