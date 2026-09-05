package com.artra.e2e.base;

/**
 * Values that must never be checked in.
 *
 * They have no default in {@link TestConfig}, so an unset one arrives as null
 * and would otherwise be typed into a form as the literal "null" - a login
 * failure that looks like an application bug. Reading them through here turns
 * that into one sentence naming the variable and where to set it.
 */
public final class Secrets {

    private static final TestConfig CONFIG = ConfigProvider.get();

    private Secrets() {
    }

    /** Password of every account created by stack/db/init/02-seed.sql. */
    public static String seedUserPassword() {
        return require(CONFIG.seedUserPassword(), "SEED_USER_PASSWORD");
    }

    public static String databasePassword() {
        return require(CONFIG.databasePassword(), "DATABASE_PASSWORD");
    }

    private static String require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                key + " is not set. Locally: copy env/secrets.env.example to env/secrets.env, "
                    + "fill it in and restart the stack so the containers pick it up. "
                    + "In CI it comes from a repository secret of the same name "
                    + "(Settings -> Secrets and variables -> Actions).");
        }
        return value;
    }
}
