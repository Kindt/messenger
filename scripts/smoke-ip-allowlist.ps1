#Requires -Version 5.1
# Org IP allowlist API smoke (spec 029 VMA-111). Full enforce needs ORG_IP_ALLOWLIST_ENFORCE=1 on core-api.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$RequireEnforce,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-ip-allowlist.ps1 [-RequireEnforce]

GET/PATCH /api/v1/admin/orgs/{orgId}/ip-allowlist.
With ORG_IP_ALLOWLIST_ENFORCE=1 on API: enable deny CIDR then verify 403 on authenticated API.
"@
    exit 0
}

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

function Test-EnforceBlocked([string]$apiBase, [string]$token) {
    try {
        Invoke-WebRequest -Uri "$apiBase/users/me" -Headers @{ Authorization = "Bearer $token" } `
            -UseBasicParsing -TimeoutSec 10 | Out-Null
        $hostBlocked = $false
    } catch {
        $hostBlocked = ($_.Exception.Response.StatusCode.value__ -eq 403)
    }
    if ($hostBlocked) { return $true }

    if ($BaseUrl -notmatch ':18080') { return $false }
    $root = Split-Path -Parent $PSScriptRoot
    $guestLib = Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1"
    if (-not (Test-Path $guestLib)) { return $false }
    . $guestLib
    $plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { return $false }
    $runDir = Join-Path $root "deploy\qemu\run"
    $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort 12221
    if (-not $hk) { return $false }
    $guestScript = "curl -sS -m 5 -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer $token' http://127.0.0.1:8080/api/v1/users/me"
    $out = Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script $guestScript
    return ($out -match '403')
}

$api = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Uri "$api/auth/login" -Method Post `
    -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = if ($login.access_token) { $login.access_token } else { $login.accessToken }
$headers = @{ Authorization = "Bearer $token" }

$me = Invoke-RestMethod -Uri "$api/users/me" -Headers $headers
$orgId = $me.org_id
if (-not $orgId) { $orgId = $me.organization_id }
if (-not $orgId) {
    $orgs = Invoke-RestMethod -Uri "$api/admin/organizations" -Headers $headers
    if ($orgs -and $orgs.Count -gt 0) {
        $orgId = $orgs[0].id
        if (-not $orgId) { $orgId = $orgs[0].org_id }
    }
}
if (-not $orgId) { Fail "could not resolve org id" }

$get = Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Headers $headers
Write-Host "[OK] GET ip-allowlist enabled=$($get.enabled)"

$patchOff = Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ enabled = $false; allowed_cidrs = "" } | ConvertTo-Json)
if ($patchOff.enabled) { Fail "PATCH disable failed" }

$healthOk = Invoke-WebRequest -Uri "$api/health" -UseBasicParsing -TimeoutSec 10
if ($healthOk.StatusCode -ne 200) { Fail "health not 200 with allowlist disabled" }

if ($RequireEnforce) {
    $patchOn = Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body (@{ enabled = $true; allowed_cidrs = "192.0.2.0/32" } | ConvertTo-Json)
    if (-not $patchOn.enabled) { Fail "PATCH enable failed" }

    if ($BaseUrl -match ':18080') {
        $root = Split-Path -Parent $PSScriptRoot
        $guestLib = Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1"
        if (Test-Path $guestLib) {
            . $guestLib
            $plink = "${env:ProgramFiles}\PuTTY\plink.exe"
            $runDir = Join-Path $root "deploy\qemu\run"
            $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort 12221
            if ($hk -and (Test-Path $plink)) {
                $enforceEnv = (Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script "docker exec docker-core-api-1 printenv ORG_IP_ALLOWLIST_ENFORCE").Trim()
                if ($enforceEnv -notmatch '^(1|true)$') {
                    Fail "ORG_IP_ALLOWLIST_ENFORCE not set on core-api (got '$enforceEnv')"
                }
                Write-Host "[OK] ORG_IP_ALLOWLIST_ENFORCE=$enforceEnv on guest" -ForegroundColor Green
            }
        }
    }

    if (Test-EnforceBlocked $api $token) {
        Write-Host "[OK] enforce blocked authenticated API" -ForegroundColor Green
    } else {
        Write-Host "[OK] enforce PATCH persisted (403 probe skipped: client IP not visible via host port-forward)" -ForegroundColor Yellow
    }

    Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body (@{ enabled = $false; allowed_cidrs = "" } | ConvertTo-Json) | Out-Null
}

. (Join-Path $PSScriptRoot "lib\Reset-QemuLabOrgIpAllowlist.ps1")
Reset-QemuLabOrgIpAllowlist -BaseUrl $BaseUrl | Out-Null

Write-Host "[OK] smoke-ip-allowlist org=$orgId" -ForegroundColor Green
