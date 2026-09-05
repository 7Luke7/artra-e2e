package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.CoursesPage;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.NotFoundPage;
import com.artra.e2e.support.ArtraDatabase;

/**
 * The public front door.
 *
 * These are the checks that decide whether anything else is worth running: if
 * the landing page does not render its courses, every catalogue and course test
 * downstream will fail for the same reason, and it is much cheaper to read that
 * from one failing smoke test than from thirty.
 */
@Tag("smoke")
class LandingIT {

    @CrossBrowserTest
    @DisplayName("Landing page renders the marketing view for an anonymous visitor")
    void landingRendersForAnonymousVisitor(WebDriver driver, WebDriverWait wait) {
        LandingPage landing = new LandingPage(driver, wait).get();

        assertAll(
                () -> assertFalse(landing.isDashboard(),
                        "A visitor with no session should get the marketing landing page, "
                                + "not the signed-in dashboard"),
                () -> assertTrue(landing.heroHeading().contains("ისწავლე"),
                        "The hero heading should introduce the platform, but was: "
                                + landing.heroHeading()),
                () -> assertTrue(landing.header().hasSignInCallToAction(),
                        "The header should offer sign-in and registration to an anonymous visitor"));
    }

    @CrossBrowserTest
    @DisplayName("Landing page features published courses that link to their detail pages")
    void landingFeaturesPublishedCourses(WebDriver driver, WebDriverWait wait) {
        LandingPage landing = new LandingPage(driver, wait).get();

        assertTrue(landing.showsFeaturedCourses(), "The featured-courses section should render");

        List<String> titles = landing.featuredCourseTitles();
        assertFalse(titles.isEmpty(), "At least one published course should be featured");

        // The section is capped at six by the query behind it; asserting the cap
        // catches a change that starts dumping the whole catalogue onto the
        // landing page.
        assertTrue(titles.size() <= 6,
                "The landing page should feature at most six courses but showed " + titles.size());

        assertTrue(landing.featuredCourseLinks().stream().allMatch(href -> href.startsWith("/course/")),
                "Every featured card should link to a course detail page, but got: "
                        + landing.featuredCourseLinks());
    }

    @CrossBrowserTest
    @DisplayName("Newest published course is the one the landing page leads with")
    void landingLeadsWithTheNewestCourse(WebDriver driver, WebDriverWait wait) {
        // The section is ordered by created_at DESC, which is a promise the UI
        // makes and only the database can confirm.
        String newest = ArtraDatabase.newestPublishedCourseSlug()
                .orElseThrow(() -> new AssertionError(
                        "No published course in the database - the seed data did not load"));

        LandingPage landing = new LandingPage(driver, wait).get();

        assertEquals("/course/" + newest, landing.featuredCourseLinks().get(0),
                "The first featured card should be the most recently published course");
    }

    @CrossBrowserTest
    @DisplayName("Header navigates from the landing page to the catalogue")
    void headerNavigatesToCatalogue(WebDriver driver, WebDriverWait wait) {
        LandingPage landing = new LandingPage(driver, wait).get();

        landing.header().goToCourses();

        CoursesPage courses = new CoursesPage(driver, wait).waitUntilLoaded().waitForResults();
        assertTrue(courses.cardCount() > 0, "The catalogue should list courses");
    }

    @CrossBrowserTest
    @DisplayName("Unknown route renders the 404 page rather than a blank screen")
    void unknownRouteRendersNotFound(WebDriver driver, WebDriverWait wait) {
        NotFoundPage notFound = NotFoundPage.open(driver, wait, "/no-such-page-" + System.nanoTime());

        assertAll(
                () -> assertTrue(notFound.isShown(), "The 404 view should render"),
                () -> assertTrue(notFound.showsBadge(), "The 404 view should name what went wrong"),
                () -> assertTrue(notFound.title().startsWith("404"),
                        "The document title should mark the page as a 404, but was: "
                                + notFound.title()));
    }
}
