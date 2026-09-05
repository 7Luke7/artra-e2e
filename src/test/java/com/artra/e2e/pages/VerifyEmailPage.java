package com.artra.e2e.pages;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;

/**
 * {@code /verify/email} - the six-digit code screen, reached from registration
 * and from the email branch of sign-in.
 *
 * A correct code is the point at which the app actually creates the account (on
 * signup) or the session (on sign-in), so this page is the hinge of both email
 * flows. The code itself comes from the test inbox, never from the database:
 * Artra stores only an HMAC of it, so reading the database would prove nothing
 * about what was delivered.
 */
public class VerifyEmailPage extends BasePage<VerifyEmailPage> {

    /**
     * Both buttons on this page are {@code type=submit} inside their own form,
     * so they can only be told apart by what they sit next to (the code field)
     * or by their caption. Anchoring the primary button to the form that owns
     * the input keeps it correct if the resend control moves.
     */
    private static final By SUBMIT =
            By.xpath("//form[.//input[@id='one-time-code']]//button[@type='submit']");
    private static final By RESEND = By.xpath(
            "//button[contains(., 'ხელახლა გაგზავნა') or contains(., 'იგზავნება')]");

    private static final By CODE_ERROR = By.cssSelector("div[role='alert'] p");
    private static final By NOTICE = By.cssSelector("div[role='status'] p");

    /** The guard screen the route renders when there is no pending
     *  verification to confirm. */
    private static final By ACCESS_DENIED = By.id("error-title");

    /** Confirming a signup code writes a user, a device and a session before it
     *  redirects. */
    private static final Duration OUTCOME = Duration.ofSeconds(40);

    @FindBy(id = "one-time-code")
    private WebElement code;

    public VerifyEmailPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/verify/email", By.id("one-time-code"));
    }

    public VerifyEmailPage enterCode(String value) {
        Interactions.setValue(driver, code, value);
        return this;
    }

    public void submit() {
        Interactions.click(driver, visible(SUBMIT));
    }

    /**
     * Submits a code the app should accept and waits for it to leave this page.
     *
     * Both flows redirect to {@code /}, where the caller decides which of the
     * two landing variants it expected.
     */
    public LandingPage confirm(String value) {
        log.info("▶ Confirming verification code");
        enterCode(value);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> !currentPath().startsWith("/verify"));
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "The verification code was not accepted - still on " + currentPath()
                            + (message() == null ? "" : " showing '" + message() + "'"), e);
        }
        return new LandingPage(driver, wait).waitUntilLoaded();
    }

    /** Submits a code the app should reject and waits for the message. */
    public VerifyEmailPage confirmExpectingRejection(String value) {
        enterCode(value);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> message() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Expected the code to be rejected with a message; the browser is at "
                            + currentPath(), e);
        }
        return this;
    }

    /** Asks for a new code and waits for the confirmation notice. */
    public VerifyEmailPage resendCode() {
        log.info("▶ Requesting a new verification code");
        Interactions.click(driver, visible(RESEND));
        wait.until(d -> isPresent(NOTICE) || message() != null);
        return this;
    }

    /** Whatever the page is currently reporting - error or success. */
    public String message() {
        if (isPresent(CODE_ERROR)) {
            return Interactions.textOf(all(CODE_ERROR).get(0));
        }
        if (isPresent(NOTICE)) {
            return Interactions.textOf(all(NOTICE).get(0));
        }
        return null;
    }

    /**
     * True when the route refused to render because there is no pending
     * verification - the guard a direct visit to this URL should hit.
     *
     * Safe to call without waitUntilLoaded(), which is the point: the denied
     * screen has no code field, so waiting for this page first would fail
     * before the assertion could run.
     */
    public boolean isAccessDenied() {
        wait.until(d -> isPresent(ACCESS_DENIED) || isPresent(By.id("one-time-code")));
        return isPresent(ACCESS_DENIED);
    }

    /** Heading of the guard screen, e.g. "წვდომა შეზღუდულია". */
    public String accessDeniedHeading() {
        return textOf(ACCESS_DENIED);
    }
}
