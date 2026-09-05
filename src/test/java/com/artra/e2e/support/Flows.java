package com.artra.e2e.support;

import java.time.Instant;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.LoginPage;
import com.artra.e2e.pages.RegisterPage;
import com.artra.e2e.pages.VerifyEmailPage;

/**
 * The multi-step journeys that other tests need as a <em>precondition</em>.
 *
 * Signing in to Artra is four screens and an email round trip. Tests whose
 * subject is something else - the account page, a protected route, changing a
 * password - would otherwise each re-spell that, and every one of them would
 * then fail for "login is broken" reasons. Composing it once here keeps those
 * tests about their own subject, while the flows themselves stay directly under
 * test in AuthenticationIT and RegistrationIT.
 *
 * Nothing here shortcuts the application. A signed-in session is produced by
 * actually signing in, not by writing a session into Redis - a fixture that
 * fabricates the state under test is how a suite ends up green against a broken
 * login.
 */
public final class Flows {

    private static final Logger log = LoggerFactory.getLogger(Flows.class);

    private Flows() {
    }

    /**
     * Registers a brand-new account and confirms it, leaving the browser signed
     * in.
     *
     * @return the account, so the caller can assert against it and delete it in
     *         teardown
     */
    public static Account registerAndVerify(WebDriver driver, WebDriverWait wait) {
        Account account = Account.random();

        // Marked before the form is submitted so the inbox lookup can reject
        // anything older - see MailpitClient.awaitMessage.
        Instant sentAfter = Instant.now();

        VerifyEmailPage verify = new RegisterPage(driver, wait).get()
                .register(account.givenName(), account.familyName(),
                        account.email(), account.password());

        String code = MailpitClient.awaitVerificationCode(account.email(), sentAfter);
        verify.confirm(code);

        log.info("✓ Registered and verified {}", account.email());
        return account;
    }

    /**
     * Signs an existing account in through the email second factor and waits
     * for the landing page it lands on.
     */
    public static LandingPage signIn(WebDriver driver, WebDriverWait wait,
                                     String email, String password) {
        Instant sentAfter = Instant.now();

        VerifyEmailPage verify = new LoginPage(driver, wait).get()
                .signInExpectingVerification(email, password)
                .chooseEmailCode();

        String code = MailpitClient.awaitVerificationCode(email, sentAfter);
        LandingPage landing = verify.confirm(code);

        log.info("✓ Signed in as {}", email);
        return landing;
    }

    /** An account this run created, with everything a test needs to use or
     *  clean it up. */
    public record Account(String givenName, String familyName, String email, String password) {

        static Account random() {
            return new Account(
                    TestData.givenName(),
                    TestData.familyName(),
                    TestData.uniqueEmail("signup"),
                    TestData.password());
        }

        public String fullName() {
            return givenName + " " + familyName;
        }
    }
}
