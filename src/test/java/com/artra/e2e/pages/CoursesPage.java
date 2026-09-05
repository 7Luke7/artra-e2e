package com.artra.e2e.pages;

import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.BasePage;
import com.artra.e2e.base.Interactions;
import com.artra.e2e.components.Header;

/**
 * {@code /courses} - the catalogue, with its filter sidebar, sort control and
 * cursor-based pagination.
 *
 * <h2>Why every action asserts on the URL</h2>
 *
 * Filtering, sorting and paging are all implemented as navigations: the
 * controls only rewrite the query string, and the list re-fetches from it. The
 * URL is therefore the app's own record of what was asked for, which makes it
 * both the right synchronisation point and the right thing to assert - a card
 * count alone cannot distinguish "the filter applied" from "the filter did
 * nothing and this category happens to hold everything".
 *
 * The list is fetched client-side, so after each navigation the previous
 * results stay on screen for a moment. Waiting on the URL and then on the
 * rendered count is what keeps a test from reading the old page.
 */
public class CoursesPage extends BasePage<CoursesPage> {

    private static final By HEADING = By.xpath("//h1[normalize-space()='კურსები']");
    private static final By LIST = By.cssSelector("ul[aria-label='კურსები']");
    private static final By CARDS =
            By.cssSelector("ul[aria-label='კურსები'] article[itemtype='https://schema.org/Course']");
    private static final By EMPTY_STATE = By.xpath("//p[normalize-space()='კურსები ვერ მოიძებნა']");
    /** The "N კურსი" summary, addressed structurally as the paragraph that
     *  follows the page heading - several other paragraphs contain the word. */
    private static final By TOTAL_COUNT =
            By.xpath("//h1[normalize-space()='კურსები']/following-sibling::p[1]");
    private static final By SORT = By.cssSelector("select");
    private static final By PAGINATION = By.cssSelector("nav[aria-label='გვერდები']");
    private static final By CURRENT_PAGE = By.cssSelector("nav[aria-label='გვერდები'] a[aria-current='page']");

    /** How long the catalogue gets to swap in a new result set. */
    private static final Duration RESULTS = Duration.ofSeconds(20);

    private final Header header;
    private final Filters filters;

    public CoursesPage(WebDriver driver, WebDriverWait wait) {
        super(driver, wait, "/courses", HEADING);
        this.header = new Header(driver, wait);
        this.filters = new Filters();
    }

    public Header header() {
        return header;
    }

    public Filters filters() {
        return filters;
    }

    /** Opens the catalogue with a query string already applied, which is how a
     *  test sets up a scenario without clicking through the sidebar. */
    public CoursesPage openWithQuery(String query) {
        driver.get(url("/courses" + (query.startsWith("?") ? query : "?" + query)));
        return waitUntilLoaded().waitForResults();
    }

    /**
     * Waits until the page is showing a result set - cards or the empty state.
     *
     * Both are valid outcomes, and treating "no cards" as the only signal would
     * make a legitimately empty filter wait out the full timeout on every run.
     */
    public CoursesPage waitForResults() {
        new WebDriverWait(driver, RESULTS)
                .until(d -> !d.findElements(CARDS).isEmpty() || Interactions.isPresent(d, EMPTY_STATE));
        return this;
    }

    public int cardCount() {
        return all(CARDS).size();
    }

    public boolean showsEmptyState() {
        return isPresent(EMPTY_STATE);
    }

