package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.ConfigProvider;
import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.RegisterPage;
import com.artra.e2e.pages.VerifyEmailPage;
import com.artra.e2e.support.ArtraDatabase;
import com.artra.e2e.support.MailpitClient;
import com.artra.e2e.support.TestData;

/**
 * Registration, end to end through the mailbox.
 *
 * Signing up is the flow with the most moving parts in the application:
 * validation, Argon2 hashing, a pending record in Redis, an outbound email, a
 * six-digit code, and only then the user row plus a session. Nothing short of
 * driving it through a real inbox tests that chain, which is why the test stack
 * runs a disposable mail server rather than stubbing the send.
 */
@Tag("auth")
@Tag("email")
class RegistrationIT {

    private String createdEmail;

    /** Registration writes a real user, so every invocation cleans up its own -
     *  otherwise a second run of the suite hits the unique email constraint and
     *  fails for a reason that has nothing to do with the application. */
    @AfterEach
    void removeAccount() {
        if (createdEmail != null) {
            ArtraDatabase.deleteUser(createdEmail);
        }
    }

    @CrossBrowserTest
    @DisplayName("A new visitor can register, confirm the emailed code and end up signed in")
    void registrationCreatesAndSignsInTheAccount(WebDriver driver, WebDriverWait wait) {
        createdEmail = TestData.uniqueEmail("signup");
        String password = TestData.password();
        Instant sentAfter = Instant.now();

        VerifyEmailPage verify = new RegisterPage(driver, wait).get()
                .register(TestData.givenName(), TestData.familyName(), createdEmail, password);

        assertFalse(ArtraDatabase.userExists(createdEmail),
                "The account must not exist before the code is confirmed - until then the "
                        + "details only live in Redis");

        MailpitClient.Message mail = MailpitClient.awaitMessage(createdEmail, sentAfter);
        assertTrue(mail.subject().contains("ვერიფიკაციის კოდი"),
                "The verification mail should be the code email, but its subject was: "
                        + mail.subject());

        LandingPage landing = verify.confirm(MailpitClient.verificationCode(mail));

        assertAll(
                () -> assertTrue(ArtraDatabase.userExists(createdEmail),
                        "Confirming the code should create the account"),
                () -> assertTrue(landing.header().isSignedIn(),
                        "The visitor should be signed in straight after confirming"),
                () -> assertTrue(landing.isDashboard(),
                        "A signed-in visitor should land on the dashboard, not the marketing page"));
    }

    @CrossBrowserTest
    @DisplayName("Registering with an address that already exists is refused")
    void duplicateEmailIsRefused(WebDriver driver, WebDriverWait wait) {
        String existing = ConfigProvider.get().seedStudentEmail();

        RegisterPage register = new RegisterPage(driver, wait).get()
                .registerExpectingRejection(TestData.givenName(), TestData.familyName(),
                        existing, TestData.password());

        // The message is deliberately vague ("invalid information") rather than
        // "this email is taken", which is the right call: a specific message
        // would turn the signup form into an account-existence oracle.
        assertAll(
                () -> assertTrue(register.errorMessage().contains("არასწორი"),
                        "A duplicate address should be refused with a non-specific message, "
                                + "but got: " + register.errorMessage()),
                () -> assertFalse(register.errorMessage().contains(existing),
                        "The rejection must not echo the address back and confirm it exists"));
    }

    @CrossBrowserTest
    @DisplayName("A wrong verification code is rejected and the account is not created")
    void wrongCodeIsRejected(WebDriver driver, WebDriverWait wait) {
        createdEmail = TestData.uniqueEmail("badcode");
        Instant sentAfter = Instant.now();

        VerifyEmailPage verify = new RegisterPage(driver, wait).get()
                .register(TestData.givenName(), TestData.familyName(),
                        createdEmail, TestData.password());

        String realCode = MailpitClient.awaitVerificationCode(createdEmail, sentAfter);
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        verify.confirmExpectingRejection(wrongCode);

        assertAll(
                () -> assertTrue(verify.message().contains("არასწორია"),
                        "The page should say the attempt was wrong, but showed: " + verify.message()),
                () -> assertFalse(ArtraDatabase.userExists(createdEmail),
                        "A rejected code must not create the account"));
    }

    @CrossBrowserTest
    @DisplayName("Requesting a new code invalidates the previous one")
    void resendInvalidatesThePreviousCode(WebDriver driver, WebDriverWait wait) {
        createdEmail = TestData.uniqueEmail("resend");
        Instant firstSentAfter = Instant.now();

        VerifyEmailPage verify = new RegisterPage(driver, wait).get()
                .register(TestData.givenName(), TestData.familyName(),
                        createdEmail, TestData.password());

        String firstCode = MailpitClient.awaitVerificationCode(createdEmail, firstSentAfter);

        Instant secondSentAfter = Instant.now();
        verify.resendCode();
        String secondCode = MailpitClient.awaitVerificationCode(createdEmail, secondSentAfter);

        assertNotEquals(firstCode, secondCode, "A resend should issue a different code");

        // The app stores one code per pending verification, so the resend has to
        // have replaced the first. If it had not, an old code intercepted from a
        // mailbox would stay usable for the full fifteen-minute window.
        verify.confirmExpectingRejection(firstCode);
        assertTrue(verify.message().contains("არასწორია"),
                "The superseded code should no longer be accepted, but the page said: "
                        + verify.message());

        verify.confirm(secondCode);
        assertTrue(ArtraDatabase.userExists(createdEmail),
                "The newly issued code should complete the registration");
    }
}
