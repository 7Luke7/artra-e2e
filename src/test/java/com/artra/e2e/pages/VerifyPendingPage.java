package com.artra.e2e.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;

/**
 * {@code /verify/pending} - the second factor chooser shown after valid
 * credentials.
 *
 * Two routes out of here: a code by email, or an approval pushed over the
 * websocket to an already-trusted device. Only the email route is automated -
 * the device route needs a second, already-signed-in browser session receiving
 * a websocket push, which is a genuinely different test to build and not one
 * that earns its keep next to the flow every real user takes.
 */
public class VerifyPendingPage extends BasePage<VerifyPendingPage> {

    private static final By CARD = By.cssSelector("form button[aria-label]");
    private static final By EMAIL_OPTION =
            By.cssSelector("button[aria-label='ელფოსტაზე მიღებული კოდით დადასტურება']");
    private static final By DEVICE_OPTION =
            By.cssSelector("button[aria-label='სხვა მოწყობილობით დადასტურება']");

    public VerifyPendingPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/verify/pending", EMAIL_OPTION);
    }

    /** Captions of the offered verification methods. */
    public List<String> offeredMethods() {
        return all(CARD).stream()
                .filter(Interactions::isVisible)
                .map(button -> Interactions.textOf(button.findElement(By.cssSelector("p"))))
                .toList();
    }

    public boolean offersDeviceApproval() {
        return isPresent(DEVICE_OPTION);
    }

    /**
     * Asks for a code by email. The button submits a form, so no hydration is
     * needed; the page it lands on is the one to wait for.
     */
    public VerifyEmailPage chooseEmailCode() {
        log.info("▶ Choosing email verification");
        Interactions.click(driver, visible(EMAIL_OPTION));
        return new VerifyEmailPage(driver, wait).waitUntilLoaded();
    }
}
