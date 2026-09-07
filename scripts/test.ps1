<#
.SYNOPSIS
  Runs the backend's JUnit5 + Mockito unit tests on Windows.

.EXAMPLE
  .\scripts\test.ps1
  # if PowerShell blocks script execution:
  powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$mvn = & (Join-Path $PSScriptRoot "ensure-maven.ps1")
if (-not $mvn) {
    Write-Host "Could not resolve a Maven executable — see errors above." -ForegroundColor Red
    exit 1
}

& $mvn test
exit $LASTEXITCODE
