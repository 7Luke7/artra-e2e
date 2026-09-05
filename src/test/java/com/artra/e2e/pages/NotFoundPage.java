package com.artra.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;

/**
 * The catch-all 404 view.
 *
 * Worth a test because it is a route the app has to get right and nobody ever
 * looks at: a broken 404 usually shows up as a blank page or an unhandled
 * client error, both of which this catches.
 */
public class NotFoundPage extends BasePage<NotFoundPage> {

    private static final By HEADING =
            By.xpath("//h1[normalize-space()='ასეთი გვერდი არ არსებობს']");
    private static final By BADGE =
            By.xpath("//div[normalize-space()='გვერდი ვერ მოიძებნა']");

    private NotFoundPage(WebDriver driver, WebDriverWait wait, String route) {
        super(driver, wait, route, HEADING);
    }

    /** Opens a route that should not resolve. */
    public static NotFoundPage open(WebDriver driver, WebDriverWait wait, String route) {
        NotFoundPage page = new NotFoundPage(driver, wait, route);
        driver.get(page.url(route));
        return page;
    }

    public boolean isShown() {
        wait.until(d -> Interactions.isPresent(d, HEADING));
        return true;
    }

    public boolean showsBadge() {
        return isPresent(BADGE);
    }
}
