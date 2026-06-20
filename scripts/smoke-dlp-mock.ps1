# Smoke: DLP mock bridge returns block for sensitive text (spec 022 T02201)
$ErrorActionPreference = "Stop"
$base = if ($env:DLP_MOCK_URL) { $env:DLP_MOCK_URL.TrimEnd("/") } else { "http://127.0.0.1:8098" }
$body = @{
  event_id = [guid]::NewGuid().ToString()
  type = "message.send"
  text = "password leak"
} | ConvertTo-Json
$r = Invoke-RestMethod -Uri "$base/v1/plugin/handle" -Method Post -Body $body -ContentType "application/json"
if ($r.dlp_verdict -ne "block") {
  Write-Error "expected block, got $($r.dlp_verdict)"
}
Write-Host "DLP mock smoke OK: block verdict"
