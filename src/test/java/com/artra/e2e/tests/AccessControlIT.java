package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.LandingPage;
import com.artra.e2e.pages.VerifyEmailPage;

/**
 * What an unauthenticated browser is allowed to reach.
 *
 * Every one of these routes is guarded server-side, and a guard that quietly
 * stops working is invisible in normal use - the app keeps working perfectly
 * for everyone who signs in first. These tests are cheap, they are the ones
 * worth having, and they are deliberately grouped so the whole set can be run
 * on its own after any change to routing or session handling.
 */
@Tag("auth")
@Tag("access")
class AccessControlIT {

    @CrossBrowserTest
    @DisplayName("The account page redirects an anonymous visitor to sign in")
    void accountRequiresASession(WebDriver driver, WebDriverWait wait) {
        LandingPage anchor = new LandingPage(driver, wait);
        driver.get(anchor.url("/account"));

        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"),
                "An anonymous visitor asking for /account should be sent to sign in");
    }

    @CrossBrowserTest
    @DisplayName("The security settings page redirects an anonymous visitor to sign in")
    void securitySettingsRequireASession(WebDriver driver, WebDriverWait wait) {
        LandingPage anchor = new LandingPage(driver, wait);
        driver.get(anchor.url("/account/security"));

        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"),
                "An anonymous visitor asking for /account/security should be sent to sign in");
    }

    @CrossBrowserTest
    @DisplayName("The verification screen refuses to open without a pending verification")
    void verificationScreenRefusesDirectAccess(WebDriver driver, WebDriverWait wait) {
        VerifyEmailPage verify = new VerifyEmailPage(driver, wait);
        driver.get(verify.url("/verify/email"));

        // The code screen is the last step before a session is issued, so it
        // must not be reachable by typing the URL.
        assertAll(
                () -> assertTrue(verify.isAccessDenied(),
                        "Opening /verify/email directly should be refused"),
                () -> assertEquals("წვდომა შეზღუდულია", verify.accessDeniedHeading(),
                        "The refusal should say access is restricted"));
    }

    @CrossBrowserTest
    @DisplayName("The verification-method chooser refuses to open without a pending sign-in")
    void verificationChooserRefusesDirectAccess(WebDriver driver, WebDriverWait wait) {
        VerifyEmailPage guard = new VerifyEmailPage(driver, wait);
        driver.get(guard.url("/verify/pending"));

        assertTrue(guard.isAccessDenied(),
                "Opening /verify/pending directly should be refused");
    }
}
