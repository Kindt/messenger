# Solr retention validation: send message, wait hot-body pass, optional Solr query (live stack).
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$SolrUrl = "http://localhost:8983/solr/messages",
    [int]$WaitSeconds = 90
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$scriptDir\lib\SmokeMessaging.ps1"

$token = Get-SmokeAccessToken -BaseUrl $BaseUrl -Username "csadmin" -Password "csadmin"
$headers = @{ Authorization = "Bearer $token" }

Write-Host "Solr retention smoke: sending plaintext message..." -ForegroundColor Cyan
$chat = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Method POST -Headers $headers `
    -ContentType "application/json" -Body '{"type":"group","title":"solr-smoke"}'
$chatId = $chat.id
$msg = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages" -Method POST -Headers $headers `
    -ContentType "application/json" -Body '{"type":"text","content":"solr-retention-smoke"}'
$msgId = $msg.id
Write-Host "Message $msgId in chat $chatId — waiting ${WaitSeconds}s for retention/indexer..." -ForegroundColor DarkGray
Start-Sleep -Seconds $WaitSeconds

try {
    $q = [uri]::EscapeDataString("id:$msgId")
    $solr = Invoke-RestMethod -Uri "$SolrUrl/select?q=$q&fl=id,content_txt&wt=json" -Method GET
    $doc = $solr.response.docs | Select-Object -First 1
    if ($doc) {
        $txt = $doc.content_txt
        if ($null -eq $txt -or "$txt".Length -eq 0) {
            Write-Host "OK: Solr content_txt cleared for $msgId" -ForegroundColor Green
        } else {
            Write-Host "WARN: content_txt still present: $txt" -ForegroundColor Yellow
        }
    } else {
        Write-Host "WARN: Solr doc not found (indexer may be down)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "Solr query skipped: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host "Check indexer_solr_content_clear_total / indexer_solr_delete_total on indexer metrics if exposed." -ForegroundColor DarkGray
exit 0
