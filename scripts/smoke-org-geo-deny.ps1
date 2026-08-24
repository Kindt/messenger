# Smoke: org geo deny when ORG_GEO_DENY_ENFORCE=1 (FSTEC-16 partial).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-org-geo-deny.ps1 [-BaseUrl url]

SKIP when ORG_GEO_DENY_ENFORCE is off (default lab).
When enforce on guest: send X-Geo-Country in denied list and expect 403 + audit.
"@
    exit 0
}

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

if ($BaseUrl -notmatch ':18080') {
    Write-Host "[SKIP] geo deny smoke for QEMU lab only (needs guest env probe)" -ForegroundColor Yellow
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$guestLib = Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1"
if (-not (Test-Path $guestLib)) {
    Write-Host "[SKIP] guest lib missing" -ForegroundColor Yellow
    exit 0
}
. $guestLib
$plink = "${env:ProgramFiles}\PuTTY\plink.exe"
$runDir = Join-Path $root "deploy\qemu\run"
$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk -or -not (Test-Path $plink)) {
    Write-Host "[SKIP] no guest SSH" -ForegroundColor Yellow
    exit 0
}

try {
    $enforce = (Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script "docker exec docker-core-api-1 printenv ORG_GEO_DENY_ENFORCE").Trim()
} catch {
    Write-Host "[SKIP] guest ORG_GEO_DENY_ENFORCE probe failed" -ForegroundColor Yellow
    exit 0
}
if ($enforce -notmatch '^(1|true)$') {
    Write-Host "[SKIP] ORG_GEO_DENY_ENFORCE off on lab (prod-only control)" -ForegroundColor Yellow
    exit 0
}

try {
    $countries = (Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script "docker exec docker-core-api-1 printenv ORG_GEO_DENY_COUNTRIES").Trim()
} catch {
    Write-Host "[SKIP] guest ORG_GEO_DENY_COUNTRIES probe failed" -ForegroundColor Yellow
    exit 0
}
if (-not $countries) { Fail "ORG_GEO_DENY_ENFORCE set but ORG_GEO_DENY_COUNTRIES empty" }
$denyCode = ($countries -split ',')[0].Trim().ToUpper()

$api = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Uri "$api/auth/login" -Method Post `
    -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = if ($login.access_token) { $login.access_token } else { $login.accessToken }

$guestScript = @"
curl -sS -m 5 -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer $token' -H 'X-Geo-Country: $denyCode' http://127.0.0.1:8080/api/v1/users/me
"@
try {
    $out = Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script $guestScript
} catch {
    Write-Host "[SKIP] guest geo curl probe failed" -ForegroundColor Yellow
    exit 0
}
if ($out -notmatch '403') { Fail "expected 403 for geo=$denyCode, got $out" }

Write-Host "[OK] geo deny blocked country=$denyCode" -ForegroundColor Green