package com.artra.e2e.base;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The small set of interaction primitives every page object here is built on.
 *
 * <h2>Why clicks are retried against an outcome</h2>
 *
 * Artra is server-rendered and then hydrated. Between those two moments the
 * markup is complete and visible but Solid has not attached its handlers yet,
 * so a click on a plain button - a filter, a sort option, the account menu -
 * lands on a real element, reports success, and does nothing at all. Waiting
 * for "the element is clickable" cannot see that: the element was always
 * clickable, it just was not wired up.
 *
 * {@link #clickUntil} therefore treats a click as done only when its
 * <em>effect</em> is observable, and re-clicks until it is. Every use passes a
 * condition that is idempotent (a URL, a rendered panel, a visible dialog), so
 * a second click that lands after the first one worked is harmless.
 *
 * Forms are the exception and are submitted normally: SolidStart progressively
 * enhances them, so a form posts natively before hydration and through the
 * action afterwards. Both paths reach the same handler.
 */
public final class Interactions {

    private static final Logger log = LoggerFactory.getLogger(Interactions.class);

    /** How long one click gets to produce its effect before being repeated. */
    private static final Duration EFFECT = Duration.ofSeconds(5);

    /** Repeats beyond this are not a hydration race any more, they are a bug. */
    private static final int MAX_CLICKS = 4;

    private Interactions() {
    }

    /**
     * Clicks until {@code effect} holds.
     *
     * @param target supplier rather than an element: the DOM is re-rendered
     *               between attempts, so a WebElement captured before the first
     *               click is stale by the second.
     */
    public static void clickUntil(WebDriver driver, WebDriverWait wait,
                                  Supplier<WebElement> target,
                                  ExpectedCondition<?> effect,
                                  String description) {
        for (int attempt = 1; attempt <= MAX_CLICKS; attempt++) {
            click(driver, target.get());
            try {
                new WebDriverWait(driver, EFFECT).until(effect);
                return;
            } catch (TimeoutException e) {
                if (attempt == MAX_CLICKS) {
                    throw new TimeoutException(
                            "'" + description + "' did not take effect after " + MAX_CLICKS
                                    + " clicks. Current URL: " + driver.getCurrentUrl(), e);
                }
                log.debug("■ '{}' had no effect yet - clicking again ({}/{})",
                        description, attempt, MAX_CLICKS);
            }
        }
    }

    /**
     * A single click that copes with the two things that routinely intercept
     * one on this site: a sticky header overlapping the target after a scroll,
     * and an element replaced mid-click by a re-render.
     */
    public static void click(WebDriver driver, WebElement element) {
        try {
            scrollIntoView(driver, element);
            element.click();
        } catch (ElementNotInteractableException | StaleElementReferenceException e) {
            // ElementClickInterceptedException is a subclass of
            // ElementNotInteractableException, so it is caught here too.
            log.debug("■ Direct click failed ({}), falling back to a scripted click",
                    e.getClass().getSimpleName());
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    /**
     * Centres the element. {@code scrollIntoView(true)} would align it to the
     * top of the viewport, straight underneath Artra's sticky header, where the
     * header eats the click.
     */
    public static void scrollIntoView(WebDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", element);
    }

    /** Replaces a field's contents. clear() alone leaves Solid's signal holding
     *  the old value on inputs that only listen to input events. */
    public static void setValue(WebDriver driver, WebElement field, String value) {
        scrollIntoView(driver, field);
        field.click();
        field.clear();
        if (!value.isEmpty()) {
            field.sendKeys(value);
        }
    }

    /**
     * Text as the DOM holds it, not as the browser paints it.
     *
     * getText() returns only rendered text, so anything clipped, collapsed or
     * scrolled out of view comes back empty - and an empty string reads as "the
     * assertion found the wrong element" rather than "the element was off
     * screen". Non-breaking spaces are folded so Georgian copy compares
     * predictably.
     */
    public static String textOf(WebElement element) {
        return String.valueOf(element.getDomProperty("textContent"))
                .replace("\u00A0", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static boolean isVisible(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (StaleElementReferenceException | org.openqa.selenium.NoSuchElementException e) {
            return false;
        }
    }

    /** Present in the DOM and on screen, checked without waiting. */
    public static boolean isPresent(WebDriver driver, By locator) {
        List<WebElement> found = driver.findElements(locator);
        return !found.isEmpty() && isVisible(found.get(0));
    }
}
