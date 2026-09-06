<#
.SYNOPSIS
  Smoke-tests the five EventZone auth APIs against a running backend.

.DESCRIPTION
  Covers: login as Admin / Organiser / Attendee, register, and logout,
  plus the main negative cases. Start the backend first (.\scripts\run.ps1).

.EXAMPLE
  .\scripts\smoke-test.ps1
  .\scripts\smoke-test.ps1 -BaseUrl http://localhost:8081
#>
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:Passed = 0
$script:Failed = 0

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token
    )
    $headers = @{ "Content-Type" = "application/json" }
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
    }
}

function Assert-Status {
    param([string]$Name, [int]$Expected, $Result)
    if ($Result.Status -eq $Expected) {
        Write-Host "  PASS  $Name (HTTP $($Result.Status))" -ForegroundColor Green
        $script:Passed++
    }
    else {
        Write-Host "  FAIL  $Name (expected $Expected, got $($Result.Status)) $($Result.Content)" -ForegroundColor Red
        $script:Failed++
    }
}

Write-Host "EventZone auth smoke test against $BaseUrl" -ForegroundColor Cyan
Write-Host ""

# 1) Login as Admin
$admin = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "admin@eventzone.com"; password = "Password@123" }
Assert-Status "1. Login as Admin" 200 $admin

# 2) Login as Organiser
$organiser = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "organiser1@eventzone.com"; password = "Password@123" }
Assert-Status "2. Login as Organiser" 200 $organiser

# 3) Login as Attendee
$attendee = Invoke-Api -Method POST -Path "/api/auth/login" -Body @{ email = "attendee1@eventzone.com"; password = "Password@123" }
Assert-Status "3. Login as Attendee" 200 $attendee
$attendeeToken = if ($attendee.Content) { ($attendee.Content | ConvertFrom-Json).token } else { $null }

# 4) Register
$newEmail = "user$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())@eventzone.com"
$register = Invoke-Api -Method POST -Path "/api/auth/register" -Body @{ email = $newEmail; password = "Password@123"; name = "New Attendee" }
Assert-Status "4. Register" 201 $register

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
