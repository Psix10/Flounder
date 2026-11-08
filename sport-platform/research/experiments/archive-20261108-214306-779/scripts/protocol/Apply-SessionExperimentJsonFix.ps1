$ErrorActionPreference = 'Stop'
$path = Join-Path (Get-Location) 'src/test/java/com/acme/sportplatform/integration/protocol/SessionProtocolExperimentTest.java'
if (-not (Test-Path $path)) { throw 'Run this script from the sport-platform root after installing step 15.' }
$helper = Join-Path (Split-Path $path) 'SessionProtocolSnapshotJsonAssertions.java'
if (-not (Test-Path $helper)) { throw 'Copy the helper Java file from this patch before applying it.' }
$text = [System.IO.File]::ReadAllText($path)
$oldProtocol = 'var protocol=json.readTree(jdbc.queryForObject("SELECT protocol_json::text FROM session_protocol_snapshots WHERE id=?",String.class,result.snapshotId()));'
$newProtocol = 'var protocol=SessionProtocolSnapshotJsonAssertions.parse(json,jdbc.queryForObject("SELECT protocol_json::text FROM session_protocol_snapshots WHERE id=?",String.class,result.snapshotId()));'
$oldAssertion = 'assertEquals(json.readTree(json.writeValueAsString(result)),protocol);'
$newAssertion = 'SessionProtocolSnapshotJsonAssertions.assertSnapshotMatches(json,result,protocol);'
if ($text.Contains($newProtocol) -and $text.Contains($newAssertion)) {
    Write-Host 'JSON snapshot comparison fix is already applied.'
    return
}
if (-not $text.Contains($oldProtocol) -or -not $text.Contains($oldAssertion)) {
    throw 'Expected source anchors were not found. No files changed. Apply the two documented replacements manually or provide the current validate method.'
}
if (($text.Split(@($oldProtocol), [System.StringSplitOptions]::None).Count -ne 2) -or
    ($text.Split(@($oldAssertion), [System.StringSplitOptions]::None).Count -ne 2)) {
    throw 'Source anchor is not unique. No files changed.'
}
$updated = $text.Replace($oldProtocol,$newProtocol).Replace($oldAssertion,$newAssertion)
$backup = $path + '.before-numeric-fix-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.bak'
Copy-Item -LiteralPath $path -Destination $backup
[System.IO.File]::WriteAllText($path,$updated,[System.Text.UTF8Encoding]::new($false))
Write-Host 'Applied two test-only replacements. Backup:' $backup
