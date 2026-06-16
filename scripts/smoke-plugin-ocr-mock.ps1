param(
    [string]$BaseUrl = "http://127.0.0.1:18095"
)

$ErrorActionPreference = "Stop"
$body = @{
    event_id = "smoke-ocr-1"
    type = "attachment"
    text = "scan invoice"
    payload = @{ file_id = "invoice-demo.pdf" }
} | ConvertTo-Json -Depth 4
$r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/plugin/handle" -ContentType "application/json" -Body $body
if (-not $r.messages[0].text) { throw "empty response" }
Write-Host "[OK] ocr-worker:" $r.messages[0].text.Substring(0, [Math]::Min(100, $r.messages[0].text.Length))
