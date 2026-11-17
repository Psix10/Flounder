# First platform administrator (MB-01)

## Behavior

Bootstrap is disabled unless app.bootstrap.admin.enabled=true. Enable it only
for the first installation, with app.bootstrap.admin.email and
app.bootstrap.admin.password supplied through the deployment environment.

Environment variable names:

- APP_BOOTSTRAP_ADMIN_ENABLED
- APP_BOOTSTRAP_ADMIN_EMAIL
- APP_BOOTSTRAP_ADMIN_PASSWORD

The password must contain at least 12 Unicode code points, no control characters,
and no more than 72 UTF-8 bytes (the current encoder is BCrypt). It is never
trimmed. Email is trimmed and lowercased for a newly created account. Existing
email spelling is never changed; the current login still uses exact email lookup.

Creation of an active account and its GLOBAL platform_admin role takes place in
one transaction, protected by a PostgreSQL transaction-scoped advisory lock
shared by bootstrap executions. Normal registration/role-management operations
are not covered by that bootstrap-only lock. Unique constraints remain active.
No public bootstrap endpoint exists. No participant profile with an invented
birth date is created. Current authentication and /me do not require a profile.

The bootstrap refuses to promote any ordinary existing account, reactivate an
inactive configured account, or reset any configured account's password. If the
chosen existing active global administrator has a matching password, startup is
idempotent and no user/role row is rewritten. A different existing administrator
prevents creation of another first administrator. Bootstrap is not a recovery or
password-rotation mechanism. Use the normal administration procedure afterward.

The ONLY exception is the exact original V002 placeholder: fixed UUID, original
email, original placeholder hash and active/inactive status must all match. It
can be explicitly initialized if no configured active global admin exists.

V035 disables only the unchanged original active placeholder. It runs through
Flyway at next application startup even when bootstrap is disabled. It does not
delete the old user/profile/assignments or change a working password. V002 is not
edited. Existing events, operator assignments and finalized results are untouched.
The installed files alone do not change any database; startup/migration does.

## First start: PowerShell environment

Set a fresh persistent JWT key through the existing deployment configuration.
Do not regenerate a deployed JWT key on every restart.

Set the email locally, then read the password without adding it to command history:

```powershell
$env:APP_BOOTSTRAP_ADMIN_EMAIL = Read-Host 'First administrator email'
$secure = Read-Host 'First administrator password (12+ characters, <=72 UTF-8 bytes)' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:APP_BOOTSTRAP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    Remove-Variable ptr, secure
}
$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'true'
```

Start the application from this PowerShell, using the project's normal startup
command and database. Never point a clean-install smoke test at the existing demo
or production database. Do not output environment variables or paste the password.
The log reports only CREATED / INITIALIZED_LEGACY / ALREADY_CONFIGURED.

Verify login and the platform administrator role. Once the bootstrap start has
completed successfully, remove the secret and disable bootstrap in the deployment
configuration for subsequent starts. In the current PowerShell:

```powershell
$env:APP_BOOTSTRAP_ADMIN_ENABLED = 'false'
Remove-Item Env:APP_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:APP_BOOTSTRAP_ADMIN_EMAIL -ErrorAction SilentlyContinue
```

Removing variables from the parent shell does not remove them from an already
running child process. Restart with bootstrap disabled to remove them from the
new server process as well. Environment variables are process secrets, not an
encrypted secret store. Use the deployment's protected secret mechanism.

## Verification

First run the unit/architecture checks:

mvn "-Dtest=AdminBootstrapCredentialsTest,AdminBootstrapServiceTest,AdminBootstrapRunnerTest,JwtSigningKeyConfigurationTest,ArchitectureTest,ModulithArchitectureTest" test

Then, with the existing project's PostgreSQL/Testcontainers test prerequisites:

mvn "-Dtest=AdminBootstrapIntegrationTest" test

The integration class uses the existing AbstractPostgresIntegrationTest,
explicitly disables the startup runner, and rolls test changes back. Its setup
temporarily deactivates test administrators within that rolled-back transaction.
It checks real hashing, authentication/token issuance, repeat initialization,
refusal to promote an ordinary account, existing-admin protection, legacy upgrade
and migration preservation. It does not prove concurrent startup behavior or
HTTP login on a deployed clean installation: those remain smoke checks.

Finally run the full backend suite. Do not mark MB-01 closed solely on unit tests.
The patch-generation environment has no Java, Maven, PostgreSQL or Docker; no
Java/database tests were run there. No environment/example secrets are committed.
