# Test plan

What is tested, why it was chosen, and what was deliberately left out.

## Risk-based selection

Artra is a marketplace: someone finds a course, creates an account, pays, and
comes back to it. The suite is built around the parts of that where a defect is
both *likely* and *expensive*, rather than around the parts that are easy to
automate.

| Risk | Why it scores high | Covered by |
|---|---|---|
| A visitor cannot create an account | Two-step flow across Redis, Argon2, an outbound email and a code; four moving parts, any of which can break silently | `RegistrationIT` |
| A returning user cannot sign in | Same again, plus a second factor with two branches | `AuthenticationIT` |
| A locked-out user cannot recover | One-time token, a short-lived cookie, a password rewrite, and every session dropped | `PasswordResetIT` |
| A protected page leaks | Guards are server-side and invisible in normal use — the app keeps working perfectly for anyone who signs in first | `AccessControlIT` |
| The catalogue shows the wrong thing | Filters, sort and cursor paging all write into one query string and are decoded by one query builder; changing any one routinely breaks the other two | `CourseCatalogueIT` |
| The course page misinforms a buyer | Last screen before money changes hands | `CourseDetailIT` |
| A password change locks someone out | Rewrites a credential and drops other sessions | `AccountIT` |
| An enquiry is silently dropped | The only write an anonymous visitor can make, and the app never shows it back | `ContactFormIT` |
| The front page is empty | Not subtle, but it is the first thing anyone sees, and it fails fast for the whole suite | `LandingIT` |

## Coverage by type

Each area is tested along the same four axes, rather than piling up happy paths.

| | Example |
|---|---|
| **Happy path** | Register → emailed code → signed in |
| **Negative** | Wrong code, wrong password, unknown address, duplicate address, mismatched confirmation |
| **Validation** | A contact message under the 50-character minimum is blocked before it is sent |
| **Authorisation** | `/account`, `/account/security`, `/verify/email`, `/verify/pending`, `/reset/password` all refuse direct access |
| **State transition** | The old password stops working after a reset; a resent code invalidates the previous one; signing out really ends the session |
| **Data integrity** | The contact message reaches the database intact; the account does *not* exist until the code is confirmed |

## Deliberate omissions

Listed because an untested area that nobody has written down is indistinguishable
from an oversight.

| Not covered | Why |
|---|---|
| Payment | Hands off to an external bank gateway. Automating a third party's sandbox UI is a separate piece of work with its own credentials and its own flakiness. |
| The course player | Requires an enrolment, which requires a completed payment. Inserting the enrolment row directly would make the test pass without testing what a user does. |
| Device-approval sign-in | The second factor's other branch pushes an approval over a websocket to an already-signed-in device. Testable with two drivers in one test; not built. |
| Google sign-in | Reachable under `public=on`, which serves the app over HTTPS on an origin Google will render the button for; not automated, because the consent screen is Google's own and Google refuses sign-in from automated browsers. |
| Mobile layout | Every session runs at 1920×1080. Artra's mobile navigation is a separate component. |
| Visual regression | Out of scope for a functional suite; would need a baseline store and a separate review workflow. |
| Load and performance | Different tooling, different environment, different question. |

## Test data

| | |
|---|---|
| **Fixed backdrop** | 18 published courses across two categories and three levels, two drafts, two instructors, two students, sections, lessons and reviews. Defined in `stack/db/init/02-seed.sql` with name-derived UUIDs, so the same row has the same id on every machine. |
| **Per-test data** | Anything a test creates: accounts, sessions, contact messages. Generated unique (`TestData`), created through the UI, deleted in `@AfterEach`. |
| **Never seeded** | The thing under test. A pre-created account would let a registration suite pass against broken registration. |

The catalogue is sized on purpose: 18 published courses is more than one page
(the API serves 15), so paging is exercised; prices are distinct, so an ordering
assertion has a total order to check; six fall inside the 50–150 band the price
filter uses; five carry a discount; and the two drafts exist so "published only"
is a claim the suite can actually test rather than assume.

## Reliability

Flakiness is treated as a defect in the suite, not as weather.

- **No fixed sleeps.** Every wait is on a condition the application satisfies.
  The suite's only `Thread.sleep` is the interval between inbox polls.
- **Two documented settle windows**, where "absent" and "not yet rendered" are
  genuinely indistinguishable: `BasePage.isAbsent` and
  `AccountSecurityPage.sessionCount`. Both are bounded well below the general
  timeout and both say in a comment why they exist.
- **Hydration races are handled, not slept through.** Server-rendered markup is
  interactive-looking before Solid attaches its handlers, so clicks on plain
  buttons go through `Interactions.clickUntil`, which repeats until the click's
  *effect* is observable. Every condition passed to it is idempotent.
- **Timeouts are set per operation.** A page load and an Argon2-backed sign-in
  are not the same wait, and giving them the same number means one of them is
  either fragile or slow.
- **Every test is independent**, so a rerun of one failure is meaningful and the
  order they run in is not.
- **Every session is a fresh container.** The grid starts one per test and
  destroys it afterwards, so no cookie, cache entry or profile setting can
  survive from one test into the next — isolation is a property of the
  environment rather than of cleanup code that has to remember everything.
