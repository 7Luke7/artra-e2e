package com.artra.e2e.support;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Values that must be unique per test.
 *
 * Test independence here is mostly a data problem: Artra's User.email is
 * unique, so two registration tests that share an address make the second one
 * fail with the first one's data, and a shared address in the mail inbox makes
 * one test read the other's verification code. Both failures look like
 * application bugs. Every value below is therefore unique per call, and unique
 * across concurrently running JVMs on the same stack.
 *
 * The domain is deliberately {@code @artra.test}: the .test TLD is reserved for
 * exactly this by RFC 6761, so a stray send can never leave the test stack.
 */
public final class TestData {

    private static final String DOMAIN = "artra.test";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestData() {
    }

    /**
     * A fresh address, e.g. {@code signup-l8kq2v-7@artra.test}.
     *
     * The random half keeps two runs against the same stack apart; the counter
     * keeps two threads inside one run apart even if they land on the same
     * millisecond.
     */
    public static String uniqueEmail(String prefix) {
        return prefix + "-" + token() + "-" + SEQUENCE.incrementAndGet() + "@" + DOMAIN;
    }

    /** A password that satisfies Artra's rule: 8+ characters, no whitespace. */
    public static String password() {
        return "Artra-" + token() + "!7";
    }

    /** Georgian given/family names - the app validates names against
     *  {@code \\p{L}+}, so an ASCII placeholder would not exercise the rule the
     *  real users hit. */
    public static String givenName() {
        return "ნინო";
    }

    public static String familyName() {
        return "ბერიძე";
    }

    /** Contact-form message body of a valid length (the form requires 50-1000
     *  characters). */
    public static String contactMessage() {
        return "ავტომატური ტესტის შეტყობინება: " + token()
                + ". გთხოვთ დაადასტუროთ, რომ ფორმა მუშაობს გამართულად და შეტყობინება ინახება.";
    }

    private static String token() {
        return Long.toString(Math.abs(RANDOM.nextLong()), 36).substring(0, 8);
    }
}
