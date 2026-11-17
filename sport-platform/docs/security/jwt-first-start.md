# JWT: first-start configuration

The JWT signing key is now decoded and validated while JwtService is constructed,
not at the first login request. Missing, malformed or short keys prevent service
construction. Diagnostics do not include the supplied secret or the decoder cause.
The configured value must be standard Base64 with at least 32 decoded bytes.
The key is retained for the lifetime of that service instance.

This validates encoding and length, not entropy or prior disclosure. Generate a
fresh random key for each environment. Never use the deterministic keys in tests
for a running server. Keys from repository history are compromised if reused.

## Generate an ephemeral key for a local smoke test (PowerShell)

```powershell
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($keyBytes)
    $env:APP_SECURITY_JWT_SECRET = [Convert]::ToBase64String($keyBytes)
    $env:JWT_SECRET = $env:APP_SECURITY_JWT_SECRET
} finally {
    $rng.Dispose()
    [Array]::Clear($keyBytes, 0, $keyBytes.Length)
    Remove-Variable keyBytes, rng
}
```

This prints no key. These environment variables apply to child processes started
from this PowerShell session. They do not modify an already running application.
APP_SECURITY_JWT_SECRET corresponds to app.security.jwt.secret; JWT_SECRET is
also set for compatibility with configuration placeholders.

Do not regenerate the key on every server restart. For deployment, generate it
once and store it in the deployment's protected secret configuration. Never
paste the value into chat, logs, screenshots, git, or a public example file.
Changing a key invalidates previously issued tokens; log in again afterward.
The local .env file and deployment secret storage are intentionally not edited.

## Scope

This patch does not change token claims, roles, issuer verification, expiration
policy, refresh/revocation, access control, admin creation, migrations, or data.
It does not replace inspection of JwtProperties, JwtConfig and existing tests.
No development fallback key is introduced. Review existing test configuration
if a previously latent invalid secret now causes test-context startup to fail.

## Checks

mvn "-Dtest=JwtSigningKeyConfigurationTest,ArchitectureTest,ModulithArchitectureTest" test

Then run the full backend suite before closing the audit item. The 13 new tests
cover initialization, key lengths, diagnostics, parsing, key reuse, signature
rejection and expiration mapping. JVM/Spring/database tests were not executed
in the patch-generation environment.
