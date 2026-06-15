# Smoke korus-web stack (Java web-client behind nginx-lb). Windows operator path; CI: smoke-korus-web.sh.
# Help: .\scripts\smoke-korus-web.ps1 -Help
param(
    [string]$WebBaseUrl = "http://localhost:9088",
    # Check lb -> web-client -> core-api (needs WEB_CLIENT_API_UPSTREAM).
    [switch]$CheckApi,
    # QEMU: wsUrl in web-client-env.js should use ws:// and host LAN IP (not 127.0.0.1 inside guest-only URL).
    [string]$ExpectWsHost = "",
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-korus-web.ps1 [-WebBaseUrl <url>] [-CheckApi] [-Help]"
    Write-Host "  Checks /health, / HTML, /web-client-env.js (wsUrl + iceServersJson)."
    Write-Host "  Default URL: http://localhost:9088 (QEMU web-VM: http://127.0.0.1:19088)."
    Write-Host "  Linux/macOS: ./scripts/smoke-korus-web.sh --help"
    exit 0
}

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
    if ($e.Content -notmatch 'wsUrl\s*:') { Fail "web-client-env.js missing wsUrl" }
    if ($WebBaseUrl -match ':19088' -and $e.Content -notmatch 'ws://') {
        Fail "QEMU web-client-env.js wsUrl should be ws:// for browser WS on host port 19088"
    }
    if ($ExpectWsHost) {
        if ($e.Content -notmatch [regex]::Escape($ExpectWsHost)) {
            Fail "web-client-env.js wsUrl missing expected host $ExpectWsHost"
        }
    }
    if ($e.Content -notmatch 'iceServersJson\s*:') { Fail "web-client-env.js missing iceServersJson" }
    if ($e.Content -notmatch 'iceServersJson\s*:\s*(null|")') {
        Fail "web-client-env.js iceServersJson must be null or a JSON string"
    }
    if ($e.Content -notmatch 'vapidPublicKey\s*:') {
        Fail "web-client-env.js missing vapidPublicKey"
    }
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
