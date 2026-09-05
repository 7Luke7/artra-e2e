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
 * {@code /reset/find} - "what is your email" step of the password reset.
 *
 * The interesting behaviour here is that the app deliberately answers the same
 * way for a known and an unknown address, so the page cannot be used to
 * enumerate accounts. The suite asserts exactly that, and proves the difference
 * where it actually exists - in whether a mail was sent.
 */
public class ResetFindPage extends BasePage<ResetFindPage> {

    private static final By SUBMIT = By.cssSelector("form[role='form'] button[type='submit']");
    private static final By NOTICE = By.cssSelector("div[role='status'] p");
    private static final By WARNING = By.cssSelector("div[role='alert'] p");
    private static final By EMAIL_HINT = By.id("email-constraints");

    /** Looking the user up and sending the mail happen before the response. */
    private static final Duration OUTCOME = Duration.ofSeconds(40);

    @FindBy(id = "email-reset")
    private WebElement email;

    public ResetFindPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/reset/find", By.id("email-reset"));
    }

    /** Submits an address and waits for the page's answer, whatever it is. */
    public ResetFindPage requestResetFor(String emailValue) {
        log.info("▶ Requesting a password reset for {}", emailValue);
        Interactions.setValue(driver, email, emailValue);
        Interactions.click(driver, visible(SUBMIT));
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> message() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "The reset request produced no message; the browser is at " + currentPath(), e);
        }
        return this;
    }

    /** The confirmation or error the page is showing. */
    public String message() {
        if (isPresent(NOTICE)) {
            return Interactions.textOf(all(NOTICE).get(0));
        }
        if (isPresent(WARNING)) {
            return Interactions.textOf(all(WARNING).get(0));
        }
        if (isPresent(EMAIL_HINT)) {
            WebElement hint = all(EMAIL_HINT).get(0);
            if ("alert".equals(hint.getDomAttribute("role"))) {
                return Interactions.textOf(hint);
            }
        }
        return null;
    }

    public boolean showsSuccessNotice() {
        return isPresent(NOTICE);
    }
}
