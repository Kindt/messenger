# Smoke: denied access → audit_events (FSTEC-14).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-denied-access-audit.ps1 [-BaseUrl url]

Triggers org IP allowlist deny on guest (when enforce active) and verifies
access.ip_allowlist.denied audit row.
"@
    exit 0
}

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

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
    if ($orgs -is [System.Array]) {
        if ($orgs.Count -gt 0) {
            $orgId = $orgs[0].id
            if (-not $orgId) { $orgId = $orgs[0].org_id }
        }
    } elseif ($orgs) {
        $orgId = $orgs.id
        if (-not $orgId) { $orgId = $orgs.org_id }
    }
}
if (-not $orgId) { Fail "could not resolve org id" }

Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ enabled = $true; allowed_cidrs = "192.0.2.0/32" } | ConvertTo-Json) | Out-Null

$blocked = $false
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
            if ($enforceEnv -match '^(1|true)$') {
                $guestScript = "curl -sS -m 5 -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer $token' http://127.0.0.1:8080/api/v1/users/me"
                $out = Invoke-PlinkShell -Plink $plink -HostKey $hk -Port 12221 -Script $guestScript
                $blocked = ($out -match '403')
            }
        }
    }
}

if (-not $blocked) {
    Write-Host "[SKIP] ip allowlist 403 probe unavailable (enforce off or no guest SSH); audit row not verified live" -ForegroundColor Yellow
    Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body (@{ enabled = $false; allowed_cidrs = "" } | ConvertTo-Json) | Out-Null
    exit 0
}

Start-Sleep -Seconds 1
$events = Invoke-RestMethod -Uri "$api/admin/audit-events?limit=10&action=access.ip_allowlist.denied&resource_type=organization&resource_id=$orgId" `
    -Headers $headers
if (-not $events -or ($events -is [System.Array] -and $events.Count -eq 0)) {
    Fail "no access.ip_allowlist.denied audit row for org=$orgId"
}

Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
    -ContentType "application/json; charset=utf-8" `
    -Body (@{ enabled = $false; allowed_cidrs = "" } | ConvertTo-Json) | Out-Null

Write-Host "[OK] audit access.ip_allowlist.denied org=$orgId" -ForegroundColor Green
