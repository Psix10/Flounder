param(
    [ValidateSet(60,200,500)][int]$Entries = 60,
    [ValidateRange(1,1000)][int]$Runs = 3,
    [ValidateRange(0,20)][int]$Warmups = 1
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.\pom.xml')) { throw 'Run this script from the sport-platform project root.' }
$revision = 'UNRECORDED'
$dirty = 'UNRECORDED'
if (Get-Command git -ErrorAction SilentlyContinue) {
    $value = & git rev-parse HEAD 2>$null
    if ($LASTEXITCODE -eq 0) {
        $revision = [string]$value
        $status = @(& git status --porcelain)
        $dirty = [string]($status.Count -gt 0)
    }
}
$mavenArguments = @(
    'test',
    '-Dtest=SessionProtocolExperimentTest',
    '-Dprotocol.session.experiment=true',
    "-Dprotocol.session.experiment.entries=$Entries",
    "-Dprotocol.session.experiment.runs=$Runs",
    "-Dprotocol.session.experiment.warmups=$Warmups",
    "-Dprotocol.session.experiment.sourceRevision=$revision",
    "-Dprotocol.session.experiment.workingTreeDirty=$dirty"
)
& mvn @mavenArguments
if ($LASTEXITCODE -ne 0) { throw "Experiment failed; Maven exit code: $LASTEXITCODE" }
