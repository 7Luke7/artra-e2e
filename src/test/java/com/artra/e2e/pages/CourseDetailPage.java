package com.artra.e2e.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;
import com.artra.e2e.components.Header;

/**
 * {@code /course/<slug>}.
 *
 * The route renders one of three things depending on the visitor: the sales
 * page for a course they have not bought, the player for one they have, and a
 * "not found" panel for a slug that does not resolve. The suite covers the
 * first and the third; the player needs an enrolment, which can only be created
 * by completing a payment against an external gateway, so it is out of scope
 * here rather than faked.
 */
public class CourseDetailPage extends BasePage<CourseDetailPage> {

    private static final By TITLE = By.cssSelector("h1[itemprop='name']");
    private static final By DESCRIPTION = By.cssSelector("p[itemprop='description']");
    private static final By BREADCRUMB = By.cssSelector("nav[aria-label='breadcrumb']");
    private static final By PURCHASE = By.cssSelector("button[aria-label^='შეიძინეთ კურსი']");
    private static final By INSTRUCTOR_HEADING = By.id("instructor-heading");
    private static final By CURRICULUM_SECTION = By.cssSelector("button[aria-controls^='section-']");
    private static final By NOT_FOUND =
            By.xpath("//h1[normalize-space()='კურსი ვერ მოიძებნა']");

    private final Header header;

    public CourseDetailPage(WebDriver driver, WebDriverWait wait, String slug) {
        super(driver, wait, "/course/" + slug, TITLE);
        this.header = new Header(driver, wait);
    }

    public Header header() {
        return header;
    }

    public String courseTitle() {
        return textOf(TITLE);
    }

    public String description() {
        return textOf(DESCRIPTION);
    }

    /** Breadcrumb captions, e.g. [კურსები, ტექნოლოგიები, <course title>]. */
    public List<String> breadcrumb() {
        return visible(BREADCRUMB).findElements(By.cssSelector("a, span")).stream()
                .map(Interactions::textOf)
                .filter(text -> !text.isEmpty() && !"/".equals(text))
                .toList();
    }

    public boolean showsPurchaseCallToAction() {
        return isPresent(PURCHASE);
    }

    /** The purchase button's accessible name, which the app builds from the
     *  course title and price - so it doubles as a check that the sidebar and
     *  the heading describe the same course. */
    public String purchaseLabel() {
        return visible(PURCHASE).getDomAttribute("aria-label");
    }

    public boolean showsInstructorSection() {
        return isPresent(INSTRUCTOR_HEADING);
    }

    /** Curriculum sections, which the page renders as collapsible blocks. */
    public int curriculumSectionCount() {
        return all(CURRICULUM_SECTION).size();
    }

    /** Expands a curriculum section and reports whether it opened. */
    public boolean expandCurriculumSection(int index) {
        var toggle = all(CURRICULUM_SECTION).get(index);
        String controls = toggle.getDomAttribute("aria-controls");
        Interactions.clickUntil(driver, wait,
                () -> all(CURRICULUM_SECTION).get(index),
                d -> "true".equals(all(CURRICULUM_SECTION).get(index).getDomAttribute("aria-expanded")),
                "expand curriculum section " + index);
        return isPresent(By.id(controls));
    }

    // ------------------------------------------------------------ not found --

    /**
     * True when the slug did not resolve.
     *
     * Waits for one of the two possible outcomes rather than checking once:
     * the detail data is fetched during server rendering but the fallback is
     * chosen on the client, so an immediate check can catch neither.
     */
    public boolean isNotFound() {
        wait.until(d -> Interactions.isPresent(d, NOT_FOUND) || Interactions.isPresent(d, TITLE));
        return isPresent(NOT_FOUND);
    }

    /** Opens a slug without asserting it exists - for the not-found case. */
    public static CourseDetailPage openRaw(WebDriver driver, WebDriverWait wait, String slug) {
        CourseDetailPage page = new CourseDetailPage(driver, wait, slug);
        driver.get(page.url("/course/" + slug));
        return page;
    }
}
