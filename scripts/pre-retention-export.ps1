# Queue chat export before aggressive retention / purge (compliance workflow).
# Requires owner or admin on the chat. Uses same API as smoke-export-chat.ps1.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [Parameter(Mandatory = $true)]
    [string]$ChatId,
    [int]$PollSeconds = 120,
    [int]$PollIntervalSec = 2
)
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$scriptDir\smoke-export-chat.ps1" -BaseUrl $BaseUrl -User $User -Pass $Pass -ChatId $ChatId `
    -PollSeconds $PollSeconds -PollIntervalSec $PollIntervalSec

Write-Host ""
Write-Host "Pre-retention checklist:" -ForegroundColor Cyan
Write-Host "  1. Store downloaded artifact (JSON or ZIP) in your compliance archive."
Write-Host "  2. For ZIP: verify attachments/manifest.json SHA-256; GET download?part=json|manifest."
Write-Host "  3. Check export.json retentionPolicy and exportCompleteness.gdprDisclosures."
Write-Host "  4. For full pack on export-replay-worker:"
Write-Host "       EXPORT_REPLAY_INCLUDE_FILE_BODIES=true"
Write-Host "       EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE=true"
Write-Host "       EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS=true"
Write-Host "       EXPORT_REPLAY_INCLUDE_SOLR_INDEX=true (if Solr is used)"
Write-Host "  5. Admin audit: export.requested, export.downloaded, export.suggested / export.auto_queued."
Write-Host "  6. Stack overlays: .\scripts\export-smoke-stack-up.ps1 -AutoQueue; .\scripts\retention-export-smoke-up.ps1"
Write-Host "  7. Retention E2E: .\scripts\smoke-retention-export-suggested.ps1 -ChatId $ChatId -Prepare"
Write-Host "  8. Full compliance: .\scripts\smoke-export-compliance-pack.ps1 -ChatId $ChatId"
Write-Host "  9. Re-run export after retention policy changes if legal requires a point-in-time snapshot."
