/**
 * Fills in the passwords for the fixture accounts created by 02-seed.sql.
 *
 * Artra hashes with Argon2id keyed by ARGON_SECRET, which Postgres cannot
 * compute - so the SQL seed creates the rows and this fills in the credential,
 * using the same algorithm and parameters as the application
 * (src/routes/api/auth/hash.js and the handlers that call it). Getting those
 * parameters wrong would produce accounts that exist but can never sign in,
 * which is a confusing way to spend an afternoon.
 *
 * Idempotent: it runs on every `up` and simply rewrites the hash, so rotating
 * SEED_USER_PASSWORD needs no database rebuild.
 *
 * Nothing here is a real credential. These accounts exist only inside the test
 * stack, and the password comes from the environment.
 */
import { argon2, randomBytes } from "node:crypto"
import pg from "pg"

const required = (name) => {
  const value = process.env[name]
  if (!value) {
    console.error(`[seed] ${name} is not set`)
    process.exit(1)
  }
  return value
}

const DATABASE_URL = required("DATABASE_URL")
const ARGON_SECRET = required("ARGON_SECRET")
const PASSWORD = required("SEED_USER_PASSWORD")

/** Same shape the application builds in login.js / register.js. */
const hash = (password, salt) =>
  new Promise((resolve, reject) =>
    argon2("argon2id", {
      message: password,
      nonce: salt,
      parallelism: 1,
      tagLength: 32,
      memory: 32768, // 32 MiB
      passes: 2,
      secret: ARGON_SECRET,
    }, (err, key) => (err ? reject(err) : resolve(key.toString("hex")))))

const client = new pg.Client({ connectionString: DATABASE_URL })

try {
  await client.connect()

  // Whoever the SQL seed created - identified by the reserved .test domain, so
  // this can never touch an account a test made or a real one.
  const { rows } = await client.query(
    `SELECT id, email FROM "User" WHERE email LIKE '%@artra.test' ORDER BY email`)

  if (rows.length === 0) {
    console.error("[seed] no fixture accounts found - did 02-seed.sql run?")
    process.exit(1)
  }

  for (const user of rows) {
    const salt = randomBytes(16)
    const key = await hash(PASSWORD, salt)
    await client.query(
      `UPDATE "User" SET password = $2, salt = $3, email_verified = true WHERE id = $1`,
      [user.id, key, salt.toString("hex")])
    console.log(`[seed] password set for ${user.email}`)
  }

  console.log(`[seed] ${rows.length} fixture account(s) ready`)
} catch (error) {
  console.error("[seed] failed:", error.message)
  process.exit(1)
} finally {
  await client.end().catch(() => {})
}
