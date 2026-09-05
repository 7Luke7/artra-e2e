package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.CoursesPage;
import com.artra.e2e.support.ArtraDatabase;

/**
 * The catalogue: filtering, sorting and paging.
 *
 * This is the most regression-prone screen in the application. Every control
 * writes into the same query string, the list is fetched from it, and paging
 * is cursor-based - it carries the sort column and the last row's value
 * forward - so a change to any one control routinely breaks the other two.
 * That is exactly the interaction no unit test sees.
 */
@Tag("catalogue")
class CourseCatalogueIT {

    @CrossBrowserTest
    @DisplayName("Catalogue lists courses and reports a total that matches what it shows")
    void catalogueListsCourses(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();

        assertAll(
                () -> assertTrue(courses.cardCount() > 0, "The catalogue should list courses"),
                () -> assertFalse(courses.showsEmptyState(),
                        "An unfiltered catalogue should not show the empty state"),
                // The page shows at most one page worth of cards but reports the
                // full total; a mismatch here is how a broken COUNT(*) OVER()
                // surfaces to a user.
                () -> assertTrue(courses.reportedTotal() >= courses.cardCount(),
                        "The reported total (" + courses.reportedTotal()
                                + ") cannot be smaller than the number of cards shown ("
                                + courses.cardCount() + ")"));
    }

    /**
     * Regression test for a defect this suite found: the catalogue listing was
     * the only query in the application that did not filter on course status,
     * so unpublished drafts were counted in the total and appeared on the last
     * page - visible to anyone, including search engines.
     */
    @CrossBrowserTest
    @DisplayName("Unpublished courses are not listed in the public catalogue")
    void unpublishedCoursesStayHidden(WebDriver driver, WebDriverWait wait) {
        int published = ArtraDatabase.publishedCourseCount();
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();

        assertEquals(published, courses.reportedTotal(),
                "The catalogue should count only published courses. A larger total means "
                        + "drafts are being listed.");

        // Paging to the end is the part that matters: the drafts sorted to the
        // last page, so a check that only looked at page 1 saw nothing wrong.
        while (courses.hasPagination() && courses.currentPage() < courses.pageNumbers().size()) {
            courses.goToNextPage();
        }
        assertTrue(courses.courseTitles().stream().noneMatch(title -> title.startsWith("დრაფტი")),
                "No draft course should appear on the last page, but it showed: "
                        + courses.courseTitles());
    }

    /**
     * Regression test for a second defect found here: the discount filter was
     * appended to the SQL after the WHERE clause had been built, so with no
     * other filter active it attached itself to a JOIN condition instead and
     * quietly returned the entire catalogue.
     */
    @CrossBrowserTest
    @DisplayName("The discount filter returns only discounted courses")
    void discountFilterReturnsOnlyOffers(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();
        int unfiltered = courses.cardCount();

        courses.filters().offer("ფასდაკლება").apply("offer=sale");

        int shown = courses.cardCount();
        assertAll(
                () -> assertTrue(shown > 0, "The seeded catalogue includes discounted courses"),
                () -> assertTrue(shown < unfiltered,
                        "Filtering to offers should narrow the catalogue, but returned "
                                + shown + " of " + unfiltered + " cards"),
                () -> assertEquals(shown, courses.discountedCardCount(),
                        "Every card returned by the offer filter should carry a discount badge"));
    }

    @CrossBrowserTest
    @DisplayName("Category filter narrows the catalogue and is reflected in the URL")
    void categoryFilterNarrowsResults(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();
        int unfiltered = courses.reportedTotal();

        courses.filters().category("ტექნოლოგიები").apply("category=technology");

        assertAll(
                () -> assertTrue(driver.getCurrentUrl().contains("category=technology"),
                        "The chosen category should be carried in the URL"),
                () -> assertTrue(courses.cardCount() > 0,
                        "The technology category should not be empty in the seeded catalogue"),
                () -> assertTrue(courses.reportedTotal() < unfiltered,
                        "Filtering by a single category should return fewer courses than the "
                                + "whole catalogue (" + courses.reportedTotal() + " vs " + unfiltered + ")"));
    }

    @CrossBrowserTest
    @DisplayName("Level filter returns only courses of that level")
    void levelFilterReturnsOnlyThatLevel(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();

        courses.filters().level("დამწყები").apply("level=beginner");

        List<String> levels = courses.courseLevels();
        assertFalse(levels.isEmpty(), "The beginner level should return at least one course");
        assertTrue(levels.stream().allMatch("დამწყები"::equals),
                "Every card should be a beginner course, but the badges read: " + levels);
    }

