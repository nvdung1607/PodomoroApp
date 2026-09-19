param(
    [Parameter(Mandatory = $true)][string]$AdbPath,
    [Parameter(Mandatory = $true)][string]$Serial
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
function Invoke-Adb([string[]]$Arguments) {
    & $AdbPath -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($Arguments[0])" }
}
Invoke-Adb @('install', '-r', (Join-Path $repoRoot 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('install', '-r', (Join-Path $repoRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
# Runtime grants are not changed here. Grant exact alarm access in Settings before
# running PlatformTest to avoid a skipped exact-alarm check.
$result = & $AdbPath -s $Serial shell am instrument -w -r -e class 'com.trustMePro.podomoroapp.RepositoryTest,com.trustMePro.podomoroapp.PlatformTest,com.trustMePro.podomoroapp.UiFlowTest,com.trustMePro.podomoroapp.PresentationTest' com.trustMePro.podomoroapp.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(\d+ tests\)') { throw 'Instrumentation did not report a successful run.' }
Write-Output 'Check for skipped exact-alarm tests; a skip is not evidence of platform coverage.'
