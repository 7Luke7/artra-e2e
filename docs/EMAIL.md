# Email: Resend in production, a captured inbox in tests

Artra sends three transactional emails, and all three gate a flow a user cannot
complete without them:

| Email | Sent when | What it carries |
|---|---|---|
| Verification code | registration, and the email branch of sign-in | a six-digit code, valid 30 minutes |
| Verification code (resent) | the visitor presses "send it again" | a **new** code; the previous one stops working |
| Password reset | a recovery request for a known address | a one-time link, valid 30 minutes |

## What changed in the application

The previous implementation created a Nodemailer transport inside
`src/routes/api/utils.js` and authenticated to **Gmail** with an app password
(`EMAIL_SECRET`). That is now gone. In its place:

```
src/routes/api/lib/email/index.js     one send_email(), two transports
```

The transport is chosen by `EMAIL_PROVIDER`:

| `EMAIL_PROVIDER` | Transport | Used by |
|---|---|---|
| `resend` (default) | the Resend API, via the official `resend` SDK | production |
| `smtp` | plain SMTP | local development and the E2E stack, pointed at Mailpit |

Nothing else in the application changed: `send_verification_code` and
`send_verification_link` call the same function and get the same `{ status }`
back either way.

`nodemailer` is kept as a dependency because it is the SMTP driver for the test
path. It no longer talks to Gmail, and the only SMTP server it is ever pointed
at is the disposable one inside the test stack. (The same code path also works
against a real relay — including Resend's own SMTP endpoint — if `SMTP_USER` and
`SMTP_PASSWORD` are set.)

### Environment variables

| Variable | Where | Notes |
|---|---|---|
| `EMAIL_PROVIDER` | app | `resend` or `smtp`. Defaults to `resend`. |
| `RESEND_API_KEY` | app, production only | **Never** in source, a README, a compose file or a workflow YAML. |
| `EMAIL_FROM` | app | Must be an address on a domain verified in your Resend account — see below. |
| `EMAIL_REPLY_TO` | app | Where replies go. This one *can* be any mailbox you control. |
| `SMTP_HOST` / `SMTP_PORT` | app, test only | `mailpit` / `1025` in the stack. |
| `SMTP_USER` / `SMTP_PASSWORD` | app, optional | Only if pointing the SMTP path at an authenticated relay. |
| `SMTP_SECURE` | app, optional | `true` for implicit TLS. |

---

## ⚠️ Action required: the sender address

**You asked for `chikvaidzeluka18@gmail.com` as the sender. Resend cannot send
from that address, and no amount of configuration will change that.**

Resend — like every reputable transactional provider — only accepts a `from`
address on a domain you have verified in your account by publishing DNS records
(SPF, DKIM and a return-path CNAME). Verifying `gmail.com` is not possible;
Google owns it. A provider that let you send as an arbitrary Gmail address would
be an open relay for spoofing, so this is a deliberate restriction rather than a
gap.

There are three honest ways forward. **Pick one and tell me which** — the code
already supports all three, only the configuration differs.

### Option A — verify a domain you own *(recommended)*

If you own a domain for Artra (say `artra.ge`):

1. Resend dashboard → **Domains** → **Add Domain** → enter `artra.ge`.
2. Resend shows three or four DNS records. Add them at your registrar:
   - `TXT` for SPF (usually on `send.artra.ge`)
   - `TXT` for DKIM (`resend._domainkey`)
   - `MX` and `TXT` on `send.artra.ge` for the return path
   - optionally a `TXT` DMARC record on `_dmarc`
3. Wait for Resend to show **Verified** (usually minutes, up to 48 hours).
4. Set:
   ```
   EMAIL_FROM=Artra <no-reply@artra.ge>
   EMAIL_REPLY_TO=chikvaidzeluka18@gmail.com
   ```

Your Gmail address still receives every reply — it is simply not the envelope
sender. This is what production email normally looks like.

### Option B — Resend's shared test domain

Set `EMAIL_FROM=Artra <onboarding@resend.dev>` (this is the built-in default).
Needs no DNS at all.

**The catch:** `onboarding@resend.dev` can only deliver to the email address
that owns the Resend account. Every other recipient is rejected. Fine for
proving the integration works; useless for real users.

### Option C — keep sending through Gmail

If you genuinely need `chikvaidzeluka18@gmail.com` as the visible sender and do
not have a domain, the transport has to be Gmail's SMTP, not Resend:

```
EMAIL_PROVIDER=smtp
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=chikvaidzeluka18@gmail.com
SMTP_PASSWORD=<a Google App Password>
EMAIL_FROM=Artra <chikvaidzeluka18@gmail.com>
```

This is roughly what the application did before, with the credential moved out
of the code. It is worth knowing what you would be accepting: Gmail caps sending
at around 500 messages a day, gives you no delivery logs, bounce handling or
suppression list, and a personal account being used for transactional mail can
be suspended without warning.

### Where the key goes — never in this repository

| Where the app runs | Where `RESEND_API_KEY` goes |
|---|---|
| Your machine | Artra's own `.env` (already gitignored) |
| A host (Vercel, Fly, Railway, a VPS) | that platform's environment/secret settings |
| A GitHub Actions job that *deploys* Artra | **Settings → Secrets and variables → Actions → New repository secret**, then `env: RESEND_API_KEY: ${{ secrets.RESEND_API_KEY }}` |

It is never needed by this test repository. Do not add it here.

---

## How the tests read email

The E2E stack sets `EMAIL_PROVIDER=smtp` and points it at
[Mailpit](https://mailpit.axllent.org/) — an SMTP server that accepts everything,
delivers nothing onward, and exposes what it caught over a REST API.

```
artra ──SMTP:1025──► mailpit ──HTTP:8025──► the test suite
```

`MailpitClient` waits for a message *to a specific recipient, sent after a
specific instant*, then pulls the code or the link out of it with a regex. Both
halves matter:

- **The recipient**, because every test that needs email generates its own
  address (`TestData.uniqueEmail`) — that is what lets three browsers run these
  flows concurrently without reading each other's codes.
- **The instant**, because several flows send more than one message to the same
  address. Without a lower bound, "resend the code" happily reads the code it
  already used and fails with *invalid code* against an application that behaved
  perfectly.

Nothing in the suite ever clears the mailbox. A shared "empty the inbox" step is
exactly what makes an email suite unusable in parallel: it throws away another
test's message a moment before that test looks for it.

You can watch it happen: while the stack is up, the captured mail is at
<http://localhost:8025>.

### Why this and not a real provider

Three reasons, in order of importance:

1. **Determinism.** A test that waits on a third party's delivery time is a test
   that fails on their bad day.
2. **No credential in CI.** A pull request from a fork can run the full suite
   without being handed an API key.
3. **It tests the real path.** The application composes the message, hands it to
   an SMTP server, and the suite reads what was actually delivered — headers,
   body and all. The only thing stubbed is the network hop to the internet.

## Verifying the Resend path itself

The E2E suite deliberately does not exercise Resend, because doing so would send
real mail from CI. To check the production configuration by hand:

```bash
cd ../Artra
EMAIL_PROVIDER=resend \
RESEND_API_KEY=<your key> \
EMAIL_FROM='Artra <no-reply@your-verified-domain>' \
node -e "
  import('./src/routes/api/lib/email/index.js').then(async ({ send_email }) => {
    console.log(await send_email('you@example.com', 'Artra test', '<p>hello</p>', 'hello'))
  })
"
```

A `{ status: 200 }` means it went out. A `{ status: 500 }` prints the provider's
own reason above it — an unverified sender domain is the usual one.
