<#
.SYNOPSIS
  Smoke-tests the five EventZone auth APIs against a running backend.
  EventZone backend smoke test — Windows PowerShell native version.

.DESCRIPTION
  Builds the app, starts it, and walks through the full happy-path flow via HTTP
  (register/login, browse events, book a ticket, cancel it, organiser + admin
  actions), asserting the HTTP status code at every step. This is the Windows
  equivalent of scripts/smoke-test.sh (which needs bash/WSL/Git Bash to run) —
  use this one if you're in plain Windows PowerShell.
  Covers: login as Admin / Organiser / Attendee, register, and logout,
  plus the main negative cases. Start the backend first (.\scripts\run.ps1).

.EXAMPLE
  .\scripts\smoke-test.ps1
  .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8081
  # if PowerShell blocks script execution (common on a fresh machine):
  powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
#>
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$baseUrl = "http://localhost:8080"
$logFile = Join-Path $env:TEMP "eventzone-smoke-test.log"
$script:pass = 0
$script:fail = 0
$javaProc = $null
$script:Passed = 0
$script:Failed = 0

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
        [string]$Url,
        [string]$Body = $null,
        [string]$Token = $null
    )
    $headers = @{ "Content-Type" = "application/json" }
    $headers = @{}
    if ($Body) { $headers["Content-Type"] = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    # Note: -SkipHttpErrorCheck is PowerShell 7+ only, so non-2xx responses are
    # read out of the terminating error instead — this works on 5.1 and 7+.
    $params = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($Body) { $params["Body"] = ($Body | ConvertTo-Json -Compress) }

    try {
        if ($Body) {
            $resp = Invoke-WebRequest -Uri $Url -Method $Method -Headers $headers -Body $Body -UseBasicParsing -TimeoutSec 15
        } else {
            $resp = Invoke-WebRequest -Uri $Url -Method $Method -Headers $headers -UseBasicParsing -TimeoutSec 15
        }
        return [PSCustomObject]@{ StatusCode = [int]$resp.StatusCode; Content = $resp.Content }
    } catch {
        $ex = $_.Exception
        $statusCode = 0
        $response = Invoke-WebRequest @params
        $content = if ($response.Content) { $response.Content } else { "" }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Content = $content }
    }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $content = ""
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $content = $_.ErrorDetails.Message }
        elseif ($status -eq 0) { $content = $_.Exception.Message }
        return [pscustomobject]@{ Status = $status; Content = $content }
        if ($ex.Response) {
            try {
                # Windows PowerShell 5.1: System.Net.WebException / HttpWebResponse
                $statusCode = [int]$ex.Response.StatusCode
                $stream = $ex.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($stream)
                $content = $reader.ReadToEnd()
            } catch {
                try {
                    # PowerShell 7+: HttpResponseException / HttpResponseMessage
                    $statusCode = [int]$ex.Response.StatusCode
                    $content = $ex.Response.Content.ReadAsStringAsync().Result
                } catch { }
            }
        }
        return [PSCustomObject]@{ StatusCode = $statusCode; Content = $content }
    }
}

function Test-Check {
    param([string]$Desc, [int]$Expected, [int]$Actual)
    if ($Actual -eq $Expected) {
        Write-Host "  [PASS] $Desc (HTTP $Actual)" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  [FAIL] $Desc (expected HTTP $Expected, got $Actual)" -ForegroundColor Red
        $script:fail++
    }
}

function Get-Field {
    param([string]$Json, [string]$Key)
    try {
        $obj = $Json | ConvertFrom-Json
        return $obj.$Key
    } catch { return $null }
}

function Get-FirstId {
    param([string]$Json)
    try {
        $arr = $Json | ConvertFrom-Json
        if ($arr -and $arr.Count -gt 0) { return $arr[0].id }
        return $null
    } catch { return $null }
}