    @CrossBrowserTest
    @DisplayName("Price range filter excludes courses outside the bounds")
    void priceFilterRespectsBounds(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();

        courses.filters().priceRange("50", "150").apply("priceFrom=50");

        List<Double> prices = courses.coursePrices();
        assertFalse(prices.isEmpty(), "The 50-150 range should match part of the seeded catalogue");
        assertTrue(prices.stream().allMatch(price -> price >= 50 && price <= 150),
                "Every price should fall inside the requested range, but got: " + prices);
    }

    @CrossBrowserTest
    @DisplayName("A filter combination that matches nothing shows the empty state, not an error")
    void impossibleFilterShowsEmptyState(WebDriver driver, WebDriverWait wait) {
        // Deliberately a range no seeded course can satisfy. The interesting
        // failure mode here is not "no results" but the page erroring or
        // silently keeping the previous results.
        CoursesPage courses = new CoursesPage(driver, wait).get()
                .openWithQuery("priceFrom=99000&priceTo=99999");

        assertAll(
                () -> assertTrue(courses.showsEmptyState(),
                        "An unsatisfiable filter should show the empty state"),
                () -> assertEquals(0, courses.cardCount(),
                        "No cards should be rendered alongside the empty state"));
    }

    @CrossBrowserTest
    @DisplayName("Clearing the filters restores the full catalogue")
    void clearingFiltersRestoresEverything(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();
        int unfiltered = courses.reportedTotal();

        courses.filters().category("მშენებლობა").apply("category=construction");
        assertTrue(courses.filters().canReset(),
                "The sidebar should offer to clear the filters once one is applied");

        courses.filters().reset();

        assertAll(
                () -> assertFalse(driver.getCurrentUrl().contains("category="),
                        "Clearing should drop the category from the URL"),
                () -> assertEquals(unfiltered, courses.reportedTotal(),
                        "Clearing the filters should bring the whole catalogue back"));
    }

    @CrossBrowserTest
    @DisplayName("Sorting by price ascending orders the cards by price")
    void sortingByPriceOrdersResults(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get().waitForResults();

        courses.sortBy("price-ASC");

        List<Double> prices = courses.coursePrices();
        assertTrue(isSorted(prices, Comparator.naturalOrder()),
                "Prices should ascend across the page, but were: " + prices);

        courses.sortBy("price-DESC");

        List<Double> descending = courses.coursePrices();
        assertTrue(isSorted(descending, Comparator.reverseOrder()),
                "Prices should descend across the page, but were: " + descending);
    }

    @CrossBrowserTest
    @DisplayName("Paging forward shows a different page of results and keeps the sort")
    void pagingForwardKeepsTheSort(WebDriver driver, WebDriverWait wait) {
        CoursesPage courses = new CoursesPage(driver, wait).get()
                .waitForResults()
                .sortBy("price-ASC");

        assertTrue(courses.hasPagination(),
                "The seeded catalogue is larger than one page, so pagination should render");
        assertEquals(1, courses.currentPage(), "The catalogue should open on page 1");

        List<String> firstPage = courses.courseTitles();
        List<Double> firstPagePrices = courses.coursePrices();

        courses.goToNextPage();

        List<String> secondPage = courses.courseTitles();
        assertAll(
                () -> assertEquals(2, courses.currentPage(), "Paging forward should land on page 2"),
                () -> assertTrue(secondPage.stream().noneMatch(firstPage::contains),
                        "Page 2 should not repeat any course from page 1. Page 1: " + firstPage
                                + " Page 2: " + secondPage),
                () -> assertEquals("price-ASC", courses.selectedSort(),
                        "Paging should carry the chosen sort forward"),
                // Cursor paging is the part that actually breaks: the cursor is
                // built from the last row of the previous page, so an off-by-one
                // shows up as page 2 starting below where page 1 ended.
                () -> assertTrue(courses.coursePrices().get(0)
                                >= firstPagePrices.get(firstPagePrices.size() - 1),
                        "Page 2 should continue the ascending price order from page 1, but "
                                + "started at " + courses.coursePrices().get(0) + " after page 1 ended at "
                                + firstPagePrices.get(firstPagePrices.size() - 1)));
    }

    private static <T> boolean isSorted(List<T> values, Comparator<T> order) {
        for (int i = 1; i < values.size(); i++) {
            if (order.compare(values.get(i - 1), values.get(i)) > 0) {
                return false;
            }
        }
        return true;
    }
}
