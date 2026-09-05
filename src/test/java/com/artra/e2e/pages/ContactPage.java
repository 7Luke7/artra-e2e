package com.artra.e2e.pages;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;

/**
 * {@code /contact}.
 *
 * The only write an anonymous visitor can make, which makes it the cheapest
 * honest end-to-end check that a form reaches the database at all - and the
 * one place where the suite verifies a row directly, because the app shows the
 * message back to nobody.
 */
public class ContactPage extends BasePage<ContactPage> {

    private static final By SUBMIT = By.cssSelector("form[role='form'] button[type='submit']");
    private static final By SUCCESS = By.cssSelector("div[role='status'] p");
    private static final By GLOBAL_ERROR = By.cssSelector("#global-error p");
    private static final By FIELD_ERROR = By.cssSelector("div[role='alert']");

    private static final Duration OUTCOME = Duration.ofSeconds(30);

    @FindBy(id = "name")
    private WebElement name;

    @FindBy(id = "email")
    private WebElement email;

    @FindBy(id = "message")
    private WebElement message;

    public ContactPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/contact", By.id("message"));
    }

    public ContactPage fill(String nameValue, String emailValue, String subject, String body) {
        Interactions.setValue(driver, name, nameValue);
        Interactions.setValue(driver, email, emailValue);
        new Select(visible(By.cssSelector("select[name='subject']"))).selectByVisibleText(subject);
        Interactions.setValue(driver, message, body);
        return this;
    }

    public void submit() {
        Interactions.click(driver, visible(SUBMIT));
    }

    /** Sends a message the app should accept and waits for the receipt. */
    public ContactPage send(String nameValue, String emailValue, String subject, String body) {
        log.info("▶ Sending a contact message as {}", emailValue);
        fill(nameValue, emailValue, subject, body);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> isPresent(SUCCESS) || errorMessage() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException("The contact form neither confirmed nor failed", e);
        }
        return this;
    }

    /** Sends a message the app should reject and waits for the message. */
    public ContactPage sendExpectingRejection(String nameValue, String emailValue,
                                              String subject, String body) {
        fill(nameValue, emailValue, subject, body);
        submit();
        try {
            new WebDriverWait(driver, OUTCOME).until(d -> errorMessage() != null);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Expected the contact form to be rejected with a message"
                            + (isPresent(SUCCESS) ? ", but it reported success instead" : ""), e);
        }
        return this;
    }

    public boolean wasAccepted() {
        return isPresent(SUCCESS);
    }

    public String successMessage() {
        return textOf(SUCCESS);
    }

    public String errorMessage() {
        if (isPresent(GLOBAL_ERROR)) {
            return Interactions.textOf(all(GLOBAL_ERROR).get(0));
        }
        if (isPresent(FIELD_ERROR)) {
            return Interactions.textOf(all(FIELD_ERROR).get(0));
        }
        return null;
    }

    /**
     * What the fields currently hold.
     *
     * Read from the DOM's value property, not from the page source: typing into
     * an input does not update its value <em>attribute</em>, so the rendered
     * HTML still shows an empty field while the browser is holding the text.
     * That distinction is the difference between this assertion meaning
     * something and it always failing.
     */
    public String enteredEmail() {
        return email.getDomProperty("value");
    }

    public String enteredMessage() {
        return message.getDomProperty("value");
    }

    /**
     * Whether the browser itself would block the submit.
     *
     * The message field carries {@code minlength=50}, so a short message never
     * reaches the server. Asserting on constraint validity states that
     * deliberately, instead of submitting and being puzzled that no server
     * error came back.
     */
    public boolean isMessageValid() {
        return Boolean.TRUE.equals(
                script("return document.getElementById('message').checkValidity();"));
    }
}