function Assert-Status {
    param([string]$Name, [int]$Expected, $Result)
    if ($Result.Status -eq $Expected) {
        Write-Host "  PASS  $Name (HTTP $($Result.Status))" -ForegroundColor Green
        $script:Passed++
$script:exitCode = 0

try {
    Write-Host "== 1. Building (mvn -q -DskipTests package) ==" -ForegroundColor Cyan
    $mvn = & (Join-Path $PSScriptRoot "ensure-maven.ps1")
    if (-not $mvn) {
        Write-Host "Could not resolve a Maven executable — see errors above." -ForegroundColor Red
        $script:exitCode = 1
        return
    }
    else {
        Write-Host "  FAIL  $Name (expected $Expected, got $($Result.Status)) $($Result.Content)" -ForegroundColor Red
        $script:Failed++
    & $mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed — fix compile errors before running the smoke test. See Maven output above." -ForegroundColor Red
        $script:exitCode = 1
        return
    }

    $jar = Get-ChildItem -Path (Join-Path $repoRoot "target") -Filter "eventzone-backend*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jar) {
        Write-Host "No jar found in target\ after build — aborting." -ForegroundColor Red
        $script:exitCode = 1
        return
    }

Write-Host "EventZone auth smoke test against $BaseUrl" -ForegroundColor Cyan
Write-Host ""
    Write-Host "== 2. Starting $($jar.Name) (log: $logFile) ==" -ForegroundColor Cyan
    if (Test-Path $logFile) { Remove-Item $logFile -Force }
    $javaProc = Start-Process -FilePath "java" -ArgumentList @("-jar", $jar.FullName) `
        -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" -PassThru -WindowStyle Hidden

    Write-Host "Waiting for the app to become ready on $baseUrl ..."
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        $r = Invoke-Api -Method GET -Url "$baseUrl/api/categories"
        if ($r.StatusCode -eq 200) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        Write-Host "App did not become ready within 60s. Last log lines:" -ForegroundColor Red
        if (Test-Path $logFile) { Get-Content $logFile -Tail 40 }
        if (Test-Path "$logFile.err") { Get-Content "$logFile.err" -Tail 40 }
        $script:exitCode = 1
        return
    }
    Write-Host "App is up." -ForegroundColor Green
    Write-Host ""

# 1) Login as Admin
$admin = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "admin@eventzone.com"; password = "Password@123" }
Assert-Status "1. Login as Admin" 200 $admin
    Write-Host "== 3. Walking the happy path ==" -ForegroundColor Cyan

    Write-Host "-- Auth --"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/auth/login" -Body '{"email":"admin@eventzone.com","password":"Password@123"}'
    Test-Check "Login as admin" 200 $r.StatusCode
    $adminToken = Get-Field $r.Content "token"

# 2) Login as Organiser
$organiser = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "organiser1@eventzone.com"; password = "Password@123" }
Assert-Status "2. Login as Organiser" 200 $organiser
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/auth/login" -Body '{"email":"organiser1@eventzone.com","password":"Password@123"}'
    Test-Check "Login as organiser" 200 $r.StatusCode
    $orgToken = Get-Field $r.Content "token"

# 3) Login as Attendee
$attendee = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "attendee1@eventzone.com"; password = "Password@123" }
Assert-Status "3. Login as Attendee" 200 $attendee
$attendeeToken = if ($attendee.Content) { ($attendee.Content | ConvertFrom-Json).token } else { $null }
    $rand = Get-Random
    $registerBody = "{`"email`":`"smoketest$rand@eventzone.com`",`"password`":`"Password@123`",`"name`":`"Smoke Test`"}"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/auth/register" -Body $registerBody
    Test-Check "Register new attendee" 201 $r.StatusCode

# 4) Register
$newEmail = "user$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())@eventzone.com"
$register = Invoke-Api -Method POST -Path "/api/auth/register" -Body @{ email = $newEmail; password = "Password@123"; name = "New Attendee" }
Assert-Status "4. Register" 201 $register
    $loginAttBody = "{`"email`":`"smoketest$rand@eventzone.com`",`"password`":`"Password@123`"}"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/auth/login" -Body $loginAttBody
    Test-Check "Login as newly-registered attendee" 200 $r.StatusCode
    $attendeeToken = Get-Field $r.Content "token"

    Write-Host "-- Categories & Events --"
    $r = Invoke-Api -Method GET -Url "$baseUrl/api/categories"
    Test-Check "List categories" 200 $r.StatusCode
    $categoryId = Get-FirstId $r.Content

    $r = Invoke-Api -Method GET -Url "$baseUrl/api/events"
    Test-Check "List events" 200 $r.StatusCode
    $eventId = Get-FirstId $r.Content

    $r = Invoke-Api -Method GET -Url "$baseUrl/api/events/$eventId"
    Test-Check "Get event detail" 200 $r.StatusCode
    $ticketCategoryId = $null
    try {
        $detail = $r.Content | ConvertFrom-Json
        $ticketCategoryId = $detail.ticketCategories[0].id
    } catch { }

    Write-Host "-- Organiser: create event + ticket category --"
    $createEventBody = "{`"title`":`"Smoke Test Concert`",`"description`":`"created by smoke-test.ps1`",`"eventDate`":`"2026-12-01T19:00:00`",`"venue`":`"Test Venue`",`"coverImageUrl`":`"https://example.com/x.jpg`",`"categoryId`":`"$categoryId`"}"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/events" -Body $createEventBody -Token $orgToken
    Test-Check "Organiser creates event" 201 $r.StatusCode
    $newEventId = Get-Field $r.Content "id"

    $createTcBody = '{"name":"General","price":500.00,"totalSeats":10}'
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/events/$newEventId/ticket-categories" -Body $createTcBody -Token $orgToken
    Test-Check "Organiser adds ticket category" 201 $r.StatusCode
    $newTcId = Get-Field $r.Content "id"

    Write-Host "-- Attendee: book + view + cancel --"
    $bookBody = "{`"ticketCategoryId`":`"$newTcId`",`"quantity`":2}"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/bookings" -Body $bookBody -Token $attendeeToken
    Test-Check "Attendee books 2 tickets" 201 $r.StatusCode
    $bookingId = Get-Field $r.Content "id"

    $overbookBody = "{`"ticketCategoryId`":`"$newTcId`",`"quantity`":50}"
    $r = Invoke-Api -Method POST -Url "$baseUrl/api/bookings" -Body $overbookBody -Token $attendeeToken
    Test-Check "Booking more than available seats is rejected" 400 $r.StatusCode

    $r = Invoke-Api -Method GET -Url "$baseUrl/api/bookings/mine" -Token $attendeeToken
    Test-Check "Attendee views their bookings" 200 $r.StatusCode

    $r = Invoke-Api -Method PUT -Url "$baseUrl/api/bookings/$bookingId/cancel" -Token $attendeeToken
    Test-Check "Attendee cancels their booking" 200 $r.StatusCode

    $r = Invoke-Api -Method PUT -Url "$baseUrl/api/bookings/$bookingId/cancel" -Token $attendeeToken
    Test-Check "Cancelling an already-cancelled booking is rejected" 400 $r.StatusCode

    Write-Host "-- Authorization checks --"
    $r = Invoke-Api -Method GET -Url "$baseUrl/api/bookings/mine"
    Test-Check "Bookings without a token is rejected" 401 $r.StatusCode

    $r = Invoke-Api -Method POST -Url "$baseUrl/api/admin/categories" -Body '{"name":"Should Fail"}' -Token $attendeeToken
    Test-Check "Attendee cannot create a category (ADMIN only)" 403 $r.StatusCode

    Write-Host "-- Organiser dashboard & Admin --"
    $r = Invoke-Api -Method GET -Url "$baseUrl/api/organiser/events" -Token $orgToken
    Test-Check "Organiser views their dashboard" 200 $r.StatusCode

    $r = Invoke-Api -Method PUT -Url "$baseUrl/api/admin/events/$newEventId/deactivate" -Token $adminToken
    Test-Check "Admin deactivates the smoke-test event" 200 $r.StatusCode

    $r = Invoke-Api -Method PUT -Url "$baseUrl/api/admin/events/$newEventId/activate" -Token $adminToken
    Test-Check "Admin reactivates it" 200 $r.StatusCode

    $r = Invoke-Api -Method DELETE -Url "$baseUrl/api/ticket-categories/$newTcId" -Token $orgToken
    Test-Check "Organiser deletes the ticket category" 204 $r.StatusCode

    $r = Invoke-Api -Method DELETE -Url "$baseUrl/api/events/$newEventId" -Token $orgToken
    Test-Check "Organiser deletes the smoke-test event" 204 $r.StatusCode
# 5) Logout
$logout = Invoke-Api -Method POST -Path "/api/auth/logout" -Token $attendeeToken
Assert-Status "5. Logout" 204 $logout

Write-Host ""
Write-Host "Negative cases" -ForegroundColor Cyan
Assert-Status "Login with wrong password -> 401" 401 (Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "admin@eventzone.com"; password = "wrong" })
Assert-Status "Register duplicate email -> 409" 409 (Invoke-Api -Method POST -Path "/api/auth/register" -Body @{ email = "attendee1@eventzone.com"; password = "Password@123"; name = "Dup" })
Assert-Status "Register short password -> 400" 400 (Invoke-Api -Method POST -Path "/api/auth/register" -Body @{ email = "shorty@eventzone.com"; password = "123"; name = "Shorty" })

Write-Host ""
Write-Host "Passed: $script:Passed  Failed: $script:Failed" -ForegroundColor $(if ($script:Failed -eq 0) { "Green" } else { "Red" })
exit $(if ($script:Failed -eq 0) { 0 } else { 1 })
    Write-Host ""
    Write-Host "== Result: $script:pass passed, $script:fail failed ==" -ForegroundColor Cyan
    if ($script:fail -ne 0) {
        Write-Host "Backend log at $logFile" -ForegroundColor Yellow
        $script:exitCode = 1
    }
}
finally {
    if ($javaProc -and -not $javaProc.HasExited) {
        Write-Host ""
        Write-Host "Stopping backend (pid $($javaProc.Id))..." -ForegroundColor Cyan
        Stop-Process -Id $javaProc.Id -Force -ErrorAction SilentlyContinue
    }
}

exit $script:exitCode
