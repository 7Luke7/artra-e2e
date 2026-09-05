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
 * {@code /register}.
 *
 * A successful submit does not create the account: Artra parks the details in
 * Redis, mails a six-digit code and redirects to {@code /verify/email}. The
 * user only exists once that code is confirmed, which is why the registration
 * test has to go through the inbox to prove anything.
 */
public class RegisterPage extends BasePage<RegisterPage> {

    private static final By GLOBAL_ERROR = By.id("global-error");
    private static final By GIVEN_NAME_ERROR = By.id("given_name-error");
    private static final By FAMILY_NAME_ERROR = By.id("family_name-error");
    private static final By EMAIL_ERROR = By.id("email-error");
    private static final By PASSWORD_HINT = By.id("password-constraints");
    private static final By SUBMIT = By.cssSelector("form[role='form'] button[type='submit']");

    /** Registration hashes the password with Argon2id and sends a mail before
     *  it answers, so its round trip is much longer than a plain form post. */
    private static final Duration OUTCOME = Duration.ofSeconds(40);

    @FindBy(id = "given-name")
    private WebElement givenName;

    @FindBy(id = "family-name")
    private WebElement familyName;

    @FindBy(id = "email")
    private WebElement email;

    @FindBy(id = "new-password")
    private WebElement password;

    @FindBy(id = "remember-me")
    private WebElement rememberMe;

    public RegisterPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/register", By.id("given-name"));
    }

    public RegisterPage fill(String given, String family, String emailValue, String passwordValue) {
        Interactions.setValue(driver, givenName, given);
        Interactions.setValue(driver, familyName, family);
        Interactions.setValue(driver, email, emailValue);
        Interactions.setValue(driver, password, passwordValue);
        return this;
    }

    public RegisterPage rememberMe() {
        if (!rememberMe.isSelected()) {
            Interactions.click(driver, rememberMe);
        }
        return this;
    }

    public void submit() {
        Interactions.click(driver, visible(SUBMIT));
    }

    /** Submits details the app should accept and returns the code screen. */
    public VerifyEmailPage register(String given, String family, String emailValue, String passwordValue) {
        log.info("▶ Registering {}", emailValue);
        fill(given, family, emailValue, passwordValue);
        submit();
        return new VerifyEmailPage(driver, wait).waitUntilLoaded();
    }

    /** Submits details the app should reject and waits for the message. */
    public RegisterPage registerExpectingRejection(String given, String family,
                                                   String emailValue, String passwordValue) {
        fill(given, family, emailValue, passwordValue);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> errorMessage() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Expected registration to be rejected with a message, but none appeared. "
                            + "The browser is at " + currentPath(), e);
        }
        return this;
    }

    /** The message the page is showing, from whichever slot carries it. */
    public String errorMessage() {
        for (By locator : new By[]{GLOBAL_ERROR, GIVEN_NAME_ERROR, FAMILY_NAME_ERROR, EMAIL_ERROR}) {
            if (isPresent(locator)) {
                WebElement box = all(locator).get(0);
                // The global banner wraps the message in its first <p> and adds
                // a fixed "check your fields" line after it.
                return locator.equals(GLOBAL_ERROR)
                        ? Interactions.textOf(box.findElement(By.tagName("p")))
                        : Interactions.textOf(box);
            }
        }
        if (isPresent(PASSWORD_HINT)) {
            WebElement hint = all(PASSWORD_HINT).get(0);
            if ("alert".equals(hint.getDomAttribute("role"))) {
                return Interactions.textOf(hint);
            }
        }
        return null;
    }

    /** True while the browser's own constraint validation is blocking the
     *  submit - used to assert that client-side rules exist at all. */
    public boolean isFieldValid(String fieldId) {
        return Boolean.TRUE.equals(
                script("return document.getElementById(arguments[0]).checkValidity();", fieldId));
    }
}
