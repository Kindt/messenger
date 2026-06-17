# Short plink sessions: start build on guest via nohup, poll log/exit (avoids 90+ min SSH hang).

function Stop-KorusGuestPlinkOnPort {
    param([int]$Port)
    Get-CimInstance Win32_Process -Filter "name='plink.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match ":$Port\b" } |
        ForEach-Object {
            Write-Host "Stopping plink pid $($_.ProcessId) on port $Port" -ForegroundColor Yellow
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

function Invoke-KorusGuestRemoteJob {
    param(
        [Parameter(Mandatory)][string]$Plink,
        [Parameter(Mandatory)][string]$HostKey,
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$JobName,
        [Parameter(Mandatory)][string]$Script,
        [int]$TimeoutMin = 120,
        [int]$PollSec = 30
    )
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")

    $log = "/tmp/korus-job-$JobName.log"
    $exitFile = "/tmp/korus-job-$JobName.exit"
    $runnerPath = "/tmp/korus-job-$JobName-runner.sh"

    $runnerBody = @"
#!/bin/bash
set -euo pipefail
: > '$log'
rm -f '$exitFile'
{
$Script
} >> '$log' 2>&1
echo `$? > '$exitFile'
"@

    Write-Host "Guest job '$JobName' (background, poll ${PollSec}s, timeout ${TimeoutMin}m)..." -ForegroundColor Cyan

    $upload = @"
cat > '$runnerPath' << 'KORUS_RUNNER_EOF'
$runnerBody
KORUS_RUNNER_EOF
chmod +x '$runnerPath'
"@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script $upload | Out-Null

    $launch = @"
nohup '$runnerPath' > /dev/null 2>&1 &
echo launched
"@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script $launch | Out-Null

    $deadline = (Get-Date).AddMinutes($TimeoutMin)
    $lastTail = ""
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds $PollSec
        $status = Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script @"
if [ -f '$exitFile' ]; then cat '$exitFile'; exit 0; fi
tail -n 2 '$log' 2>/dev/null || true
echo STILL_RUNNING
"@
        $lines = @($status -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        if ($lines.Count -ge 1 -and $lines[0] -match '^\d+$') {
            $code = [int]$lines[0]
            if ($code -eq 0) {
                Write-Host "[OK] guest job $JobName finished (exit 0)" -ForegroundColor Green
                return $true
            }
            Write-Host "[FAIL] guest job $JobName exit $code" -ForegroundColor Red
            $tail = Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script "tail -n 50 '$log' 2>/dev/null || true"
            Write-Host $tail
            return $false
        }
        $line = ($lines | Where-Object { $_ -ne 'STILL_RUNNING' -and $_ -notmatch 'launched' } | Select-Object -Last 1)
        if ($line -and $line -ne $lastTail) {
            $lastTail = $line
            Write-Host "  ${JobName}: $line" -ForegroundColor DarkGray
        }
    }
    Write-Host "[FAIL] guest job $JobName timeout after ${TimeoutMin}m" -ForegroundColor Red
    $tail = Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script "tail -n 30 '$log' 2>/dev/null || true"
    Write-Host $tail
    return $false
}
