# Smoke: spec 022 Phase 5 ADR scaffolds (stickers, kanban, sip, passkeys)
param(
    [string]$ApiBase = $(if ($env:KORUS_API_URL) { $env:KORUS_API_URL } else { "http://127.0.0.1:18080/api" })
)
$ErrorActionPreference = "Stop"

function Get-Token {
    $body = @{ username = "admin"; password = "admin" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$ApiBase/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    return $r.access_token
}

$token = Get-Token
$h = @{ Authorization = "Bearer $token" }

Write-Host "[1] sticker packs"
Invoke-RestMethod -Uri "$ApiBase/v1/stickers/packs" -Headers $h | Out-Null
$pack = Invoke-RestMethod -Uri "$ApiBase/v1/stickers/packs" -Method Post -Headers $h -Body (@{ name = "Lab pack" } | ConvertTo-Json) -ContentType "application/json"
if (-not $pack.pack_id) { throw "sticker pack create failed" }

Write-Host "[2] gif search"
Invoke-RestMethod -Uri "$ApiBase/v1/stickers/gifs?q=thumb" -Headers $h | Out-Null

Write-Host "[3] sip gateway"
Invoke-RestMethod -Uri "$ApiBase/v1/platform/sip" -Method Put -Headers $h -Body (@{ enabled = $true; gateway_uri = "sip:gw.lab.local"; h323_enabled = $false } | ConvertTo-Json) -ContentType "application/json" | Out-Null

Write-Host "[4] passkeys scaffold"
$cred = "cred-smoke-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
Invoke-RestMethod -Uri "$ApiBase/v1/platform/passkeys" -Method Post -Headers $h -Body (@{ credential_id = $cred; public_key = "pk-scaffold" } | ConvertTo-Json) -ContentType "application/json" | Out-Null

Write-Host "[OK] smoke-phase5-adr-scaffolds"
