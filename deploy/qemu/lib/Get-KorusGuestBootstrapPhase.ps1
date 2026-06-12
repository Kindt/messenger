function Get-KorusGuestBootstrapPhase {
    param([string]$BootstrapText)
    if (-not $BootstrapText) { return "unknown" }
    $t = $BootstrapText
    if ($t -match 'PLAY RECAP|All stacks.*ready|stack is up|full-stack.*ready|\[OK\].*health') { return "ready" }
    if ($t -match 'docker compose up|compose up -d|Starting full-stack-up') { return "compose-up" }
    if ($t -match 'gradle|Gradle|installDist|Daemon will be stopped|BUILD SUCCESSFUL') { return "gradle-build" }
    if ($t -match 'docker pull|Downloading|Pull complete|Pulling fs layer') { return "docker-pull" }
    if ($t -match 'docker compose build|Service .* Building|Sending build context') { return "docker-build" }
    if ($t -match 'repo\.tgz|repo-updated|waiting for repo|run-ansible-local') { return "repo-sync" }
    return "unknown"
}

function Get-KorusGuestBootstrapTail {
    param(
        [string]$HostKey,
        [int]$Port,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe",
        [int]$Lines = 40
    )
    if (-not $HostKey -or -not (Test-Path $Plink)) { return "" }
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")
    try {
        return (Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $Port -Script "tail -n $Lines /var/log/korus-bootstrap.log 2>/dev/null || echo ''")
    } catch {
        return ""
    }
}

function Write-KorusGuestBootstrapProgress {
    param(
        [string]$Role,
        [string]$HostKey,
        [int]$Port,
        [string]$Plink,
        [hashtable]$State
    )
    $tail = Get-KorusGuestBootstrapTail -HostKey $HostKey -Port $Port -Plink $Plink
    $phase = Get-KorusGuestBootstrapPhase -BootstrapText $tail
    $fp = ""
    if ($tail) {
        $fp = ($tail -split "`n" | Select-Object -Last 1).Trim()
        if ($fp.Length -gt 80) { $fp = $fp.Substring(0, 80) }
    }
    if ($State.phase -eq $phase -and $State.fp -eq $fp) {
        $State.stuckMin = [math]::Round($State.stuckMin + 1.0, 1)
    } else {
        $State.phase = $phase
        $State.fp = $fp
        $State.stuckMin = 0
    }
    $warn = if ($phase -eq "gradle-build" -and $State.stuckMin -ge 45) { " (stuck $($State.stuckMin)m)" } else { "" }
    Write-Host "  guest $Role phase=$phase$warn" -ForegroundColor DarkGray
    if ($fp) { Write-Host "    $fp" -ForegroundColor DarkGray }
}
