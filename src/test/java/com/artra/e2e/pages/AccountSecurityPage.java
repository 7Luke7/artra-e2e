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
 * {@code /account/security} - change the password, review active sessions.
 *
 * Changing a password here also signs every <em>other</em> device out, which is
 * the security behaviour worth an end-to-end test: it spans the form, the
 * database and the session store, and no unit test can see all three.
 */
public class AccountSecurityPage extends BasePage<AccountSecurityPage> {

    private static final By HEADING = By.id("security-heading");
    private static final By SUBMIT = By.cssSelector("button[aria-label='პაროლის შეცვლა']");
    private static final By RESULT = By.cssSelector("form div[role='alert'] p");
    // The list is built from ARIA roles, not from <ul>/<li> - so the rows have
    // to be found by role="listitem", which is what a screen reader sees and
    // what the application actually renders.
    private static final By SESSIONS =
            By.cssSelector("[aria-label='ავტორიზაციის სესიების სია'] [role='listitem']");
    private static final By SESSIONS_HEADING = By.id("sessions-heading");

    /** Verifying the old password and hashing the new one are both Argon2
     *  operations, and the handler re-issues the session afterwards. */
    private static final Duration OUTCOME = Duration.ofSeconds(45);

    @FindBy(id = "current-password")
    private WebElement currentPassword;

    @FindBy(id = "new-password")
    private WebElement newPassword;

    @FindBy(id = "confirm-password")
    private WebElement confirmPassword;

    public AccountSecurityPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/account/security", HEADING);
    }

    /**
     * Submits a password change and waits for the app's verdict.
     *
     * Returns the message either way: the same form reports success and
     * failure through the same element, so a caller that wants one specific
     * outcome asserts on the text rather than on which method it called.
     */
    public String changePassword(String current, String next, String confirmation) {
        log.info("▶ Changing the account password");
        Interactions.setValue(driver, currentPassword, current);
        Interactions.setValue(driver, newPassword, next);
        Interactions.setValue(driver, confirmPassword, confirmation);
        Interactions.click(driver, visible(SUBMIT));

        try {
            new WebDriverWait(driver, OUTCOME).until(d -> isPresent(RESULT));
        } catch (TimeoutException e) {
            throw new TimeoutException("The password form reported neither success nor failure", e);
        }
        return textOf(RESULT);
    }

    public boolean showsSessions() {
        return isPresent(SESSIONS_HEADING);
    }

    /**
     * How many devices the app currently lists as signed in.
     *
     * The list is loaded asynchronously after the page renders, so this waits
     * for the first row rather than reading a section that is still empty -
     * and falls back to reporting zero once the heading has been up long
     * enough for "no sessions" to be a real answer.
     */
    public int sessionCount() {
        wait.until(d -> isPresent(SESSIONS_HEADING));
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(SESSIONS).isEmpty());
        } catch (TimeoutException e) {
            log.warn("■ No session rows appeared within 10s - reading the list as empty");
        }
        return all(SESSIONS).size();
    }
}
