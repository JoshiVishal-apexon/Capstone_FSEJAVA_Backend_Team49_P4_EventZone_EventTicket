<#
.SYNOPSIS
  Runs the EventZone backend locally on Windows (http://localhost:8080).

.DESCRIPTION
  Uses Maven from PATH if present, otherwise auto-downloads a local copy first
  (see ensure-maven.ps1) — either way, no manual Maven install is required.

.EXAMPLE
  .\scripts\run.ps1
  # if PowerShell blocks script execution:
  powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$mvn = & (Join-Path $PSScriptRoot "ensure-maven.ps1")
if (-not $mvn) {
    Write-Host "Could not resolve a Maven executable — see errors above." -ForegroundColor Red
    exit 1
}

Write-Host "Starting EventZone backend (Ctrl+C to stop)..." -ForegroundColor Cyan
& $mvn spring-boot:run
