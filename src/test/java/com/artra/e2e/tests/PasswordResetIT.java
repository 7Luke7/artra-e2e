package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.LoginPage;
import com.artra.e2e.pages.ResetFindPage;
import com.artra.e2e.pages.ResetPasswordPage;
import com.artra.e2e.support.ArtraDatabase;
import com.artra.e2e.support.Flows;
import com.artra.e2e.support.MailpitClient;
import com.artra.e2e.support.TestData;

/**
 * Password recovery, end to end.
 *
 * The flow crosses four boundaries - form, mail, a one-time token exchanged for
 * a short-lived cookie, and finally the password update - and the only outcome
 * a user cares about is the last one: can they sign in afterwards. So that is
 * what the happy-path test asserts, rather than stopping at the confirmation
 * message.
 *
 * Each invocation resets its own freshly registered account. Sharing one would
 * be worse than slow: the reset signs every session out, so two concurrent
 * invocations would tear down each other's browsers mid-test.
 */
@Tag("auth")
@Tag("email")
class PasswordResetIT {

    private String createdEmail;

    @AfterEach
    void removeAccount() {
        if (createdEmail != null) {
            ArtraDatabase.deleteUser(createdEmail);
        }
    }

    @CrossBrowserTest
    @DisplayName("A forgotten password can be reset through the emailed link and used to sign in")
    void resetLinkLetsTheUserSetAndUseANewPassword(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        Instant sentAfter = Instant.now();
        ResetFindPage find = new ResetFindPage(driver, wait).get()
                .requestResetFor(account.email());

        assertTrue(find.showsSuccessNotice(),
                "The page should confirm that instructions were sent, but said: " + find.message());

        MailpitClient.Message mail = MailpitClient.awaitMessage(account.email(), sentAfter);
        assertTrue(mail.subject().contains("პაროლის აღდგენა"),
                "The reset mail should be the recovery email, but its subject was: " + mail.subject());

        // Following the link is the whole point: it exchanges the one-time token
        // for the short-lived reset_session cookie that the form requires.
        driver.get(MailpitClient.resetLink(mail));

        String newPassword = TestData.password();
        new ResetPasswordPage(driver, wait).waitUntilLoaded().setPassword(newPassword);

        assertEquals("/login", new LoginPage(driver, wait).waitUntilLoaded().currentPath(),
                "A completed reset should return the user to the sign-in page");

        LandingPage landing = Flows.signIn(driver, wait, account.email(), newPassword);
        assertTrue(landing.header().isSignedIn(),
                "The new password should work for signing in");
    }

    @CrossBrowserTest
    @DisplayName("The old password stops working once a reset completes")
    void oldPasswordStopsWorkingAfterAReset(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        Instant sentAfter = Instant.now();
        new ResetFindPage(driver, wait).get().requestResetFor(account.email());
        driver.get(MailpitClient.awaitResetLink(account.email(), sentAfter));
        new ResetPasswordPage(driver, wait).waitUntilLoaded().setPassword(TestData.password());

        LoginPage login = new LoginPage(driver, wait).waitUntilLoaded()
                .signInExpectingRejection(account.email(), account.password());

        assertAll(
                () -> assertNotNull(login.errorMessage(),
                        "The superseded password should be refused"),
                () -> assertEquals("/login", login.currentPath(),
                        "A refused sign-in should stay on the sign-in page"));
    }

    @CrossBrowserTest
    @DisplayName("An address with no account gets the same answer and no email")
    void unknownAddressIsNotDisclosed(WebDriver driver, WebDriverWait wait) {
        String unknown = TestData.uniqueEmail("no-account");

        ResetFindPage find = new ResetFindPage(driver, wait).get().requestResetFor(unknown);

        // Answering differently here would turn password recovery into an
        // account-existence check, so the identical confirmation is the correct
        // behaviour and worth pinning down.
        assertAll(
                () -> assertTrue(find.showsSuccessNotice(),
                        "An unknown address should get the same confirmation as a known one, "
                                + "but the page said: " + find.message()),
                () -> assertEquals(0, MailpitClient.messageCount(unknown),
                        "No mail should actually be sent to an address with no account"));
    }

    @CrossBrowserTest
    @DisplayName("A mismatched confirmation is refused and the password is unchanged")
    void mismatchedConfirmationIsRefused(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        Instant sentAfter = Instant.now();
        new ResetFindPage(driver, wait).get().requestResetFor(account.email());
        driver.get(MailpitClient.awaitResetLink(account.email(), sentAfter));

        String chosen = TestData.password();
        String mistyped = chosen + "-typo";

        ResetPasswordPage reset = new ResetPasswordPage(driver, wait).waitUntilLoaded()
                .setPasswordExpectingRejection(chosen, mistyped);

        assertTrue(reset.errorMessage().contains("არ ემთხვევა"),
                "Two different passwords should be refused as a mismatch, but the page said: "
                        + reset.errorMessage());

        // The original password must still work, or a failed reset has quietly
        // locked the user out.
        driver.get(reset.url("/login"));
        LandingPage landing = Flows.signIn(driver, wait, account.email(), account.password());
        assertTrue(landing.header().isSignedIn(),
                "A refused reset should leave the existing password working");
    }

    @CrossBrowserTest
    @DisplayName("The reset form cannot be opened without following the emailed link")
    void resetFormRequiresTheEmailedLink(WebDriver driver, WebDriverWait wait) {
        ResetPasswordPage reset = new ResetPasswordPage(driver, wait);
        driver.get(reset.url("/reset/password"));

        // Without the reset_session cookie the route must not render a usable
        // form - otherwise the password of any account could be set by anyone
        // who guesses the URL.
        assertTrue(reset.isLinkInvalid(),
                "Opening /reset/password directly must not present a working password form");
    }
}
