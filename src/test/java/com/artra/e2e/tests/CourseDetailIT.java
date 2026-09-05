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
import com.artra.e2e.pages.CourseDetailPage;
import com.artra.e2e.pages.CoursesPage;

/**
 * The course detail page - the last screen before a purchase, so the one where
 * wrong or missing information costs the most.
 */
@Tag("catalogue")
class CourseDetailIT {

    @CrossBrowserTest
    @DisplayName("Opening a course from the catalogue shows that course's detail page")
    void catalogueOpensTheRightCourse(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();
        String expectedTitle = courses.courseTitles().get(0);

        CourseDetailPage detail = courses.openFirstCourse();

        assertEquals(expectedTitle, detail.courseTitle(),
                "The detail page should be for the card that was clicked");
    }

    @CrossBrowserTest
    @DisplayName("Course detail page shows the information a buyer needs")
    void detailPageShowsBuyingInformation(WebDriver driver, WebDriverWait wait) {
        CourseDetailPage detail = new CoursesPage(driver, wait).get()
                .waitForResults()
                .openFirstCourse();

        assertAll(
                () -> assertFalse(detail.description().isBlank(),
                        "The course description should be rendered"),
                () -> assertTrue(detail.showsInstructorSection(),
                        "The instructor section should be rendered"),
                () -> assertTrue(detail.curriculumSectionCount() > 0,
                        "The curriculum should list at least one section"),
                () -> assertTrue(detail.showsPurchaseCallToAction(),
                        "A visitor who is not enrolled should be offered the purchase button"),
                // The button's accessible name is built from the title and price
                // separately from the heading, so agreement between them is a
                // real check rather than a tautology.
                () -> assertTrue(detail.purchaseLabel().contains(detail.courseTitle()),
                        "The purchase button should name the course it buys, but reads: "
                                + detail.purchaseLabel()));
    }

    @CrossBrowserTest
    @DisplayName("Breadcrumb places the course under its category")
    void breadcrumbPlacesTheCourse(WebDriver driver, WebDriverWait wait) {
        CourseDetailPage detail = new CoursesPage(driver, wait).get()
                .waitForResults()
                .openFirstCourse();

        List<String> crumbs = detail.breadcrumb();

        assertAll(
                () -> assertEquals("კურსები", crumbs.get(0),
                        "The breadcrumb should start at the catalogue"),
                () -> assertEquals(detail.courseTitle(), crumbs.get(crumbs.size() - 1),
                        "The breadcrumb should end at the course itself"),
                () -> assertEquals(3, crumbs.size(),
                        "The breadcrumb should be catalogue / category / course, but was: " + crumbs));
    }

    @CrossBrowserTest
    @DisplayName("Curriculum sections expand to reveal their lessons")
    void curriculumSectionsExpand(WebDriver driver, WebDriverWait wait) {
        CourseDetailPage detail = new CoursesPage(driver, wait).get()
                .waitForResults()
                .openFirstCourse();

        assertTrue(detail.expandCurriculumSection(0),
                "Expanding the first curriculum section should reveal its lessons");
    }

    @CrossBrowserTest
    @DisplayName("An unknown course slug shows the not-found panel, not an error page")
    void unknownSlugShowsNotFound(WebDriver driver, WebDriverWait wait) {
        CourseDetailPage detail =
                CourseDetailPage.openRaw(driver, wait, "no-such-course-" + System.nanoTime());

        assertAll(
                () -> assertTrue(detail.isNotFound(),
                        "An unknown slug should render the course not-found panel"),
                () -> assertEquals("Artra - კურსი ვერ მოიძებნა", detail.title(),
                        "The document title should say the course was not found"));
    }
}
