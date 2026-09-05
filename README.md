# artra-e2e

End-to-end test automation for **Artra**, an online course platform.

The suite drives real browsers against a real, fully containerised instance of
the application — its database, cache and mail server included — and covers
the journeys that matter to the business: registering, signing in
through an emailed verification code, recovering a forgotten password, browsing
and filtering the catalogue, and managing an account.

It runs on Chrome, Firefox and Edge through Selenium Grid, in parallel, on a
laptop and in CI with the same command.

```
git clone <this repo>
cd artra-e2e
./run.sh browsers=chrome,firefox,edge
```

Nothing else is needed on the machine but Docker — no JDK, no Maven, no
browsers, no database, and no credentials.

---

## Contents

- [What Artra is](#what-artra-is)
- [What this project is](#what-this-project-is)
- [Technologies](#technologies)
- [Architecture](#architecture)
- [Test strategy](#test-strategy)
- [What is covered](#what-is-covered)
- [Defects this suite found](#defects-this-suite-found)
- [Project structure](#project-structure)
- [Running it](#running-it)
- [Selenium Grid](#selenium-grid)
- [Parallel execution](#parallel-execution)
- [Reports, screenshots and diagnostics](#reports-screenshots-and-diagnostics)
- [Continuous integration](#continuous-integration)
- [Configuration](#configuration)
- [Email and the test inbox](#email-and-the-test-inbox)
- [A two-minute tour](#a-two-minute-tour)
- [Known limitations](#known-limitations)

Longer reads: [docs/TEST-PLAN.md](docs/TEST-PLAN.md) (what is tested and why),
[docs/EMAIL.md](docs/EMAIL.md) (Resend and the test inbox).

---

## What Artra is

Artra is a Georgian-language e-learning marketplace: instructors publish video
courses, students browse a catalogue, buy a course and work through its lessons.
It is a SolidStart (SolidJS) application backed by PostgreSQL and Redis, with
transactional email, a websocket channel for live device notifications, and a
payment integration.

Two things about it shape the tests more than anything else:

1. **Authentication always has a second factor.** Correct credentials are not
   enough — the application parks a pending verification in Redis and requires
   either an emailed six-digit code or an approval pushed to an already-trusted
   device. Any test that needs a signed-in session has to go through a mailbox.
2. **Session and verification cookies are marked `Secure`.** A browser only
   stores those from an origin it considers trustworthy — HTTPS, or localhost —
   and a browser in a container reaching the app by its network address is
   neither. Each browser is therefore explicitly told to trust that one origin;
   see [Architecture](#architecture).

Both are handled by the environment rather than worked around in the tests —
see [Architecture](#architecture).

## What this project is

A regression suite a team could actually keep: page objects over robust
selectors, no arbitrary sleeps, deterministic fixtures, isolated tests, honest
failure diagnostics, and one command to run the whole thing.

It is deliberately **not** optimised for test count. Every test below exists
because it protects something a user would notice, and each one states in its
name and its failure message what it is protecting.

## Technologies

| Layer                | Choice |
|----------------------|--------|
| Language             | Java 25 |
| Build                | Maven (Surefire for unit tests, Failsafe for the browser suite) |
| Browser automation   | Selenium 4.43 (`RemoteWebDriver` against a Grid) |
| Test framework       | JUnit 5 (Jupiter), custom `@CrossBrowserTest` test template |
| Configuration        | Owner (typed config merged from env → system properties → `test.properties`) |
| Logging              | SLF4J + Logback (console and file) |
| Fixtures / assertions| PostgreSQL JDBC driver, Jackson (Mailpit's REST API) |
| Environment          | Docker Compose — app, PostgreSQL, Redis, Mailpit, Selenium Dynamic Grid |
| CI                   | GitHub Actions (browser matrix, artifacts, job summary) |

## Architecture

```
                              docker compose
  ┌──────────────────────────────────────────────────────────────────────┐
  │                                                                      │
  │   runner  ──────►  standalone-docker  ── docker.sock ──┐             │
  │   (JUnit 5,        (Selenium Grid 4.43)                │             │
  │    Selenium)                                           │ starts one  │
  │      │                                                 │ container   │
  │      │ JDBC (fixtures)                                 ▼ per session │
  │      │ HTTP  (test inbox)                    chrome / firefox / edge │
  │      │                                          (+ video recorder)   │
  │      │                                                 │             │
  │      │                                                 ▼             │
  │      │                                    artra  172.19.0.9:3000     │
  │      │                                         (SolidStart)          │
  │      │                                                 │             │
  │      ├──────────────┬──────────────────────────────────┤             │
  │      ▼              ▼                                  ▼             │
  │  postgres        mailpit                            redis            │
  │                                                                      │
  └──────────────────────────────────────────────────────────────────────┘
```

Every box is a container on one bridge network, `artra-net`, pinned to
`172.19.0.0/16`. The same commands work identically on a laptop and on a CI
runner.

Three decisions are worth calling out, because they are what make the
environment work at all:

**The grid starts browsers on demand.** `standalone-docker` runs no browser
itself. For each session it creates a container from the images in
`config.generated.toml`, attaches it to `artra-net`, and removes it when the
session ends — which is why the grid needs the Docker socket, and why a
Chrome-only run costs nothing for Firefox and Edge. The browsers land on the
same network as the application, so they can reach it directly.

**The application has a fixed address, and every browser is told to trust it.**
Artra is served at `http://172.19.0.9:3000` — pinned in `docker-compose.yml`,
because the address is also baked into the client bundle at build time. It is
plain HTTP, and Artra marks its session and verification cookies `Secure`,
which a browser will only store from an origin it considers *potentially
trustworthy*. Left alone, every cookie the app sets would be discarded
silently: no error, no console warning, just a sign-in that never sticks.
`DriverFactory` therefore declares that exact origin trustworthy to each
engine — `--unsafely-treat-insecure-origin-as-secure` plus a profile directory
on Chrome and Edge, the `dom.securecontext.allowlist` preference on Firefox —
derived from `TEST_BASE_URL`, so changing the address cannot leave the setting
pointing at the old one.

**Email is captured, never sent.** The application's email provider is selected
by `EMAIL_PROVIDER`: `resend` in production, `smtp` in the test stack, pointed
at [Mailpit](https://mailpit.axllent.org/). The suite reads verification codes
and reset links out of Mailpit's REST API, so the email flows are tested end to
end without an external provider, a real mailbox, or any credential. See
[docs/EMAIL.md](docs/EMAIL.md).

**The application under test is staged, not vendored.** Artra lives in its own
repository. `scripts/prepare-app.sh` copies a local checkout (or clones a fresh
one) into `.artra-build/` and adds this repository's Dockerfile, so the
application repository is never modified just to be testable.

## Test strategy

A fuller write-up — the risk analysis behind what was chosen, coverage by test
type, the test-data design and how flakiness is kept out — is in
[docs/TEST-PLAN.md](docs/TEST-PLAN.md). The short version:

**Scope.** End-to-end, through the browser, against a full stack. Anything that
a unit test could answer on its own is left to a unit test; what is here is the
behaviour that only appears when the pieces are connected — a cookie set by one
request being accepted by the next, a code that travels through a mail server, a
cursor built on page 1 being read on page 2.

**Test design.**

- **Page objects** wrap every screen. A test says
  `login.signInExpectingVerification(email, password)`, not `driver.findElement(...)`.
- **Selectors are chosen from what the application guarantees**: roles,
  `aria-label`s, ids and `href`s. Artra is styled entirely with Tailwind utility
  classes, which change whenever the design does; its accessible names do not.
- **No fixed sleeps.** Synchronisation is explicit: waits on a condition the
  application actually satisfies (a URL, a rendered element, a message), plus a
  documented settle window in the two places where "absent" and "not yet" are
  genuinely indistinguishable. There is exactly one `Thread.sleep` in the suite
  — the 500 ms interval between inbox polls in `MailpitClient`, which is a poll
  interval rather than a guess about how long something takes.
- **Hydration is handled honestly.** Artra is server-rendered and then hydrated,
  so a plain button is clickable a beat before it is wired up. Clicks on those
  controls go through `Interactions.clickUntil`, which repeats the click until
  its *effect* is observable and fails with a useful message if it never is.
- **Tests are independent.** Every test that needs an account registers its own;
  every one that needs an inbox uses its own address; every one that writes
  cleans up after itself. Nothing shares mutable state, which is what lets the
  whole suite run concurrently across three browsers.
- **Fixtures set up the backdrop, never the subject.** The catalogue, the
  instructors and the seeded student come from SQL. A session, an account or a
  contact message is created the way a user creates it — through the UI. A
  fixture that pre-creates the thing under test is how a suite ends up green
  against a broken feature.

**Where the database is used.** Only for teardown and for confirming a write the
UI cannot show — the contact form says "we received it" and then never displays
the message again, so the only honest way to know it was persisted is to look.

## What is covered

| Area | Tests |
|---|---|
| **Landing** (`LandingIT`) | Marketing page renders for anonymous visitors; featured courses are published and link to their detail pages; the newest published course leads; header navigation; unknown routes render the 404 page |
| **Catalogue** (`CourseCatalogueIT`) | Listing and totals; category, level, price and discount filters; clearing filters; sorting by price both ways; cursor-based paging that continues the sort; unsatisfiable filters show the empty state; unpublished drafts stay hidden |
| **Course detail** (`CourseDetailIT`) | The clicked card opens the right course; description, instructor, curriculum and purchase call-to-action; breadcrumb; curriculum sections expand; unknown slug shows the not-found panel |
| **Registration** (`RegistrationIT`) | Sign-up → emailed code → account created and signed in; the account does *not* exist before the code is confirmed; duplicate address is refused without confirming it exists; a wrong code creates nothing; requesting a new code invalidates the previous one |
| **Authentication** (`AuthenticationIT`) | Correct credentials alone do not grant access; sign-in completes with the emailed code; wrong password and unknown address are refused and send no mail; sign-out really ends the session; a signed-in visitor is redirected away from the sign-in page; password masking; links to the adjacent journeys |
| **Password reset** (`PasswordResetIT`) | Emailed link → new password → sign in with it; the old password stops working; an unknown address gets the same answer and no email; a mismatched confirmation is refused and leaves the old password working; the reset form cannot be opened without the link |
| **Access control** (`AccessControlIT`) | `/account` and `/account/security` redirect anonymous visitors; `/verify/email` and `/verify/pending` refuse direct access |
| **Account** (`AccountIT`) | The profile shows the signed-in user; active sessions are listed; a password change takes effect on the next sign-in; a wrong current password and a mistyped confirmation are both refused without changing anything |
| **Contact form** (`ContactFormIT`) | A valid message is accepted and stored; a too-short message is blocked before it is sent; a bounced submission does not clear what the visitor typed |
| **Framework** (`CrossBrowserExtensionTest`) | The browser-list parser, run by Surefire before any browser starts |

Every browser test runs once per configured browser, so the full matrix is
these tests × three.

## Defects this suite found

Building the suite surfaced two real bugs in the application's catalogue query.
Both are fixed in the application and pinned by regression tests here.

1. **Unpublished courses were listed publicly.** The catalogue listing was the
   only query in the application that did not filter on `course.status`, so
   drafts were included in the reported total and appeared on the last page of
   the catalogue. Guarded by
   `CourseCatalogueIT.unpublishedCoursesStayHidden`.
2. **The discount filter did nothing on its own.** `?offer=sale` was appended to
   the SQL *after* the `WHERE` clause was assembled, so when no other filter was
   active it attached itself to a `LEFT JOIN … ON` condition instead and quietly
   returned the entire catalogue. Guarded by
   `CourseCatalogueIT.discountFilterReturnsOnlyOffers`.

One behaviour is recorded rather than fixed:
`AuthenticationIT.failureMessagesDistinguishKnownAccounts` documents that a
wrong password and an unknown address produce different messages, which makes
the sign-in form usable as an account-existence oracle. The test states the
current behaviour so that tightening it is a deliberate change rather than an
accident.

## Project structure

```
artra-e2e/
├── run.sh                     one command: stack up, suite, artifacts, teardown
├── dev.sh                     stack up and left up, for writing tests
├── docker-compose.yml         the whole environment
├── Dockerfile                 the test runner image (JDK 25 + Maven + the suite)
├── pom.xml
│
├── config.toml                Dynamic Grid template; the scripts generate
│                              config.generated.toml from it per run
├── lib/stack-env.sh           browser validation, machine sizing, grid config,
│                              video ownership and archiving
├── scripts/
│   ├── prepare-env.sh         generates .env on first run (no committed secrets)
│   ├── prepare-app.sh         stages the application under test
│   └── summarise.sh           turns the Failsafe XML into a readable summary
│
├── stack/
│   ├── app/Dockerfile         builds Artra for testing
│   ├── app/seed-users.mjs     gives the seeded accounts a password (Argon2id)
│   └── db/init/               01-schema.sql, 02-seed.sql
│
├── src/test/java/com/artra/e2e/
│   ├── base/                  config, driver lifecycle, cross-browser template,
│   │                          parallelism strategy, failure diagnostics, BasePage
│   ├── components/            Header — shared across pages
│   ├── pages/                 one class per screen
│   ├── support/               Mailpit client, database fixtures, test data, flows
│   └── tests/                 the suite (*IT)
│
├── src/test/resources/        test.properties, junit-platform.properties, logback
├── docs/EMAIL.md              Resend and the test inbox
└── .github/workflows/e2e.yml  CI
```

## Running it

**Requirements:** Docker with the Compose plugin. That is all.

```bash
./run.sh                                     # chrome, headless
./run.sh browsers=chrome,firefox,edge        # the full matrix
./run.sh browsers=chrome record=chrome       # record the sessions to video
./run.sh tags=smoke                          # one tag
./run.sh test=CourseCatalogueIT              # one class
./run.sh sessions=4                          # override the auto-sized parallelism
./run.sh keep=true                           # leave the stack running afterwards
```

`run.sh` brings the stack up, runs the suite, archives the results under
`runs/<timestamp>/` and exits with Maven's exit code — so it fails the shell,
and the pipeline, exactly when the tests fail.

### Writing or debugging tests

```bash
./dev.sh browsers=chrome record=chrome
```

This leaves the environment running and gives you a fast loop. `src/` is
bind-mounted into the runner, so an edit on the host is picked up by the next
run with no rebuild:

```bash
docker compose exec runner mvn verify                              # everything
docker compose exec runner mvn verify -Dit.test=LandingIT          # one class
docker compose exec runner mvn verify -Dit.test=LandingIT#unknownRouteRendersNotFound
docker compose exec runner mvn verify -Dgroups=auth                # one tag
docker compose exec runner mvn verify -Dgroups='auth & !email'     # tag expression
```

While it runs:

| | |
|---|---|
| Grid console | <http://localhost:4444> — live sessions and the containers behind them |
| Test inbox | <http://localhost:8025> |
| Recordings | `./videos/<session-id>/<test-name>.mp4`, when started with `record=` |

The application is not published to the host: it lives at `172.19.0.9:3000` on
the compose network, reachable from the browsers and the runner. `curl` it from
there with `docker compose exec runner ...`, or add a port mapping if you want
it on the host.

Tags currently in use: `smoke`, `catalogue`, `auth`, `email`, `access`,
`account`, `forms`.

### Running against an application you are already running

The suite has no hard dependency on the bundled stack — point it somewhere else:

```bash
TEST_BASE_URL=https://my-artra-instance.example ./run.sh
```

Every setting is an environment variable; see [Configuration](#configuration).

## Selenium Grid

A **Dynamic Grid** — one `selenium/standalone-docker` container with the Docker
socket mounted. It runs no browser of its own:

```
runner ──HTTP──► selenium-hub:4444 ──docker.sock──► browser container  ┐
                                                    video recorder     ├─ per session,
                                                    (both removed      ┘  created and
                                                     when it ends)        destroyed
                                                          │
                                                          ▼
                                              artra  172.19.0.9:3000
```

- **`config.toml` is the tracked template**, and the startup scripts write
  `config.generated.toml` from it on every run, patching two things:
  `max-sessions` to match `PARALLELISM`, and `[docker].configs` down to the
  browsers actually requested. Generated rather than edited in place, so
  `config.toml` never shows up dirty in `git status`.
- **Only the requested browsers are declared.** The grid resolves every image in
  `configs` at startup and pulls any it does not have, so leaving all three in
  would make `./run.sh browsers=chrome` fetch Firefox and Edge as well —
  several GB, during which the healthcheck fails.
- **Images are pre-pulled** before the grid starts, for the same reason: a pull
  triggered by the first session request can outlast the session timeout.
- Browser and driver versions are **pinned to exact tags**, so a run is
  reproducible.

Browsers are selected purely through configuration — the `BROWSERS` environment
variable, written by the startup scripts and set directly by the CI matrix. No
test names a browser.

**What it costs.** A container per session is a few seconds of start-up that a
long-lived node does not pay, so the full 150-test matrix runs in roughly twelve
minutes here against about five for a fixed set of always-on nodes. What it buys
is worth more for this project: every session starts from a genuinely clean
browser, a Chrome-only run consumes nothing for Firefox and Edge, capacity is one
number in one file, and video recording comes as a per-session sidecar rather
than a separate always-running service.

**Supported:** Chrome, Firefox, Edge. Adding one means adding an image to
`config.toml` and `lib/stack-env.sh` and a case to `DriverFactory` —
`CrossBrowserExtensionTest` fails if those ever disagree.

### Watching a run

The Grid console at <http://localhost:4444> shows live sessions and the
containers backing them. For a visual record, record the run to video:

```bash
./run.sh browsers=chrome,firefox,edge record=chrome,firefox,edge
```

Each recorded session gets a recorder sidecar that captures the browser
container's display and writes `videos/<session-id>/<test-name>.mp4`, named
from the `se:name` capability the suite sets. Two things follow from how this
works, and both are handled for you:

- **Recording implies headed.** A headless browser paints nothing to the
  display, so a recorded headless session produces a blank file. `RECORD_VIDEO`
  therefore also switches those browsers out of headless mode.
- **The recorder writes as uid 1200.** If `videos/` is not owned by
  `1200:1201` it produces an empty directory per session and no file at all —
  silently. `generate_grid_config` sets that ownership before the grid starts,
  and `archive_videos` hands it back afterwards so the recordings can be moved
  into `runs/`.

Recording is opt-in, and deliberately so. It roughly halves the number of
sessions a machine can sustain — the auto-sizer accounts for that — and the
sessions themselves are slower, because the browser is really rendering. On a
four-core laptop the full 150-test matrix takes about five minutes headless and
well over an hour with every browser recorded. Record a slice, not the suite:

```bash
./run.sh browsers=chrome,firefox,edge record=chrome,firefox,edge tags=smoke
```

## Parallel execution

Enabled in `junit-platform.properties`: test classes and every
`@CrossBrowserTest` invocation run concurrently.

The thread pool is sized by `GridParallelismStrategy`, which reads `PARALLELISM`
— a number `lib/stack-env.sh` computes from the machine's *physical* cores
(`nproc` counts hyperthreads, which a rendering browser cannot use) and its
available RAM, capped at 6. Past that the bottleneck stops being the browsers
and becomes the single application container they all talk to.

That same number becomes the grid's `max-sessions` in
`config.generated.toml`, so the client can never ask for more browser
containers than the grid will start at once. Recording halves the auto-sized
value, because a recorded session runs headed and carries an ffmpeg sidecar
that saturates a core on its own.

**Shared state is avoided by construction, not by convention:**

- Each driver lives in JUnit's per-invocation `Store`, not a `ThreadLocal` —
  JUnit scopes it automatically, so nothing leaks when the pool reuses a thread.
- Every test that needs an account registers its own, with a unique address
  (`TestData.uniqueEmail`), so two invocations can never read each other's
  verification codes.
- Nothing ever clears the shared inbox; lookups are scoped to a recipient and a
  timestamp instead.
- Tests that write to the database delete what they wrote in `@AfterEach`.

## Reports, screenshots and diagnostics

Every run archives to `runs/<timestamp>/`:

```
runs/2026-09-05_18-32-19/
├── reports/          Failsafe XML + text, one file per test class
├── unit-reports/     Surefire XML for the framework's own unit tests
├── diagnostics/      one directory per failure (see below)
├── videos/           one directory per recorded session, when run with record=
├── logs/             the full run log
└── summary.md        pass/fail table and the list of failures
```

When a test fails, the driver is not thrown away before it has been asked what
it was looking at. Each failure leaves:

| File | Why it is there |
|---|---|
| `screenshot.png` | what the browser was actually showing |
| `page.html` | the rendered DOM — answers "was the element missing, or just invisible?" without a re-run |
| `context.txt` | URL, document title, and the failure itself |
| `console.log` | browser console errors (Chrome and Edge; geckodriver does not expose this) |

Capture happens in the extension's `afterEach` rather than in a `TestWatcher`,
because JUnit runs `afterEach` callbacks *before* `testFailed` — a watcher would
only ever see an already-quit driver.

Failure messages are treated as part of the deliverable — the person reading
them is usually not the person who wrote the test. Rather than
`expected: <true> but was: <false>`, a failure here reads:

```
VerifyPendingPage never appeared. Expected path /verify/pending, browser is at
/login titled 'Artra - შესვლა'

The catalogue should count only published courses. A larger total means drafts
are being listed. ==> expected: <18> but was: <20>

No email arrived for signup-4k2p9x-7@artra.test within 45s. Mailpit currently
holds 0 message(s) for that address. Check the app container's logs for
ERROR_WHILE_SENDING_EMAIL, and that the artra service still has
EMAIL_PROVIDER=smtp in docker-compose.yml.
```

## Continuous integration

`.github/workflows/e2e.yml` runs on every push to `main`, every pull request,
and on demand.

```
build ──► plan ──► e2e (chrome)   ┐
                   e2e (firefox)  ├─ each: stack up → suite → artifacts
                   e2e (edge)     ┘
```

1. **`build`** — sets up JDK 25, compiles the suite and runs the framework's
   unit tests (`mvn verify -DskipITs`). Seconds, so a typo never costs a full
   browser matrix to discover.
2. **`plan`** — turns the comma-separated browser list into the JSON that
   `strategy.matrix` consumes. It is a job of its own because GitHub's
   expression language has no way to split a string.
3. **`e2e`** — one job per browser. Each checks out this repository and the
   application, brings up its own stack, runs the whole suite against it, and
   uploads the results. `fail-fast: false`, because Chrome failing tells you
   nothing about Firefox.

The pipeline fails when tests fail: `run.sh` exits with Maven's exit code.

**Artifacts on every run** (kept 14 days): `e2e-<browser>` contains
`runs/<timestamp>/` — reports, screenshots, page dumps, console logs and the
summary. On a failed run, `container-logs-<browser>` adds the full
`docker compose logs`, which is where an infrastructure problem shows up as
opposed to a test problem. The pass/fail table is also pinned to the job summary
page.

**Manual runs.** *Actions → E2E → Run workflow* takes a browser list and a tag
filter, so a targeted run needs no YAML edit.

### The suite also gates the application's own repository

The application lives in [7Luke7/Artraa](https://github.com/7Luke7/Artraa), and
that repository has a workflow of its own which checks this suite out and runs it
against the commit being pushed. So a change to Artra is tested by these tests
before it can land, not only when someone remembers to run them.

```
Artraa: push to main / pull request
        └── checks out 7Luke7/artra-e2e@main   (the suite)
        └── checks out this commit             (the application)
            └── ./run.sh browsers=<matrix>  ×  chrome | firefox | edge
```

`main` on that repository is protected: changes go through a pull request, and
all three E2E jobs are required status checks. A direct push is refused by
GitHub outright —

```
remote: - Changes must be made through a pull request.
remote: - 3 of 3 required status checks are expected.
 ! [remote rejected] main -> main (protected branch hook declined)
```

The suite is pinned to this repository's `main` rather than to a tag, so a
regression test written here starts guarding the application as soon as it
exists. The trade-off is real and deliberate: a newly strict test here can turn
the application's CI red without the application having changed.

**No secrets are configured for CI, and none are needed.** The stack generates
its own throwaway keys and captures every email in a disposable mail server.
That is deliberate: a pull request from a fork can run the entire suite without
ever being handed a credential. `RESEND_API_KEY` belongs to the *application's*
production environment, not to this pipeline — see [docs/EMAIL.md](docs/EMAIL.md).

## Configuration

Every setting is an environment variable, resolved in this order: **environment
→ system properties → `src/test/resources/test.properties`**. Anything sensitive
has no default at all, so an unset one fails with a message naming the variable
instead of reaching a form as an empty string.

### Suite

| Variable | Default | Purpose |
|---|---|---|
| `BROWSERS` | `chrome` | Comma-separated browsers each test runs against |
| `RECORD_VIDEO` | *(empty)* | Subset of `BROWSERS` to record to video (those run headed) |
| `PARALLELISM` | `1` | Thread pool size, and the grid's `max-sessions` |
| `SELENIUM_HUB_URL` | `http://selenium-hub:4444/wd/hub` | Grid router |
| `TEST_BASE_URL` | `http://172.19.0.9:3000` | The application under test; also what `DriverFactory` declares trustworthy |
| `MAILPIT_URL` | `http://mailpit:8025` | Test inbox API |
| `MAIL_WAIT_SECONDS` | `45` | How long an email gets to arrive |
| `EXPLICIT_WAIT_SECONDS` | `30` | Default `WebDriverWait` timeout |
| `PAGE_LOAD_TIMEOUT_SECONDS` | `60` | Navigation timeout |
| `IMPLICIT_WAIT_SECONDS` | `0` | Deliberately zero — see `TestConfig` |
| `DIAGNOSTICS_DIR` | `target/diagnostics` | Where failure bundles are written |
| `DATABASE_JDBC_URL` | `jdbc:postgresql://postgres:5432/artra` | Fixtures and teardown |
| `DATABASE_USER` | `artra` | |
| `DATABASE_PASSWORD` | *(none — required)* | |
| `SEED_STUDENT_EMAIL` | `student@artra.test` | Seeded account used by negative auth tests |
| `SEED_USER_PASSWORD` | *(none — required)* | Password for every seeded account |

### Stack

`.env` is generated on first run by `scripts/prepare-env.sh` with fresh random
values, is `chmod 600`, and is gitignored. `.env.example` documents every key if
you would rather pin them yourself. Nothing in it is a real credential — each
value keys a hash or an HMAC inside a throwaway stack — but it is generated per
machine so that no example value can ever end up protecting something real.

```
APP_URL, APP_WS_URL          the app's fixed address (baked into the client bundle at build time)
POSTGRES_USER/DB/PASSWORD    the application database
SEED_STUDENT_EMAIL           the seeded fixture account
SEED_USER_PASSWORD           its password, hashed with the app's own Argon2 parameters
ARGON_SECRET                 Argon2id key
CODE_PEPPER                  verification-code HMAC key
PASSWORD_RESET_SECRET        reset-token HMAC key
SIGNATURE_SECRET, IP_SECRET  device-fingerprint HMAC keys
EMAIL_FROM, EMAIL_REPLY_TO   headers on the captured mail
```

`config.generated.toml` (the grid's effective configuration for a run) and
`videos/` are also gitignored; `config.toml` itself is tracked.

## Email and the test inbox

Artra's email provider is now selected by `EMAIL_PROVIDER`:

- **`resend`** (default) — the [Resend](https://resend.com) API, which replaced
  the previous Gmail/Nodemailer transport.
- **`smtp`** — a plain SMTP server. The test stack sets this and points it at
  Mailpit, so the suite reads real delivered messages without touching an
  external provider or anyone's real inbox.

The application code does not know which is active; both go through one
`send_email` entry point.

**Resend requires a one-time manual setup and has a constraint on the sender
address.** Read [docs/EMAIL.md](docs/EMAIL.md) before configuring production.

## A two-minute tour

If you have Docker and two minutes, this is the whole project end to end.

```bash
./dev.sh browsers=chrome,firefox,edge record=chrome,firefox,edge
```

Then, while it runs:

| Open | You will see |
|---|---|
| <http://localhost:4444> | the Grid console — sessions appearing and disappearing as the grid starts and destroys a browser container for each one |
| `docker ps` | `browser-chrome-…` and `recorder-chrome-…` containers coming and going in real time |
| <http://localhost:8025> | the test inbox filling with verification codes and reset links |

Kick the suite off in another terminal:

```bash
docker compose exec runner mvn verify -Dgroups=smoke     # ~30 seconds
docker compose exec runner mvn verify                    # the whole thing
```

and when it finishes:

```bash
cat runs/*/summary.md
open runs/*/videos/*/*.mp4     # one recording per test, named after it
```

On GitHub, the same run is the **E2E** workflow: three matrix jobs, one per
browser, each with its `e2e-<browser>` artifact attached and the pass/fail table
on the job summary page.

## Known limitations

Stated plainly, because a suite that hides its gaps is worse than one with gaps.

- **Payments are not automated.** Buying a course hands off to an external bank
  gateway. The tests assert that the purchase call-to-action is present and
  correctly labelled, and stop there; driving a real payment would mean
  automating a third party's UI against their sandbox, which is a separate piece
  of work with its own credentials.
- **The course player is not covered**, because reaching it requires an
  enrolment, which requires a completed payment. Faking the enrolment row would
  make the test pass without testing the path a user takes.
- **Device-approval sign-in is not automated.** The second verification method
  pushes an approval over a websocket to a second, already-signed-in browser.
  It is testable — two drivers in one test — but it is a different kind of test
  from the rest of this suite and has not been built.
- **Google sign-in is not covered.** It depends on Google's own consent screen.
- **The suite is desktop-only.** Every session runs at 1920×1080; Artra's mobile
  navigation is a separate component and is not exercised.
