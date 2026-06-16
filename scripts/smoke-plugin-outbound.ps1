param(
    [string]$ApiBase = "http://127.0.0.1:18080/api",
    [string]$InstanceId = "",
    [string]$Token = "dev-outbound-token",
    [string]$Text = "Outbound smoke from integrations VM"
)

$ErrorActionPreference = "Stop"
if (-not $InstanceId) {
    Write-Host "Set -InstanceId to a plugin instance with outbound configured"
    exit 1
}
$uri = "$ApiBase/v1/integrations/outbound/$InstanceId"
$body = @{ text = $Text } | ConvertTo-Json
$headers = @{ "X-Plugin-Outbound-Token" = $Token; "Content-Type" = "application/json" }
$r = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -Body $body
$r | ConvertTo-Json -Depth 5
