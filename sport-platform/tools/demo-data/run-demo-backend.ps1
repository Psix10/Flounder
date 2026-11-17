param(
    [Parameter(Mandatory = $true)]
    [guid] $EventId
)
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location -LiteralPath $root
$arguments = "--app.demo-data.enabled=true --app.demo-data.allowed-event-ids=$EventId"
if (Test-Path -LiteralPath (Join-Path $root "mvnw.cmd")) {
    & (Join-Path $root "mvnw.cmd") spring-boot:run "-Dspring-boot.run.arguments=$arguments"
} else {
    & mvn spring-boot:run "-Dspring-boot.run.arguments=$arguments"
}
if ($LASTEXITCODE -ne 0) { throw "Backend process exited with code $LASTEXITCODE" }
