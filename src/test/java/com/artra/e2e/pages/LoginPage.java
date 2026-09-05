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
 * {@code /login}.
 *
 * Submitting valid credentials does <em>not</em> sign the user in on this app:
 * Artra always requires a second factor, so a successful submit lands on
 * {@code /verify/pending}. {@link #signInExpectingVerification()} encodes that,
 * and {@link #signInExpectingRejection()} encodes the other outcome, so no test
 * has to re-derive which of the two it should wait for.
 */
public class LoginPage extends BasePage<LoginPage> {

    private static final By GLOBAL_ERROR = By.id("global-error");
    private static final By EMAIL_ERROR = By.id("email-error");
    private static final By PASSWORD_HINT = By.id("password-constraints");
    private static final By SUBMIT = By.cssSelector("form[role='form'] button[type='submit']");

    /** How long a rejected sign-in gets to render its message. Server-side
     *  Argon2 verification is deliberately slow, so this is well above the
     *  usual "the DOM updated" wait. */
    private static final Duration OUTCOME = Duration.ofSeconds(30);

    @FindBy(id = "email")
    private WebElement email;

    @FindBy(id = "current-password")
    private WebElement password;

    @FindBy(id = "remember-me")
    private WebElement rememberMe;

    public LoginPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/login", By.id("current-password"));
    }

    public LoginPage enterEmail(String value) {
        Interactions.setValue(driver, email, value);
        return this;
    }

    public LoginPage enterPassword(String value) {
        Interactions.setValue(driver, password, value);
        return this;
    }

    public LoginPage rememberMe() {
        if (!rememberMe.isSelected()) {
            Interactions.click(driver, rememberMe);
        }
        return this;
    }

    public boolean isRememberMeChecked() {
        return rememberMe.isSelected();
    }

    public void submit() {
        Interactions.click(driver, visible(SUBMIT));
    }

    /**
     * Signs in with credentials the app should accept, and returns the
     * verification-method screen it hands back.
     */
    public VerifyPendingPage signInExpectingVerification(String emailValue, String passwordValue) {
        log.info("▶ Signing in as {}", emailValue);
        enterEmail(emailValue);
        enterPassword(passwordValue);
        submit();
        return new VerifyPendingPage(driver, wait).waitUntilLoaded();
    }

    /**
     * Signs in with credentials the app should reject, and waits for the error
     * the page renders in place of a redirect.
     *
     * Waiting for the message rather than asserting "still on /login" matters:
     * immediately after the click the browser is also still on /login, so the
     * weaker check passes before the app has done anything at all.
     */
    public LoginPage signInExpectingRejection(String emailValue, String passwordValue) {
        log.info("▶ Signing in as {} (expecting rejection)", emailValue);
        enterEmail(emailValue);
        enterPassword(passwordValue);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> errorMessage() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Expected the sign-in to be rejected with a message, but none appeared. "
                            + "The browser is at " + currentPath(), e);
        }
        return this;
    }

    /**
     * The rejection message, wherever the page chose to put it: a global banner
     * for an unknown account, an inline field message for a wrong password.
     * Returns null while no error is showing.
     */
    public String errorMessage() {
        if (isPresent(GLOBAL_ERROR)) {
            return Interactions.textOf(all(GLOBAL_ERROR).get(0).findElement(By.tagName("p")));
        }
        if (isPresent(EMAIL_ERROR)) {
            return Interactions.textOf(all(EMAIL_ERROR).get(0));
        }
        // The password field reuses its hint slot for the error and flips it to
        // role="alert", which is the only thing distinguishing the two states.
        if (isPresent(PASSWORD_HINT)) {
            WebElement hint = all(PASSWORD_HINT).get(0);
            if ("alert".equals(hint.getDomAttribute("role"))) {
                return Interactions.textOf(hint);
            }
        }
        return null;
    }

    public boolean isPasswordMasked() {
        return "password".equals(password.getDomAttribute("type"));
    }

    /** Clicks the eye toggle next to the password field. */
    public LoginPage togglePasswordVisibility() {
        Interactions.clickUntil(driver, wait,
                () -> driver.findElement(By.cssSelector("button[aria-controls='current-password']")),
                d -> !isPasswordMasked(),
                "reveal the password");
        return this;
    }

    public RegisterPage goToRegister() {
        Interactions.click(driver, visible(By.cssSelector("a[href='/register']")));
        return new RegisterPage(driver, wait).waitUntilLoaded();
    }

    public ResetFindPage goToPasswordReset() {
        Interactions.click(driver, visible(By.cssSelector("a[href='/reset/find']")));
        return new ResetFindPage(driver, wait).waitUntilLoaded();
    }
}
