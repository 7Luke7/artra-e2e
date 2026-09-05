package com.artra.e2e.base;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the annotated method once per browser in BROWSERS, each invocation with
 * its own WebDriver and WebDriverWait injected as parameters. Invocations run
 * concurrently, capped by {@link GridParallelismStrategy}.
 *
 * <pre>{@code
 * @CrossBrowserTest
 * @DisplayName("Anonymous visitor sees the sign-in call to action")
 * void headerOffersSignIn(WebDriver driver, WebDriverWait wait) { ... }
 * }</pre>
 *
 * Prefer this over @Test for anything that touches a page: a test that only
 * ever ran on Chrome is exactly the test that breaks the day someone opens the
 * site in Firefox.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(CrossBrowserExtension.class)
public @interface CrossBrowserTest {
}
