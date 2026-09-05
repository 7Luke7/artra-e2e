package com.artra.e2e.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.artra.e2e.base.ConfigProvider;
import com.artra.e2e.base.Secrets;
import com.artra.e2e.base.TestConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Direct database access, used for two things and nothing else:
 *
 * <ul>
 *   <li><b>teardown</b> - removing the account a registration test created, so
 *       the suite can be re-run against the same stack without piling up rows
 *       or tripping the unique email constraint;</li>
 *   <li><b>confirming a write the UI cannot show</b> - the contact form tells
 *       the visitor "we received it" and then never displays the message again,
 *       so the only way to prove it was actually persisted is to look.</li>
 * </ul>
 *
 * It is deliberately <em>not</em> used to set up the state a test is about. A
 * login test that inserts a session row into Redis and skips the login form has
 * stopped testing logging in. State that the user can create through the UI is
 * created through the UI here; only the fixed backdrop (courses, categories,
 * the instructor) is seeded, in stack/db/init/02-seed.sql.
 */
public final class ArtraDatabase {

    private static final Logger log = LoggerFactory.getLogger(ArtraDatabase.class);
    private static final TestConfig CONFIG = ConfigProvider.get();

    private ArtraDatabase() {
    }

    /**
     * Removes a user and, by cascade, their devices, notifications and
     * enrolments.
     *
     * Safe to call for an address that was never created - a registration test
     * that failed before submitting still runs its teardown.
     */
    public static void deleteUser(String email) {
        int removed = update("DELETE FROM \"User\" WHERE email = ?", email);
        if (removed > 0) {
            log.info("✓ Cleaned up user {}", email);
        }
    }

    /** Removes contact messages left by a test. */
    public static void deleteContactMessages(String email) {
        update("DELETE FROM contact_message WHERE email = ?", email);
    }

    public static boolean userExists(String email) {
        return queryString("SELECT id FROM \"User\" WHERE email = ?", email).isPresent();
    }

    /** The stored message body for an address, if the contact form saved one. */
    public static Optional<String> contactMessageFor(String email) {
        return queryString(
                "SELECT message FROM contact_message WHERE email = ? ORDER BY created_at DESC LIMIT 1",
                email);
    }

    /** Slug of the newest published course - the one the landing page leads
     *  with, so a test can assert the two agree. */
    public static Optional<String> newestPublishedCourseSlug() {
        return queryString(
                "SELECT slug FROM course WHERE status = 'published' ORDER BY created_at DESC LIMIT 1");
    }

    public static int publishedCourseCount() {
        return queryString("SELECT COUNT(*)::text FROM course WHERE status = 'published'")
                .map(Integer::parseInt)
                .orElse(0);
    }

    // ------------------------------------------------------------- plumbing --

    private static Optional<String> queryString(String sql, Object... parameters) {
        try (Connection connection = connect();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet results = statement.executeQuery()) {
            return results.next() ? Optional.ofNullable(results.getString(1)) : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
    }

    private static int update(String sql, Object... parameters) {
        try (Connection connection = connect();
             PreparedStatement statement = prepare(connection, sql, parameters)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Statement failed: " + sql, e);
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... parameters)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement;
    }

    /**
     * A connection per call rather than a pool.
     *
     * The suite makes a handful of queries per test, all of them in setup or
     * teardown, so a pool would add shared state - and a pool shared across
     * parallel invocations is one more thing that can be the reason a test
     * failed. Postgres opens a local connection in single-digit milliseconds.
     */
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                CONFIG.databaseUrl(), CONFIG.databaseUser(), Secrets.databasePassword());
    }
}
