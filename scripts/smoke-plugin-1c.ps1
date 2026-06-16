param(
    [string]$BaseUrl = "http://127.0.0.1:18097"
)

$ErrorActionPreference = "Stop"
$body = @{ event_id = "smoke-1c-1"; type = "slash"; text = "/catalog" } | ConvertTo-Json
$r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/plugin/handle" -ContentType "application/json" -Body $body
if (-not $r.messages[0].text) { throw "empty response" }
Write-Host "[OK] onec-bridge:" $r.messages[0].text.Substring(0, [Math]::Min(100, $r.messages[0].text.Length))
