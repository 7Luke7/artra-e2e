package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.ConfigProvider;
import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.LoginPage;
import com.artra.e2e.pages.VerifyPendingPage;
import com.artra.e2e.support.ArtraDatabase;
import com.artra.e2e.support.Flows;
import com.artra.e2e.support.MailpitClient;
import com.artra.e2e.support.TestData;

/**
 * Signing in, signing out, and the guards around both.
 *
 * <h2>Why each test provisions its own account</h2>
 *
 * The suite runs three browsers concurrently, and every sign-in sends a code to
 * the account's mailbox. Two invocations sharing one address would race for
 * each other's codes and fail in a way that looks exactly like a broken
 * application. Tests that need a real session therefore register their own
 * account first; only the checks that never trigger an email - a wrong password,
 * an unknown address - reuse the seeded fixture.
 */
@Tag("auth")
class AuthenticationIT {

    private String createdEmail;

    @AfterEach
    void removeAccount() {
        if (createdEmail != null) {
            ArtraDatabase.deleteUser(createdEmail);
        }
    }

    @CrossBrowserTest
    @DisplayName("Correct credentials are not enough on their own - a second factor is required")
    void correctCredentialsRequireASecondFactor(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        VerifyPendingPage pending = new LoginPage(driver, wait).waitUntilLoaded()
                .signInExpectingVerification(account.email(), account.password());

        List<String> methods = pending.offeredMethods();
        assertAll(
                () -> assertEquals(2, methods.size(),
                        "Both verification methods should be offered, but got: " + methods),
                () -> assertTrue(pending.offersDeviceApproval(),
                        "Approval from an already-trusted device should be offered"));

        // The point of the test: valid credentials alone leave the visitor
        // unauthenticated. Asked directly, a protected route still bounces -
        // which is a stronger statement than the chooser merely being on screen.
        driver.get(pending.url("/account"));
        wait.until(ExpectedConditions.urlContains("/login"));

        assertTrue(driver.getCurrentUrl().contains("/login"),
                "A half-completed sign-in must not grant access to a protected route");
    }

    @CrossBrowserTest
    @DisplayName("Sign-in completes once the emailed code is confirmed")
    void signInCompletesWithTheEmailedCode(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        LandingPage landing = Flows.signIn(driver, wait, account.email(), account.password());

        assertAll(
                () -> assertTrue(landing.header().isSignedIn(),
                        "Confirming the code should establish a session"),
                () -> assertTrue(landing.greeting().contains(account.givenName()),
                        "The dashboard should greet the signed-in user by name, but read: "
                                + landing.greeting()));
    }

    @CrossBrowserTest
    @DisplayName("A wrong password is rejected without signing the visitor in")
    void wrongPasswordIsRejected(WebDriver driver, WebDriverWait wait) {
        String seededAccount = ConfigProvider.get().seedStudentEmail();

        LoginPage login = new LoginPage(driver, wait).get()
                .signInExpectingRejection(seededAccount, "definitely-not-the-password");

        assertAll(
                () -> assertNotNull(login.errorMessage(), "A rejection message should be shown"),
                () -> assertEquals("/login", login.currentPath(),
                        "A rejected sign-in should stay on the sign-in page"),
                () -> assertEquals(0, MailpitClient.messageCount(seededAccount),
                        "No verification code should be sent for a failed sign-in"));
    }

    @CrossBrowserTest
    @DisplayName("An unknown address is rejected with a message that does not confirm it")
    void unknownAddressIsRejected(WebDriver driver, WebDriverWait wait) {
        String unknown = TestData.uniqueEmail("nobody");

        LoginPage login = new LoginPage(driver, wait).get()
                .signInExpectingRejection(unknown, TestData.password());

        assertAll(
                () -> assertNotNull(login.errorMessage(), "A rejection message should be shown"),
                () -> assertFalse(login.errorMessage().contains(unknown),
                        "The message must not echo the address back"),
                () -> assertEquals(0, MailpitClient.messageCount(unknown),
                        "Nothing should be mailed to an address with no account"));
    }

