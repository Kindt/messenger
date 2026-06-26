# Live progress for «тесты UI» — polls deploy/qemu/run/ui-tests-live.json
param(
    [int]$IntervalSec = 5
)

$Root = Split-Path -Parent $PSScriptRoot
$LiveFile = Join-Path $Root "deploy\qemu\run\ui-tests-live.json"
$LogHint = Join-Path $Root "deploy\qemu\run\ui-tests-run.log"

Write-Host "Watching: $LiveFile" -ForegroundColor Cyan
Write-Host "Full log (if started via ui-tests.ps1): $LogHint" -ForegroundColor DarkGray
Write-Host ""

while ($true) {
    $ts = Get-Date -Format "HH:mm:ss"
    if (-not (Test-Path $LiveFile)) {
        Write-Host "[$ts] idle — no live file yet (run .\scripts\ui-tests.ps1 -Profile smoke)" -ForegroundColor Yellow
    } else {
        try {
            $j = Get-Content $LiveFile -Raw -Encoding UTF8 | ConvertFrom-Json
            $pct = if ($j.total -gt 0) { [math]::Round(100 * $j.index / $j.total) } else { 0 }
            $line = "[$ts] $($j.status) $($j.index)/$($j.total) ($pct%) pass=$($j.passed) fail=$($j.failed) skip=$($j.skipped)"
            if ($j.currentId) { $line += " | $($j.currentId)" }
            $color = switch ($j.status) {
                "done" { "Green" }
                "running" { "Cyan" }
                default { "Gray" }
            }
            Write-Host $line -ForegroundColor $color
        } catch {
            Write-Host "[$ts] (reading live file...)" -ForegroundColor DarkGray
        }
    }
    Start-Sleep -Seconds $IntervalSec
}
