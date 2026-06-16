param(
    [string]$BaseUrl = "http://127.0.0.1:18094"
)

$ErrorActionPreference = "Stop"
$body = @{ event_id = "smoke-st-1"; type = "slash"; text = "/files search report" } | ConvertTo-Json
$r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/plugin/handle" -ContentType "application/json" -Body $body
if (-not $r.messages[0].text) { throw "empty response" }
Write-Host "[OK] storage-bridge:" $r.messages[0].text.Substring(0, [Math]::Min(80, $r.messages[0].text.Length))
