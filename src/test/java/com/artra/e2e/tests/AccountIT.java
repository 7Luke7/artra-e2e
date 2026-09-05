package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.AccountPage;
import com.artra.e2e.pages.AccountSecurityPage;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.LoginPage;
import com.artra.e2e.support.ArtraDatabase;
import com.artra.e2e.support.Flows;
import com.artra.e2e.support.TestData;

/**
 * The signed-in account area.
 *
 * Each invocation registers its own account, because the most valuable test
 * here changes a password - and a shared fixture whose password changes mid-run
 * breaks every other test that uses it, on a different browser, for reasons
 * that take a long evening to work out.
 */
@Tag("account")
class AccountIT {

    private String createdEmail;

    @AfterEach
    void removeAccount() {
        if (createdEmail != null) {
            ArtraDatabase.deleteUser(createdEmail);
        }
    }

    @CrossBrowserTest
    @DisplayName("The account page shows the signed-in user's own details")
    void accountShowsTheSignedInUser(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        AccountPage page = new AccountPage(driver, wait).get();

        assertAll(
                () -> assertEquals(account.fullName(), page.displayName(),
                        "The profile should show the name the account was created with"),
                () -> assertEquals(account.email(), page.email(),
                        "The profile should show the account's email address"),
                () -> assertFalse(page.joinedOn().isBlank(),
                        "The profile should show when the account was created"),
                () -> assertEquals(List.of("აქაუნთი", "უსაფრთხოება"), page.sectionCaptions(),
                        "The account area should offer its profile and security sections"));
    }

    @CrossBrowserTest
    @DisplayName("The security page lists the session the visitor is signed in with")
    void securityPageListsActiveSessions(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        AccountSecurityPage security = new AccountPage(driver, wait).get().openSecurity();

        assertAll(
                () -> assertTrue(security.showsSessions(), "Active sessions should be listed"),
                () -> assertTrue(security.sessionCount() >= 1,
                        "The browser's own session should appear in the list"));
    }

    @CrossBrowserTest
    @DisplayName("Changing the password succeeds and the new one works on the next sign-in")
    void passwordChangeTakesEffect(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        String newPassword = TestData.password();

        AccountSecurityPage security = new AccountPage(driver, wait).get().openSecurity();
        String result = security.changePassword(account.password(), newPassword, newPassword);

        assertTrue(result.contains("წარმატებით"),
                "The password change should report success, but said: " + result);

        // A success message is not the outcome that matters. Signing in again
        // with the new password is.
        new LandingPage(driver, wait).get().header().signOut();
        LandingPage landing = Flows.signIn(driver, wait, account.email(), newPassword);

        assertTrue(landing.header().isSignedIn(),
                "The new password should work on the next sign-in");
    }

    @CrossBrowserTest
    @DisplayName("Changing the password is refused when the current one is wrong")
    void passwordChangeRequiresTheCurrentPassword(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        String attempted = TestData.password();

        AccountSecurityPage security = new AccountPage(driver, wait).get().openSecurity();
        String result = security.changePassword("not-the-current-password", attempted, attempted);

        assertTrue(result.contains("არასწორია"),
                "A wrong current password should be refused, but the page said: " + result);

        // And the original password must still work - a refused change that
        // quietly applied anyway would lock the user out.
        new LandingPage(driver, wait).get().header().signOut();
        LoginPage login = new LoginPage(driver, wait).waitUntilLoaded();
        login.signInExpectingVerification(account.email(), account.password());

        assertTrue(driver.getCurrentUrl().contains("/verify/pending"),
                "The original password should still be accepted after a refused change");
    }

    @CrossBrowserTest
    @DisplayName("Changing the password is refused when the confirmation does not match")
    void passwordChangeRequiresAMatchingConfirmation(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();
        String chosen = TestData.password();

        AccountSecurityPage security = new AccountPage(driver, wait).get().openSecurity();
        String result = security.changePassword(account.password(), chosen, chosen + "-typo");

        assertTrue(result.contains("არ ემთხვევა"),
                "A mistyped confirmation should be refused as a mismatch, but the page said: "
                        + result);
    }
}
