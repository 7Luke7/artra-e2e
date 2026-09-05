package com.artra.e2e.pages;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;
import com.artra.e2e.components.Header;

/**
 * The public landing page at {@code /}.
 *
 * Artra renders two different pages at this route - a marketing page for
 * anonymous visitors and a dashboard for signed-in ones - chosen by the session
 * cookie. This object models the anonymous one, and {@link #isDashboard()}
 * exists so a test that expected to be signed out fails saying which page it
 * got rather than "element not found".
 */
public class LandingPage extends BasePage<LandingPage> {

    private static final By FEATURED_HEADING = By.id("featured-courses-heading");
    private static final By FEATURED_LIST =
            By.cssSelector("ul[aria-label='რეკომენდებული კურსების ჩამონათვალი']");
    private static final By COURSE_CARDS = By.cssSelector(
            "ul[aria-label='რეკომენდებული კურსების ჩამონათვალი'] article[itemtype='https://schema.org/Course']");
    private static final By HERO_HEADING = By.id("main-hero-heading");
    private static final By BROWSE_CATALOGUE =
            By.cssSelector("a[aria-label='კურსების სრული კატალოგის ნახვა']");

    /**
     * The two variants are told apart by a section only one of them renders.
     *
     * The document title also differs and was the first thing tried, but it is
     * set during hydration - after the markup is on screen - so reading it
     * immediately after the navigation occasionally returned the *previous*
     * page's title and reported the wrong variant. Roughly one invocation in a
     * hundred, and only on Firefox. A rendered element does not have that
     * problem, and does not tie the assertion to marketing copy either.
     */
    private static final By DASHBOARD_MARKER =
            By.xpath("//h2[normalize-space()='ჩემი კურსები']");

    private final Header header;

    public LandingPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/", By.cssSelector("header[role='banner']"));
        this.header = new Header(driver, wait);
    }

    public Header header() {
        return header;
    }

    /**
     * True when the route resolved to the signed-in dashboard rather than the
     * marketing page.
     *
     * Waits for whichever variant is coming before answering: both are code
     * split and neither is on screen for the first moment after the
     * navigation, so an immediate look sees neither and would report
     * "not the dashboard" for a page that is about to be exactly that.
     */
    public boolean isDashboard() {
        wait.until(d -> Interactions.isPresent(d, DASHBOARD_MARKER)
                || Interactions.isPresent(d, HERO_HEADING));
        return isPresent(DASHBOARD_MARKER);
    }

    /** Greeting on the signed-in dashboard, e.g. "გამარჯობა, ნინო 👋". */
    public String greeting() {
        wait.until(d -> Interactions.isPresent(d, DASHBOARD_MARKER));
        return heading();
    }

    public String heroHeading() {
        return textOf(HERO_HEADING);
    }

    public boolean showsFeaturedCourses() {
        return isPresent(FEATURED_HEADING) && isPresent(FEATURED_LIST);
    }

    public int featuredCourseCount() {
        wait.until(d -> !d.findElements(COURSE_CARDS).isEmpty());
        return all(COURSE_CARDS).size();
    }

    /** Titles of the featured cards, in the order the page renders them. */
    public List<String> featuredCourseTitles() {
        wait.until(d -> !d.findElements(COURSE_CARDS).isEmpty());
        return all(COURSE_CARDS).stream()
                .map(card -> card.findElement(By.cssSelector("h2 a")))
                .map(Interactions::textOf)
                .toList();
    }

    /** Course-detail hrefs the featured cards link to. */
    public List<String> featuredCourseLinks() {
        return all(COURSE_CARDS).stream()
                .map(card -> card.findElement(By.cssSelector("h2 a")).getDomAttribute("href"))
                .toList();
    }

    public CoursesPage browseCatalogue() {
        Interactions.click(driver, visible(BROWSE_CATALOGUE));
        return new CoursesPage(driver, wait).waitUntilLoaded();
    }
}
