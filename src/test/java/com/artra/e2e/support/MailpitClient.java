package com.artra.e2e.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.artra.e2e.base.ConfigProvider;
import com.artra.e2e.base.TestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the test inbox.
 *
 * Artra's email flows - registration codes, login confirmation codes, password
 * reset links - are only meaningfully testable end to end if the suite can read
 * what was actually delivered. In the test stack Artra sends over SMTP to
 * Mailpit, a disposable mail server with an HTTP API, so these tests exercise
 * the real send path without touching a real provider or anyone's real inbox.
 * Production keeps sending through Resend; only the transport differs, and it
 * is selected by EMAIL_PROVIDER (see the app's src/routes/api/lib/email).
 *
 * <h2>Parallel safety</h2>
 *
 * Every lookup is scoped to a recipient address, and every test that needs mail
 * registers a unique one via {@link TestData#uniqueEmail}. Nothing here ever
 * deletes messages: a shared "clear the inbox" step is exactly what makes an
 * email suite unusable in parallel, because it throws away another test's mail
 * a moment before that test looks for it.
 */
public final class MailpitClient {

    private static final Logger log = LoggerFactory.getLogger(MailpitClient.class);
    private static final TestConfig CONFIG = ConfigProvider.get();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** How often the inbox is polled while waiting for a delivery. */
    private static final Duration POLL = Duration.ofMillis(500);

    /** Artra's verification codes are six digits (validation-rules.js). */
    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    /** The reset mail links to /api/auth/issue_session?token=<96 hex chars>. */
    private static final Pattern RESET_LINK =
            Pattern.compile("https?://[^\\s\"'<>]*/api/auth/issue_session\\?token=[0-9a-fA-F]{96}");

    private MailpitClient() {
    }

    /**
     * The most recent message to {@code recipient} that arrived at or after
     * {@code since}.
     *
     * The timestamp is not decoration. Several flows send more than one mail to
     * the same address - resend a code, request a second reset - and without a
     * lower bound the test happily reads the code it already used and fails
     * with "invalid code" against an application that behaved perfectly.
     */
    public static Message awaitMessage(String recipient, Instant since) {
        Duration timeout = Duration.ofSeconds(CONFIG.mailWaitSeconds());
        Instant deadline = Instant.now().plus(timeout);

        log.info("▶ Waiting for mail to {} (since {})", recipient, since);

        Optional<Message> found;
        do {
            found = latestFor(recipient, since);
            if (found.isPresent()) {
                log.info("✓ Received '{}' for {}", found.get().subject(), recipient);
                return found.get();
            }
            sleep(POLL);
        } while (Instant.now().isBefore(deadline));

        throw new AssertionError(
                "No email arrived for " + recipient + " within " + timeout.toSeconds()
                        + "s. Mailpit currently holds " + messageCount(recipient)
                        + " message(s) for that address. Check the app container's logs for "
                        + "ERROR_WHILE_SENDING_EMAIL, and that the artra service still has "
                        + "EMAIL_PROVIDER=smtp in docker-compose.yml.");
    }

    /** The six-digit verification code carried by a message. */
    public static String verificationCode(Message message) {
        Matcher matcher = CODE.matcher(message.text());
        if (!matcher.find()) {
            throw new AssertionError(
                    "No 6-digit code in the message '" + message.subject() + "'. Body was:\n"
                            + message.text());
        }
        return matcher.group(1);
    }

    /** The password-reset link carried by a message. */
    public static String resetLink(Message message) {
        Matcher matcher = RESET_LINK.matcher(message.text());
        if (!matcher.find()) {
            throw new AssertionError(
                    "No password-reset link in the message '" + message.subject() + "'. Body was:\n"
                            + message.text());
        }
        return matcher.group();
    }

    /** Convenience for the common "wait, then read the code" pair. */
    public static String awaitVerificationCode(String recipient, Instant since) {
        return verificationCode(awaitMessage(recipient, since));
    }

    public static String awaitResetLink(String recipient, Instant since) {
        return resetLink(awaitMessage(recipient, since));
    }

    /** How many messages the inbox holds for an address; used in assertions
     *  about mail that should <em>not</em> have been sent. */
    public static int messageCount(String recipient) {
        try {
            JsonNode results = search(recipient);
            JsonNode count = results.path("messages_count");
            return count.isNumber() ? count.asInt() : results.path("messages").size();
        } catch (Exception e) {
            return -1;
        }
    }

    // ------------------------------------------------------------- internals --

    private static Optional<Message> latestFor(String recipient, Instant since) {
        JsonNode results;
        try {
            results = search(recipient);
        } catch (Exception e) {
            // Mailpit may not be accepting connections yet on the very first
            // poll of a freshly started stack; that is not a failure.
            log.debug("Mailpit search failed, retrying: {}", e.toString());
            return Optional.empty();
        }

        // Mailpit returns newest first.
        for (JsonNode summary : results.path("messages")) {
            Instant created = Instant.parse(summary.path("Created").asText());
            // Mailpit stores whole milliseconds while Instant.now() carries
            // microseconds, so a mail sent inside the same millisecond as the
            // marker would otherwise be rejected as "too old".
            if (created.plusMillis(1).isBefore(since)) {
                continue;
            }
            return Optional.of(fetch(summary.path("ID").asText()));
        }
        return Optional.empty();
    }

    private static JsonNode search(String recipient) {
        String query = URLEncoder.encode("to:" + recipient, StandardCharsets.UTF_8);
        return get("/api/v1/search?limit=20&query=" + query);
    }

    private static Message fetch(String id) {
        JsonNode body = get("/api/v1/message/" + id);
        return new Message(
                id,
                body.path("Subject").asText(""),
                body.path("Text").asText(""),
                body.path("HTML").asText(""),
                address(body.path("From")),
                Instant.parse(body.path("Date").asText(Instant.now().toString())));
    }

    private static String address(JsonNode node) {
        return node.isArray() && !node.isEmpty()
                ? node.get(0).path("Address").asText("")
                : node.path("Address").asText("");
    }

    private static JsonNode get(String route) {
        String url = base() + route;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Mailpit request failed: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling Mailpit: " + url, e);
        }
    }

    private static String base() {
        String url = CONFIG.mailpitUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for mail", e);
        }
    }

    /** One delivered message, as much of it as any assertion here needs. */
    public record Message(String id, String subject, String text, String html,
                          String from, Instant received) {
    }
}
