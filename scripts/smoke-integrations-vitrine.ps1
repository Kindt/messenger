# Spec 022: smoke connector vitrine tiles on integrations tab (QEMU guest).
param(
    [string]$ApiBase = "http://127.0.0.1:18080/api"
)
$ErrorActionPreference = "Stop"
$token = $env:KORUS_SMOKE_TOKEN
if (-not $token) {
    Write-Host "Set KORUS_SMOKE_TOKEN (JWT) before running."
    exit 1
}
$headers = @{ Authorization = "Bearer $token" }
$res = Invoke-RestMethod -Uri "$ApiBase/v1/me/integrations" -Headers $headers -Method GET
$tiles = $res.vitrine_tiles
if (-not $tiles -or $tiles.Count -lt 2) {
    Write-Host "FAIL: expected vitrine_tiles >= 2, got $($tiles.Count)"
    exit 1
}
Write-Host "OK: vitrine tiles count=$($tiles.Count) first=$($tiles[0].connector_key)"
