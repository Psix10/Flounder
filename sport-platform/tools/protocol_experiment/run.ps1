param(
    [ValidateRange(8, 2000)][int]$Size = 60,
    [ValidateRange(1, 100)][int]$Repetitions = 3,
    [long]$Seed = 424242,
    [string]$Python = "python"
)
$ErrorActionPreference = "Stop"
$project = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Push-Location $project
try {
    & $Python -m unittest discover -s tools/protocol_experiment -p test_validate.py
    if ($LASTEXITCODE -ne 0) { throw "Validator self-tests failed" }
    $runs = Join-Path $project "target\protocol-experiments"
    $before = @()
    if (Test-Path $runs) { $before = @(Get-ChildItem $runs -Directory | Select-Object -ExpandProperty FullName) }
    & mvn test "-Dtest=ProtocolExperimentScenario" "-Dexperiment.size=$Size" "-Dexperiment.repetitions=$Repetitions" "-Dexperiment.seed=$Seed"
    if ($LASTEXITCODE -ne 0) { throw "Java experiment failed; validation was not run" }
    $fresh = @(Get-ChildItem $runs -Directory | Where-Object {
        $_.FullName -notin $before -and (Test-Path (Join-Path $_.FullName "manifest.json"))
    })
    if ($fresh.Count -ne 1) { throw "Expected exactly one new complete experiment directory; found $($fresh.Count)" }
    & $Python tools/protocol_experiment/validate.py --run ($fresh[0].FullName)
    if ($LASTEXITCODE -ne 0) { throw "Independent validation failed; inspect validation-report.json" }
    Write-Host "Validated experiment:" $fresh[0].FullName
    Write-Host "Reports: validation-report.json and summary.csv"
} finally {
    Pop-Location
}
