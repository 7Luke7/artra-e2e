package com.artra.e2e.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.artra.e2e.base.CrossBrowserTest;
import com.artra.e2e.pages.ContactPage;
import com.artra.e2e.support.ArtraDatabase;
import com.artra.e2e.support.TestData;

/**
 * The contact form - the only write an anonymous visitor can make.
 *
 * Each invocation uses its own sender address so the three browsers can run
 * this concurrently and still assert on "their" row, and so a failed run leaves
 * nothing behind that a later run would trip over.
 */
@Tag("forms")
class ContactFormIT {

    /** Set as soon as the address is generated, so teardown runs even when the
     *  test failed halfway through submitting. */
    private String sender;

    @AfterEach
    void removeMessage() {
        if (sender != null) {
            ArtraDatabase.deleteContactMessages(sender);
        }
    }

    @CrossBrowserTest
    @DisplayName("A valid contact message is accepted and stored")
    void validMessageIsStored(WebDriver driver, WebDriverWait wait) {
        sender = TestData.uniqueEmail("contact");
        String body = TestData.contactMessage();

        ContactPage contact = new ContactPage(driver, wait).get()
                .send("ტესტ მომხმარებელი", sender, "ტექნიკური პრობლემა", body);

        assertTrue(contact.wasAccepted(),
                "The form should confirm receipt, but reported: " + contact.errorMessage());
        assertTrue(contact.successMessage().contains("მიღებულია"),
                "The confirmation should say the message was received, but read: "
                        + contact.successMessage());

        // The app never shows the message again, so the only way to know it was
        // persisted rather than merely acknowledged is to look at the row.
        assertEquals(body, ArtraDatabase.contactMessageFor(sender).orElse(null),
                "The stored message should match what was typed");
    }

    @CrossBrowserTest
    @DisplayName("A message shorter than the minimum is blocked before it is sent")
    void shortMessageIsBlocked(WebDriver driver, WebDriverWait wait) {
        sender = TestData.uniqueEmail("contact-short");

        ContactPage contact = new ContactPage(driver, wait).get()
                .fill("ტესტ მომხმარებელი", sender, "სხვა", "ძალიან მოკლე");
        contact.submit();

        assertFalse(contact.isMessageValid(),
                "A 12-character message should fail the field's minlength=50 rule");
        assertFalse(contact.wasAccepted(),
                "A message that fails client-side validation should not be accepted");
        assertTrue(ArtraDatabase.contactMessageFor(sender).isEmpty(),
                "Nothing should have been written for a message that never passed validation");
    }

    @CrossBrowserTest
    @DisplayName("The form keeps the visitor's input when the browser rejects it")
    void rejectedSubmitKeepsInput(WebDriver driver, WebDriverWait wait) {
        sender = TestData.uniqueEmail("contact-keep");

        ContactPage contact = new ContactPage(driver, wait).get()
                .fill("ტესტ მომხმარებელი", sender, "ანგარიშის საკითხი", "მოკლე");
        contact.submit();

        // Losing a long message to a validation bounce is a real complaint that
        // no server-side test can catch, so it is worth stating here.
        assertAll(
                () -> assertFalse(contact.wasAccepted(), "The submission should not go through"),
                () -> assertEquals(sender, contact.enteredEmail(),
                        "The email the visitor typed should still be in the form"),
                () -> assertEquals("მოკლე", contact.enteredMessage(),
                        "The message the visitor typed should not be cleared by the bounce"));
    }
}
