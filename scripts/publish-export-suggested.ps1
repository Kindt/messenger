# Publish msg.export.suggested to NATS (dev/smoke). Requires nats CLI and reachable NATS_URL.
param(
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [string]$NatsUrl = "",
    [int]$CandidateCount = 3,
    [string]$Reason = "hot_body_candidates"
)
$ErrorActionPreference = "Stop"

$nats = Get-Command nats -ErrorAction SilentlyContinue
if (-not $nats) { throw "nats CLI not found in PATH" }

if (-not $NatsUrl) { $NatsUrl = $env:NATS_URL }
if (-not $NatsUrl) { $NatsUrl = "nats://127.0.0.1:4222" }

$payload = @{
    chatId = $ChatId
    reason = $Reason
    candidateMessageCount = $CandidateCount
    suggestedAtEpochMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
} | ConvertTo-Json -Compress

Write-Host "Publishing msg.export.suggested chatId=$ChatId via $NatsUrl ..." -ForegroundColor Cyan
& nats --server $NatsUrl pub msg.export.suggested $payload
Write-Host "[OK] Published. Check core-api audit export.suggested (and auto-queue if enabled)." -ForegroundColor Green
Write-Host "Or use admin API: POST /api/v1/admin/chats/$ChatId/export-suggest with EXPORT_ADMIN_SUGGEST_ENABLED=true" -ForegroundColor DarkGray
