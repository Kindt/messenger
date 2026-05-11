# Быстрая проверка: GET /api/v1/admin/console (303) → login → (опционально refresh) → admin/ui manifest+stats → (опционально logout) → GET /api/v1/admin/session
# Пример: .\scripts\smoke-auth.ps1
#         .\scripts\smoke-auth.ps1 -BaseUrl http://127.0.0.1:8080 -User csadmin -Pass csadmin
#         .\scripts\smoke-auth.ps1 -SkipRefresh   # только access token с login
#         .\scripts\smoke-auth.ps1 -SkipLogout     # не вызывать POST .../auth/logout
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [switch]$SkipRefresh,
    [switch]$SkipLogout
)
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\lib\SmokeAdminUi.ps1"

try {
    Test-SmokeAdminConsoleRedirect -BaseUrl $BaseUrl
} catch {
    Write-Host "admin console redirect: $_" -ForegroundColor Red
    exit 1
}

$loginUri = "$BaseUrl/api/v1/auth/login"
$refreshUri = "$BaseUrl/api/v1/auth/refresh"
$logoutUri = "$BaseUrl/api/v1/auth/logout"
$sessionUri = "$BaseUrl/api/v1/admin/session"

Write-Host "POST $loginUri (user=$User)..." -ForegroundColor Cyan
try {
    $loginBody = @{ username = $User; password = $Pass } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri $loginUri -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
} catch {
    Write-Host "Login failed: $_" -ForegroundColor Red
    exit 1
}

$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) {
    Write-Host "No access token in response." -ForegroundColor Red
    exit 1
}

$refreshTokenForLogout = $null
$rt = $login.refresh_token
if (-not $rt) { $rt = $login.refreshToken }
$refreshTokenForLogout = $rt

if (-not $SkipRefresh) {
    if ($rt) {
        Write-Host "POST $refreshUri ..." -ForegroundColor Cyan
        try {
            $refBody = @{ refresh_token = $rt } | ConvertTo-Json
            $ref = Invoke-RestMethod -Uri $refreshUri -Method Post -Body $refBody -ContentType "application/json; charset=utf-8"
            $t2 = $ref.access_token
            if (-not $t2) { $t2 = $ref.accessToken }
            if ($t2) {
                $token = $t2
                Write-Host "[OK] Refresh returned new access token" -ForegroundColor DarkGray
            }
            $nr = $ref.refresh_token
            if (-not $nr) { $nr = $ref.refreshToken }
            if ($nr) {
                $refreshTokenForLogout = $nr
            }
        } catch {
            Write-Host "Refresh failed (optional): $_" -ForegroundColor Yellow
        }
    }
}

try {
    $hdrUi = @{ Authorization = "Bearer $token" }
    $ui = Test-SmokeAdminUiApi -BaseUrl $BaseUrl -AuthHeaders $hdrUi
    Write-Host "[OK] admin/ui manifest api=$($ui.Manifest.api_version) ($($ui.SectionCount) sections), stats api_version=$($ui.Stats.api_version)" -ForegroundColor DarkGray
} catch {
    Write-Host "admin/ui failed: $_" -ForegroundColor Red
    exit 1
}

if (-not $SkipLogout -and $refreshTokenForLogout) {
    Write-Host "POST $logoutUri ..." -ForegroundColor Cyan
    $logoutBody = @{ refresh_token = $refreshTokenForLogout } | ConvertTo-Json
    try {
        $logoutResp = Invoke-WebRequest -Uri $logoutUri -Method Post -Body $logoutBody -ContentType "application/json; charset=utf-8" -UseBasicParsing
    } catch {
        Write-Host "Logout failed: $_" -ForegroundColor Red
        exit 1
    }
    if ($logoutResp.StatusCode -ne 204) {
        Write-Host "Logout expected 204, got $($logoutResp.StatusCode)" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Logout (204)" -ForegroundColor DarkGray
}

Write-Host "GET $sessionUri ..." -ForegroundColor Cyan
try {
    $headers = @{ Authorization = "Bearer $token" }
    $sess = Invoke-RestMethod -Uri $sessionUri -Method Get -Headers $headers
    $sess | ConvertTo-Json -Depth 5
    Write-Host "[OK] Admin session" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "Admin session failed (need realm role admin on user): $_" -ForegroundColor Red
    exit 1
}
