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
 * {@code /reset/password} - reached only by following the emailed link, which
 * exchanges its one-time token for a short-lived {@code reset_session} cookie.
 *
 * On success the app signs every device out and sends the browser back to
 * {@code /login}, which is the observable outcome this object waits for.
 */
public class ResetPasswordPage extends BasePage<ResetPasswordPage> {

    private static final By SUBMIT = By.cssSelector("form[role='form'] button[type='submit']");
    private static final By GLOBAL_ERROR = By.cssSelector("div[role='alert'] p");
    private static final By PASSWORD_HINT = By.id("password-constraints");

    /** The route's guard screen, shown when the reset_session cookie is absent
     *  or expired. */
    private static final By LINK_INVALID =
            By.xpath("//h1[normalize-space()='ბმული არ არის მოქმედი']");

    private static final Duration OUTCOME = Duration.ofSeconds(40);

    @FindBy(id = "new-password")
    private WebElement newPassword;

    @FindBy(id = "confirm-password")
    private WebElement confirmPassword;

    public ResetPasswordPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/reset/password", By.id("new-password"));
    }

    public ResetPasswordPage fill(String password, String confirmation) {
        Interactions.setValue(driver, newPassword, password);
        Interactions.setValue(driver, confirmPassword, confirmation);
        return this;
    }

    /** Sets a new password and waits for the redirect back to sign-in. */
    public LoginPage setPassword(String password) {
        log.info("▶ Setting a new password");
        fill(password, password);
        Interactions.click(driver, visible(SUBMIT));
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> currentPath().startsWith("/login"));
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "The password was not accepted - still on " + currentPath()
                            + (errorMessage() == null ? "" : " showing '" + errorMessage() + "'"), e);
        }
        return new LoginPage(driver, wait).waitUntilLoaded();
    }

    /** Submits a pair the app should reject and waits for the message. */
    public ResetPasswordPage setPasswordExpectingRejection(String password, String confirmation) {
        fill(password, confirmation);
        Interactions.click(driver, visible(SUBMIT));
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> errorMessage() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Expected the reset to be rejected with a message; the browser is at "
                            + currentPath(), e);
        }
        return this;
    }

    /**
     * True when the route refused to render the form because there is no valid
     * reset session.
     *
     * Waits for one of the two possible screens rather than checking once - the
     * guard is resolved asynchronously, so an immediate look finds neither.
     */
    public boolean isLinkInvalid() {
        wait.until(d -> Interactions.isPresent(d, LINK_INVALID)
                || Interactions.isPresent(d, By.id("new-password")));
        return isPresent(LINK_INVALID);
    }

    public String errorMessage() {
        if (isPresent(GLOBAL_ERROR)) {
            return Interactions.textOf(all(GLOBAL_ERROR).get(0));
        }
        if (isPresent(PASSWORD_HINT)) {
            WebElement hint = all(PASSWORD_HINT).get(0);
            if ("alert".equals(hint.getDomAttribute("role"))) {
                return Interactions.textOf(hint);
            }
        }
        return null;
    }
}
