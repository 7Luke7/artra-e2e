package com.artra.e2e.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;

/**
 * {@code /account} - the signed-in profile page, and the suite's canonical
 * protected route.
 *
 * The layout guards itself: without a valid session the route redirects to
 * {@code /login} rather than rendering an empty shell, which is what the access
 * tests assert against.
 */
public class AccountPage extends BasePage<AccountPage> {

    private static final By PROFILE_HEADING =
            By.xpath("//h1[normalize-space()='ჩემი პროფილი']");
    private static final By DISPLAY_NAME = By.cssSelector("h2");
    private static final By SIDEBAR = By.cssSelector("nav[aria-label='ანგარიშის სექციები']");
    private static final By SECURITY_LINK =
            By.cssSelector("nav[aria-label='ანგარიშის სექციები'] a[href='/account/security']");

    public AccountPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/account", PROFILE_HEADING);
    }

    /** The account holder's name, as the profile card shows it. */
    public String displayName() {
        return textOf(DISPLAY_NAME);
    }

    /**
     * The value the profile shows under a given label, e.g. "სრული სახელი" or
     * "ელექტრონული ფოსტა".
     *
     * Addressed through the label rather than by position, because the fields
     * are visually identical blocks and a positional locator silently starts
     * reading the wrong one the moment a field is added.
     */
    public String fieldValue(String label) {
        return Interactions.textOf(visible(By.xpath(
                "//label[normalize-space()='" + label + "']/following-sibling::div//span")));
    }

    public String email() {
        return fieldValue("ელექტრონული ფოსტა");
    }

    public String joinedOn() {
        return fieldValue("შემოუერთდა");
    }

    public List<String> sectionCaptions() {
        return visible(SIDEBAR).findElements(By.tagName("a")).stream()
                .map(link -> Interactions.textOf(link.findElement(By.cssSelector("span"))))
                .filter(caption -> !caption.isEmpty())
                .toList();
    }

    public AccountSecurityPage openSecurity() {
        Interactions.click(driver, visible(SECURITY_LINK));
        return new AccountSecurityPage(driver, wait).waitUntilLoaded();
    }
}
