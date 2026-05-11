# Быстрая проверка стека korus-web (Java web-client за nginx-lb).
# Условия: docker compose в каталоге korus-web поднят, порт lb по умолчанию 9088.
param(
    [string]$WebBaseUrl = "http://localhost:9088",
    # Проверить цепочку lb → web-client → core-api (нужен доступный WEB_CLIENT_API_UPSTREAM).
    [switch]$CheckApi
)
$ErrorActionPreference = "Stop"

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

$healthUrl = "$WebBaseUrl/health"
Write-Host "GET $healthUrl ..." -ForegroundColor Cyan
try {
    $h = Invoke-WebRequest -Uri $healthUrl -Method Get -UseBasicParsing
    if ($h.StatusCode -ne 200) { Fail "health status $($h.StatusCode)" }
    if ($h.Content.Trim() -ne "ok") { Fail "health body expected 'ok', got: $($h.Content)" }
} catch {
    Fail "health: $_"
}

$rootUrl = "$WebBaseUrl/"
Write-Host "GET $rootUrl ..." -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri $rootUrl -Method Get -UseBasicParsing
    if ($r.StatusCode -ne 200) { Fail "root status $($r.StatusCode)" }
    if ($r.Content -notmatch "Korus Messenger") { Fail "root HTML missing title marker" }
} catch {
    Fail "root: $_"
}

$envJs = "$WebBaseUrl/web-client-env.js"
Write-Host "GET $envJs ..." -ForegroundColor Cyan
try {
    $e = Invoke-WebRequest -Uri $envJs -Method Get -UseBasicParsing
    if ($e.StatusCode -ne 200) { Fail "web-client-env.js status $($e.StatusCode)" }
    if ($e.Content -notmatch "__WEB_CLIENT__") { Fail "web-client-env.js missing __WEB_CLIENT__" }
} catch {
    Fail "web-client-env.js: $_"
}

if ($CheckApi) {
    $apiUrl = "$WebBaseUrl/api/v1/health"
    Write-Host "GET $apiUrl ..." -ForegroundColor Cyan
    try {
        $j = Invoke-RestMethod -Uri $apiUrl -Method Get
        if (-not $j.status) { Fail "API health JSON missing status" }
    } catch {
        Fail "API via proxy: $_"
    }
}

Write-Host "[OK] korus-web smoke ($WebBaseUrl)$(if ($CheckApi) { ' (+ API proxy)' })" -ForegroundColor Green
