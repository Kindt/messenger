# Smoke: passkeys WebAuthn scaffold endpoints (FSTEC-17 partial).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-passkeys-scaffold.ps1 [-BaseUrl url]

GET /platform/passkeys (empty list OK) and POST scaffold credential.
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

$list = Invoke-RestMethod -Uri "$api/platform/passkeys" -Headers $headers
if ($null -eq $list) { Fail "GET passkeys returned null" }

$credId = "smoke-" + [guid]::NewGuid().ToString("N")
$body = @{
    credential_id = $credId
    public_key      = "c21va2UtcHVibGljLWtleQ=="
} | ConvertTo-Json
try {
    $created = Invoke-RestMethod -Uri "$api/platform/passkeys" -Method Post -Headers $headers `
        -ContentType "application/json; charset=utf-8" -Body $body
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 403) {
        Write-Host "[OK] passkeys scaffold present (register forbidden for lab user)" -ForegroundColor Green
        exit 0
    }
    Fail "POST passkeys: $_"
}

if (-not $created) { Fail "POST passkeys empty response" }
Write-Host "[OK] passkeys scaffold GET+POST" -ForegroundColor Green
