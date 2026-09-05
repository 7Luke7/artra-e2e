package com.artra.e2e.components;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.artra.e2e.base.Interactions;

/**
 * The site header, which is also the suite's cheapest authentication check.
 *
 * The header is rendered from the session cookie, so what it shows is the
 * app's own answer to "is this browser signed in": an anonymous visitor gets
 * the შესვლა / რეგისტრაცია pair, a signed-in one gets the avatar button and its
 * menu. Tests assert against {@link #isSignedIn()} rather than poking at
 * cookies, because the cookie being present is not the same claim as the app
 * accepting it.
 *
 * <h2>Locators</h2>
 *
 * Everything is addressed by role, aria-label or href - all of them chosen by
 * the application for accessibility, none of them a Tailwind class. Artra's
 * markup is styled entirely with utility classes, which change whenever the
 * design does; the accessible names do not.
 */
public class Header {

    private static final Logger log = LoggerFactory.getLogger(Header.class);

    private static final By BANNER = By.cssSelector("header[role='banner']");
    private static final By LOGO = By.cssSelector("header a[aria-label='Artra - მთავარი გვერდი']");
    private static final By ANONYMOUS_NAV = By.cssSelector("nav[aria-label='მთავარი ნავიგაცია']");
    private static final By SIGN_IN = By.cssSelector("nav[aria-label='მთავარი ნავიგაცია'] a[href='/login']");
    private static final By REGISTER = By.cssSelector("nav[aria-label='მთავარი ნავიგაცია'] a[href='/register']");
    private static final By COURSES_LINK = By.cssSelector("header a[href='/courses']");

    private static final By ACCOUNT_BUTTON = By.cssSelector("button[aria-label='ანგარიშის პარამეტრები']");
    private static final By ACCOUNT_MENU = By.id("account-options");
    private static final By ACCOUNT_LINK = By.cssSelector("#account-options a[href='/account']");
    private static final By SIGN_OUT = By.cssSelector("#account-options form button[type='submit']");

    /**
     * The header's signed-in half is code-split and rendered from an async
     * session lookup, so on a cold page load neither variant exists for a beat.
     * Long enough to cover that, short enough that asserting "signed out" does
     * not cost the full explicit wait.
     */
    private static final Duration RESOLVE = Duration.ofSeconds(15);

    private final WebDriver driver;
    private final WebDriverWait wait;

    public Header(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /** Waits until the header has decided which variant to show. */
    public Header waitUntilResolved() {
        wait.until(ExpectedConditions.presenceOfElementLocated(BANNER));
        try {
            new WebDriverWait(driver, RESOLVE).until(
                    d -> Interactions.isPresent(d, ACCOUNT_BUTTON)
                            || Interactions.isPresent(d, ANONYMOUS_NAV));
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "The header showed neither the signed-in avatar nor the anonymous "
                            + "navigation within " + RESOLVE.toSeconds() + "s at "
                            + driver.getCurrentUrl(), e);
        }
        return this;
    }

    public boolean isSignedIn() {
        waitUntilResolved();
        return Interactions.isPresent(driver, ACCOUNT_BUTTON);
    }

    public boolean isAnonymous() {
        return !isSignedIn();
    }

    public boolean hasSignInCallToAction() {
        waitUntilResolved();
        return Interactions.isPresent(driver, SIGN_IN) && Interactions.isPresent(driver, REGISTER);
    }

    /** Captions of the header's visible links, which is what a navigation test
     *  compares against rather than counting anonymous {@code <a>} tags. */
    public List<String> linkCaptions() {
        waitUntilResolved();
        return driver.findElement(BANNER).findElements(By.tagName("a")).stream()
                .filter(Interactions::isVisible)
                .map(Interactions::textOf)
                .filter(caption -> !caption.isEmpty())
                .toList();
    }

    public void goToCourses() {
        Interactions.click(driver, wait.until(ExpectedConditions.elementToBeClickable(COURSES_LINK)));
    }

    public void goToSignIn() {
        Interactions.click(driver, wait.until(ExpectedConditions.elementToBeClickable(SIGN_IN)));
    }

    public void goToRegister() {
        Interactions.click(driver, wait.until(ExpectedConditions.elementToBeClickable(REGISTER)));
    }

    public void goHome() {
        Interactions.click(driver, wait.until(ExpectedConditions.elementToBeClickable(LOGO)));
    }

    /**
     * Opens the avatar dropdown.
     *
     * The menu is a plain button with a Solid click handler, so it is one of
     * the controls that silently does nothing until the page has hydrated -
     * hence clickUntil against the menu actually being on screen.
     */
    public Header openAccountMenu() {
        log.info("▶ Opening the account menu");
        Interactions.clickUntil(driver, wait,
                () -> wait.until(ExpectedConditions.elementToBeClickable(ACCOUNT_BUTTON)),
                ExpectedConditions.visibilityOfElementLocated(ACCOUNT_MENU),
                "open the account menu");
        return this;
    }

    public List<String> accountMenuCaptions() {
        return driver.findElement(ACCOUNT_MENU).findElements(By.cssSelector("a, button")).stream()
                .filter(Interactions::isVisible)
                .map(Interactions::textOf)
                .filter(caption -> !caption.isEmpty())
                .toList();
    }

    public void goToAccount() {
        openAccountMenu();
        Interactions.click(driver, wait.until(ExpectedConditions.elementToBeClickable(ACCOUNT_LINK)));
    }

    /**
     * Signs out and waits for the app to land on the sign-in page.
     *
     * The wait is on the URL, not on the header coming back anonymous: signing
     * out redirects to /login, and that page renders no header at all - so
     * waiting for the anonymous navigation would time out on a sign-out that
     * worked perfectly.
     */
    public void signOut() {
        log.info("▶ Signing out");
        openAccountMenu();
        WebElement button = wait.until(ExpectedConditions.elementToBeClickable(SIGN_OUT));
        Interactions.click(driver, button);
        wait.until(ExpectedConditions.urlContains("/login"));
    }
}