    /**
     * Documents a real finding rather than asserting the app is perfect.
     *
     * Artra answers an unknown address with a generic "invalid details" but
     * answers a known address with a wrong password with "the password is
     * incorrect" - so the two are distinguishable, and the form can be used to
     * check whether an address has an account. The test pins the behaviour as
     * it is today; if it is tightened, this fails and the change is deliberate
     * rather than accidental.
     */
    @CrossBrowserTest
    @DisplayName("Known and unknown addresses are currently distinguishable on failure (finding)")
    void failureMessagesDistinguishKnownAccounts(WebDriver driver, WebDriverWait wait) {
        String known = ConfigProvider.get().seedStudentEmail();
        String unknown = TestData.uniqueEmail("nobody");

        String forKnown = new LoginPage(driver, wait).get()
                .signInExpectingRejection(known, "definitely-not-the-password")
                .errorMessage();

        driver.navigate().refresh();

        String forUnknown = new LoginPage(driver, wait).waitUntilLoaded()
                .signInExpectingRejection(unknown, TestData.password())
                .errorMessage();

        assertNotNull(forKnown);
        assertNotNull(forUnknown);
        assertFalse(forKnown.equals(forUnknown),
                "This test records that the two messages differ today. If they have been "
                        + "made identical, that is an improvement - delete this test.");
    }

    @CrossBrowserTest
    @DisplayName("Signing out ends the session and returns the visitor to the sign-in page")
    void signOutEndsTheSession(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        new LandingPage(driver, wait).waitUntilLoaded().header().signOut();

        assertTrue(driver.getCurrentUrl().contains("/login"),
                "Signing out should land on the sign-in page");

        // The session has to be gone server-side, not just hidden: asking for a
        // protected route again is the only way to prove that.
        driver.get(new LoginPage(driver, wait).url("/account"));
        wait.until(ExpectedConditions.urlContains("/login"));

        assertTrue(driver.getCurrentUrl().contains("/login"),
                "A protected route should redirect to sign-in once the session has ended");
    }

    @CrossBrowserTest
    @DisplayName("A signed-in visitor is redirected away from the sign-in page")
    void signedInVisitorCannotOpenTheSignInPage(WebDriver driver, WebDriverWait wait) {
        Flows.Account account = Flows.registerAndVerify(driver, wait);
        createdEmail = account.email();

        driver.get(new LandingPage(driver, wait).url("/login"));

        // A signed-in user reaching the sign-in form is how duplicate sessions
        // and confusing "you are already signed in" states start.
        wait.until(d -> !d.getCurrentUrl().contains("/login"));
        assertTrue(new LandingPage(driver, wait).waitUntilLoaded().isDashboard(),
                "A signed-in visitor should be sent to their dashboard instead");
    }

    @CrossBrowserTest
    @DisplayName("The password field is masked until the reveal control is used")
    void passwordIsMaskedByDefault(WebDriver driver, WebDriverWait wait) {
        LoginPage login = new LoginPage(driver, wait).get();

        assertTrue(login.isPasswordMasked(), "The password should be masked when the page opens");

        login.enterPassword(TestData.password()).togglePasswordVisibility();

        assertFalse(login.isPasswordMasked(), "The reveal control should unmask the password");
    }

    @CrossBrowserTest
    @DisplayName("Sign-in offers routes to registration and password recovery")
    void signInOffersTheAdjacentJourneys(WebDriver driver, WebDriverWait wait) {
        assertEquals("/register", new LoginPage(driver, wait).get().goToRegister().currentPath(),
                "The sign-in page should lead to registration");

        assertEquals("/reset/find", new LoginPage(driver, wait).get().goToPasswordReset().currentPath(),
                "The sign-in page should lead to password recovery");
    }
}
