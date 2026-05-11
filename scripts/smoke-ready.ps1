# Полная проверка готовности API без ручного перебора (PowerShell).
# Условия: PostgreSQL, Redis, NATS, MinIO, Keycloak; запущен core-api (например run-core-api-local.ps1).
# Выход 0 при успехе всех шагов.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    # Требовать в ответе health/ready: redis_ok и nats_ok = true (полный стек, не только PostgreSQL).
    [switch]$StrictDependencies
)
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\lib\SmokeAdminUi.ps1"

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

Write-Host "GET $BaseUrl/api/v1/health ..." -ForegroundColor Cyan
$h = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health" -Method Get
if (-not $h.status) { Fail "health" }

Write-Host "GET $BaseUrl/api/v1/health/ready ..." -ForegroundColor Cyan
try {
    $rd = Invoke-RestMethod -Uri "$BaseUrl/api/v1/health/ready" -Method Get
    if (-not $rd.database_ok) { Fail "database_ok=false" }
    if ($StrictDependencies) {
        if ($rd.redis_ok -ne $true) { Fail "redis_ok не true (ключ -StrictDependencies только при поднятом Redis)" }
        if ($rd.nats_ok -ne $true) { Fail "nats_ok не true (ключ -StrictDependencies только при доступном NATS)" }
    }
} catch {
    Fail "health/ready: $_"
}

Write-Host "GET $BaseUrl/api/v1/media/capabilities ..." -ForegroundColor Cyan
$cap = Invoke-RestMethod -Uri "$BaseUrl/api/v1/media/capabilities" -Method Get
if (-not $cap.max_upload_bytes) { Fail "capabilities" }

try {
    Test-SmokeAdminConsoleRedirect -BaseUrl $BaseUrl
} catch {
    Fail $_
}

Write-Host "POST login + admin/session ..." -ForegroundColor Cyan
$loginBody = @{ username = $User; password = $Pass } | ConvertTo-Json
try {
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
} catch {
    Fail "login: $_"
}
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { Fail "no access token" }

$hdr = @{ Authorization = "Bearer $token" }

try {
    Test-SmokeAdminStaticPage -BaseUrl $BaseUrl
} catch {
    Fail $_
}
try {
    $null = Test-SmokeAdminUiApi -BaseUrl $BaseUrl -AuthHeaders $hdr
} catch {
    Fail $_
}

$sess = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/session" -Headers $hdr -Method Get
if (-not $sess.user_id) { Fail "admin session (need user_id)" }

$msg = "[OK] Стек готов: health, ready, media/capabilities, admin/console→/admin/, /admin/, admin/ui, JWT admin/session"
if ($StrictDependencies) { $msg += " (StrictDependencies: Redis+NATS)" }
Write-Host $msg -ForegroundColor Green
exit 0