    /** The "N კურსი" total the page reports, or -1 when it shows none. */
    public int reportedTotal() {
        if (!isPresent(TOTAL_COUNT)) {
            return -1;
        }
        String text = Interactions.textOf(all(TOTAL_COUNT).get(0));
        String digits = text.replaceAll("\\D+", " ").trim().split(" ")[0];
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    public List<String> courseTitles() {
        return all(CARDS).stream()
                .map(card -> card.findElement(By.cssSelector("h2 a")))
                .map(Interactions::textOf)
                .toList();
    }

    /** Prices as numbers, so a sort assertion can compare them properly rather
     *  than lexicographically ("₾100" &lt; "₾99" as text). */
    public List<Double> coursePrices() {
        return all(CARDS).stream()
                .map(card -> card.findElement(By.cssSelector("[aria-label^='ახლანდელი ფასი']")))
                .map(element -> element.getDomAttribute("aria-label"))
                .map(label -> label.replaceAll("[^0-9.]", ""))
                .map(Double::parseDouble)
                .toList();
    }

    /**
     * Level badges shown on the cards ("დამწყები", "საშუალო", "მაღალი").
     *
     * A card carries several pill-shaped badges - category, level, discount -
     * so they are told apart by the level badge's own colour class rather than
     * by position, which changes as soon as a course has no category.
     */
    public List<String> courseLevels() {
        return all(CARDS).stream()
                .map(card -> card.findElements(By.cssSelector("span.bg-green-100")))
                .map(badges -> badges.isEmpty() ? "" : Interactions.textOf(badges.get(0)))
                .toList();
    }

    /**
     * How many of the cards on screen carry a discount badge.
     *
     * The badge is rendered only when the API returned an original_price above
     * the current one, so this is the UI's own view of "is this course on
     * offer" - which is what the offer filter is supposed to select on.
     */
    public int discountedCardCount() {
        return (int) all(CARDS).stream()
                .filter(card -> !card.findElements(
                        By.cssSelector("[aria-label$='პროცენტიანი ფასდაკლება']")).isEmpty())
                .count();
    }

    public CourseDetailPage openFirstCourse() {
        WebElement link = all(CARDS).get(0).findElement(By.cssSelector("h2 a"));
        String slug = link.getDomAttribute("href").replace("/course/", "");
        Interactions.click(driver, link);
        return new CourseDetailPage(driver, wait, slug).waitUntilLoaded();
    }

    // ---------------------------------------------------------------- sort --

    public CoursesPage sortBy(String optionValue) {
        log.info("▶ Sorting by {}", optionValue);
        new Select(visible(SORT)).selectByValue(optionValue);
        wait.until(ExpectedConditions.urlContains("sort=" + optionValue));
        return waitForResults();
    }

    public String selectedSort() {
        return new Select(visible(SORT)).getFirstSelectedOption().getDomAttribute("value");
    }

    // ---------------------------------------------------------- pagination --

    public boolean hasPagination() {
        return isPresent(PAGINATION);
    }

    public int currentPage() {
        return Integer.parseInt(textOf(CURRENT_PAGE));
    }

    public List<String> pageNumbers() {
        return driver.findElement(PAGINATION).findElements(By.tagName("a")).stream()
                .map(Interactions::textOf)
                .filter(text -> text.matches("\\d+"))
                .toList();
    }

    /**
     * Follows the "next page" arrow.
     *
     * The links are plain anchors carrying a cursor built from the last course
     * on the current page, so this is a real navigation, not a click handler -
     * no hydration wait needed, but the result set still has to be re-fetched.
     */
    public CoursesPage goToNextPage() {
        List<WebElement> arrows = driver.findElement(PAGINATION).findElements(By.tagName("a"));
        WebElement next = arrows.get(arrows.size() - 1);
        if ("true".equals(next.getDomAttribute("aria-disabled"))) {
            throw new IllegalStateException("Already on the last page (page " + currentPage() + ")");
        }
        List<String> before = courseTitles();
        Interactions.click(driver, next);
        new WebDriverWait(driver, RESULTS).until(d -> !courseTitles().equals(before));
        return waitForResults();
    }

    /** The filter sidebar. */
    public class Filters {

        private static final By APPLY = By.xpath("//button[normalize-space()='გაფილტვრა']");
        private static final By RESET = By.xpath("//button[normalize-space()='გასუფთავება']");

        /**
         * A group's option button, located through the group's own label.
         *
         * Three groups each offer a button captioned "ყველა", so an unscoped
         * caption lookup picks whichever comes first in the DOM and silently
         * clears the wrong filter.
         */
        private WebElement option(String group, String caption) {
            return visible(By.xpath(
                    "//label[normalize-space()='" + group + "']/following-sibling::div"
                            + "//button[normalize-space()='" + caption + "']"));
        }

        /** Selecting an option only stages it - the sidebar applies nothing
         *  until გაფილტვრა is pressed - so the observable effect is the button
         *  becoming the highlighted one. */
        private void select(String group, String caption) {
            Interactions.clickUntil(driver, wait,
                    () -> option(group, caption),
                    d -> isSelected(option(group, caption)),
                    "select " + group + " = " + caption);
        }

        private boolean isSelected(WebElement button) {
            String classes = String.valueOf(button.getDomAttribute("class"));
            return classes.contains("text-[#E85A4F]");
        }

        public Filters category(String caption) {
            select("კატეგორია", caption);
            return this;
        }

        public Filters level(String caption) {
            select("დონე", caption);
            return this;
        }

        public Filters offer(String caption) {
            select("შეთავაზებები", caption);
            return this;
        }

        public Filters priceRange(String from, String to) {
            List<WebElement> inputs = visible(By.xpath(
                    "//label[starts-with(normalize-space(), 'ფასი')]/following-sibling::div"))
                    .findElements(By.cssSelector("input[type='number']"));
            Interactions.setValue(driver, inputs.get(0), from);
            Interactions.setValue(driver, inputs.get(1), to);
            return this;
        }

        /** Applies the staged filters and waits for the query string the app
         *  builds from them. */
        public CoursesPage apply(String expectedQueryFragment) {
            log.info("▶ Applying filters, expecting '{}' in the URL", expectedQueryFragment);
            Interactions.clickUntil(driver, wait,
                    () -> visible(APPLY),
                    ExpectedConditions.urlContains(expectedQueryFragment),
                    "apply the filters");
            return CoursesPage.this.waitForResults();
        }

        public boolean canReset() {
            return isPresent(RESET);
        }

        /** Clears every filter; the app navigates back to a bare /courses. */
        public CoursesPage reset() {
            Interactions.clickUntil(driver, wait,
                    () -> visible(RESET),
                    d -> !d.getCurrentUrl().contains("category=")
                            && !d.getCurrentUrl().contains("level="),
                    "clear the filters");
            return CoursesPage.this.waitForResults();
        }
    }
}
