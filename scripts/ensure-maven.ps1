<#
.SYNOPSIS
  Prints the path to a usable mvn.cmd, installing Maven for you if needed.

.DESCRIPTION
  If `mvn` is already on PATH, that's what gets used. Otherwise this downloads a
  self-contained copy of Apache Maven into a `.maven-local` folder next to the
  project (one-time, ~10 MB) so you don't have to separately install Maven system-
  wide just to build this project. Requires only that Java is installed and that
  you have internet access (this only needs to run once; after that .maven-local
  already has everything).

  IMPORTANT: this script deliberately never calls `exit` — it's meant to be called
  with `&` from other scripts in the same PowerShell session (run.ps1, test.ps1,
  smoke-test.ps1), and `exit` inside a called script propagates up and terminates
  the *caller* too, which would break their cleanup logic. On failure this prints
  an error and returns nothing (empty/`$null`); callers must check for that.

.EXAMPLE
  $mvn = & .\scripts\ensure-maven.ps1
  if (-not $mvn) { Write-Host "Maven unavailable, see errors above" -ForegroundColor Red; return }
  & $mvn -q -DskipTests package
#>

$ErrorActionPreference = "Stop"

$mavenVersion  = "3.9.9"
$repoRoot      = Split-Path -Parent $PSScriptRoot
$localMavenDir = Join-Path $repoRoot ".maven-local"
$localMavenHome = Join-Path $localMavenDir "apache-maven-$mavenVersion"
$localMvnCmd   = Join-Path $localMavenHome "bin\mvn.cmd"

$systemMvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($systemMvn) {
    Write-Output $systemMvn.Source
    return
}

if (Test-Path $localMvnCmd) {
    Write-Output $localMvnCmd
    return
}

Write-Host "Maven not found on PATH. Downloading a local copy of Apache Maven $mavenVersion into .maven-local\ (one-time, ~10 MB)..." -ForegroundColor Yellow

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host ""
    Write-Host "ERROR: Java was not found on PATH either. This project needs Java 17 or newer (whatever pom.xml currently targets)." -ForegroundColor Red
    Write-Host "Install it (e.g. https://adoptium.net/temurin/releases/), or:" -ForegroundColor Red
    Write-Host "    winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Red
    Write-Host "then open a NEW PowerShell window (so PATH updates take effect) and try again." -ForegroundColor Red
    return
}

New-Item -ItemType Directory -Force -Path $localMavenDir | Out-Null
$zipUrl  = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$zipPath = Join-Path $localMavenDir "apache-maven-$mavenVersion-bin.zip"

try {
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath
} catch {
    Write-Host "ERROR: could not download Maven from $zipUrl" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "If your machine is behind a proxy/firewall, install Maven manually instead: https://maven.apache.org/download.cgi" -ForegroundColor Red
    return
}

Expand-Archive -Path $zipPath -DestinationPath $localMavenDir -Force
Remove-Item $zipPath -ErrorAction SilentlyContinue

if (-not (Test-Path $localMvnCmd)) {
    Write-Host "ERROR: expected $localMvnCmd after extracting but it's not there." -ForegroundColor Red
    return
}

Write-Host "Maven $mavenVersion ready at $localMavenHome" -ForegroundColor Green
Write-Output $localMvnCmd
