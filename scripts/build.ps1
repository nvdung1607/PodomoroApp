param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest')
)

$ErrorActionPreference = 'Stop'
if (-not $JdkPath -or -not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to a working JDK 17+ or pass -JdkPath.'
}
$previousJavaHome = $env:JAVA_HOME
$buildExitCode = 1
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $env:JAVA_HOME = $JdkPath
    & (Join-Path $JdkPath 'bin/java.exe') -version
    if ($LASTEXITCODE -ne 0) { throw 'The selected Java runtime cannot start.' }
    & ./gradlew.bat @Tasks --console=plain
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
exit $buildExitCode
