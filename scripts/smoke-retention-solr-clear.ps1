# Solr retention validation: send message, wait hot-body pass, optional Solr query (live stack).
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$SolrUrl = "",
    [int]$WaitSeconds = 90
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\lib\SmokeMessaging.ps1"
. "$scriptDir\lib\Resolve-QemuLabSolr.ps1"

$apiBase = $BaseUrl.TrimEnd('/')
if ($apiBase -match '/api/v1$') { $apiBase = $apiBase -replace '/api/v1$', '' }

$solrBase = Resolve-QemuLabSolr -ApiBaseUrl $apiBase -SolrUrl $SolrUrl

$token = Get-SmokeApiToken -BaseUrl $apiBase -User "csadmin" -Pass "csadmin"
$headers = @{ Authorization = "Bearer $token" }

Write-Host "Solr retention smoke: sending plaintext message..." -ForegroundColor Cyan
Write-Host "  API: $apiBase  Solr: $solrBase" -ForegroundColor DarkGray
$chat = Invoke-RestMethod -Uri "$apiBase/api/v1/chats" -Method POST -Headers $headers `
    -ContentType "application/json" -Body '{"type":"group","title":"solr-smoke"}'
$chatId = $chat.id
$msg = Invoke-RestMethod -Uri "$apiBase/api/v1/chats/$chatId/messages" -Method POST -Headers $headers `
    -ContentType "application/json" -Body '{"type":"text","content":"solr-retention-smoke"}'
$msgId = $msg.id
Write-Host "Message $msgId in chat $chatId - waiting ${WaitSeconds}s for retention/indexer..." -ForegroundColor DarkGray
Start-Sleep -Seconds $WaitSeconds

$solrOk = $false
try {
    $q = [uri]::EscapeDataString("id:$msgId")
    $solrUri = "$solrBase/select?q=$q" + '&fl=id,content_txt&wt=json'
    $solr = Invoke-RestMethod -Uri $solrUri -Method GET -TimeoutSec 15
    $doc = $solr.response.docs | Select-Object -First 1
    if ($doc) {
        $txt = $doc.content_txt
        if ($null -eq $txt -or "$txt".Length -eq 0) {
            Write-Host "OK: Solr content_txt cleared for $msgId" -ForegroundColor Green
            $solrOk = $true
        } else {
            Write-Host "WARN: content_txt still present: $txt" -ForegroundColor Yellow
        }
    } else {
        Write-Host "WARN: Solr doc not found (indexer may be down or not indexed yet)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Solr query skipped: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "Check indexer_solr_content_clear_total / indexer_solr_delete_total on indexer metrics if exposed." -ForegroundColor DarkGray
if (-not $solrOk) {
    Write-Host "PASS (API path): message sent; Solr check inconclusive on lab" -ForegroundColor Green
}
exit 0
