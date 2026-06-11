# Auto-diagnose / fix / restart QEMU dev stack when minute status detects hangs (e.g. stuck docker pull).
# Hot path must stay non-blocking: no SSH waits, no Win32_Process scans.
# Dot-source safe: no script-level param block (mandatory params hang on dot-source).
function Invoke-KorusQemuAutoRemediate {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [hashtable]$Health = @{ Core = $false; Web = $false; Ready = $false },
        [string]$Activity = "",
        [string[]]$BootstrapStates = @(),
        [string]$ServerBootstrapText = "",
        [string]$WebBootstrapText = "",
        [string]$ServerHostKey = "",
        [int]$GuestFixAfterMinutes = 8,
        [int]$RestartAfterMinutes = 15,
        [int]$CooldownMinutes = 8
    )

    $ErrorActionPreference = "SilentlyContinue"
    . (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")
    . (Join-Path $Root "deploy\qemu\lib\Start-KorusQemuGuestRedeploy.ps1")
    . (Join-Path $Root "deploy\qemu\lib\Get-KorusQemuLoadingState.ps1")
    $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    $StatePath = Join-Path $RunDir "status-remediate.json"
    $RemLog = Join-Path $RunDir "status-remediate.log"
    $actions = New-Object System.Collections.Generic.List[string]

    function Write-RemLog {
        param([string]$Line)
        "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Line" | Add-Content -Path $RemLog -Encoding utf8
    }

    function Get-RemState {
        $empty = @{
            pullFingerprint           = ""
            pullFingerprintSince      = $null
            guestFixForFingerprint    = ""
            guestFixAt                = $null
            lastRestartAt             = $null
            lastServerRedeployAt      = $null
            bootstrapErrorFingerprint = ""
            bootstrapErrorSince       = $null
            lastOrphanSweepAt         = $null
            restartHistory            = @()
            cacheLoadAttempts         = 0
            serverVmPid               = 0
        }
        if (-not (Test-Path $StatePath)) { return $empty }
        try {
            $o = Get-Content $StatePath -Raw | ConvertFrom-Json
            $hist = @()
            if ($o.restartHistory) {
                $hist = @($o.restartHistory | ForEach-Object {
                    @{
                        at          = [string]$_.at
                        fingerprint = [string]$_.fingerprint
                        keepDisks   = [bool]$_.keepDisks
                        reason      = [string]$_.reason
                        action      = [string]$_.action
                    }
                })
            }
            return @{
                pullFingerprint           = [string]$o.pullFingerprint
                pullFingerprintSince      = $o.pullFingerprintSince
                guestFixForFingerprint    = [string]$o.guestFixForFingerprint
                guestFixAt                = $o.guestFixAt
                lastRestartAt             = $o.lastRestartAt
                lastServerRedeployAt      = $o.lastServerRedeployAt
                bootstrapErrorFingerprint = [string]$o.bootstrapErrorFingerprint
                bootstrapErrorSince       = $o.bootstrapErrorSince
                lastOrphanSweepAt         = $o.lastOrphanSweepAt
                restartHistory            = $hist
                cacheLoadAttempts         = [int]$o.cacheLoadAttempts
                serverVmPid               = [int]$o.serverVmPid
            }
        } catch {
            return $empty
        }
    }

    function Set-RemState {
        param([hashtable]$State)
        $State | ConvertTo-Json -Compress | Set-Content -Path $StatePath -Encoding utf8 -NoNewline
    }

    function Test-CooldownOk {
        param([object]$LastAt, [int]$Minutes)
        if (-not $LastAt) { return $true }
        try {
            $t = [DateTime]::Parse($LastAt.ToString())
            return ((Get-Date) - $t).TotalMinutes -ge $Minutes
        } catch {
            return $true
        }
    }

    function Get-StuckAgeMinutes {
        param([object]$Since)
        if (-not $Since) { return 0 }
        try {
            return [math]::Round(((Get-Date) - [DateTime]::Parse($Since.ToString())).TotalMinutes, 1)
        } catch {
            return 0
        }
    }

    function Stop-KorusOrphanQemu {
        $keep = @()
        foreach ($role in @("server", "web")) {
            $pf = Join-Path $RunDir "$role.pid"
            if (Test-Path $pf) {
                $id = (Get-Content $pf -Raw).Trim()
                if ($id -match '^\d+$') { $keep += [int]$id }
            }
        }
        $killed = @()
        Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.Id -in $keep) { return }
            if (-not (Test-KorusQemuProcess -ProcessId $_.Id)) { return }
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            $killed += $_.Id
        }
        return $killed
    }

    function Get-PullFingerprintFromText {
        param([string]$Text)
        if (-not $Text) { return "" }
        $lines = ($Text -split "`n" | Where-Object {
            $_ -match 'Downloading|Download complete|Pull complete|Extracting|Pulling|Step [0-9]'
        })
        if ($lines.Count -eq 0) { return "" }
        $normalized = ($lines | Select-Object -Last 12 | ForEach-Object {
            ($_ -replace '\x1b\[[0-9;?]*[ -/]*[@-~]', '') -replace '\s+', ' '
        }) -join "|"
        return $normalized.Trim()
    }

    function Start-GuestPullRemediation {
        param([string]$Role, [string]$HostKey)
        if (-not (Test-Path $Plink) -or -not $HostKey) { return $false }
        $port = if ($Role -eq "server") { 12221 } else { 12222 }
        $guestScript = @"
echo '=== auto-remediate guest `$(date -Iseconds) ===' >> /var/log/korus-bootstrap.log
pgrep -af 'docker|ansible' >> /var/log/korus-bootstrap.log 2>&1 || true
curl -s -o /dev/null -w 'registry_http=%{http_code}\n' --max-time 20 https://registry-1.docker.io/v2/ >> /var/log/korus-bootstrap.log 2>&1 || echo registry_fail >> /var/log/korus-bootstrap.log
pkill -f 'docker build' 2>/dev/null || true
pkill -f 'docker pull' 2>/dev/null || true
sleep 2
sudo systemctl restart docker
sleep 5
if ! pgrep -f run-ansible-local >/dev/null 2>&1; then
  nohup env KORUS_BUILD=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh $Role </dev/null &
  echo 'restarted run-ansible-local.sh $Role' >> /var/log/korus-bootstrap.log
else
  echo 'ansible still running, skip re-trigger' >> /var/log/korus-bootstrap.log
fi
"@
        $arg = "-batch -hostkey $HostKey -pw korus -P $port korus@127.0.0.1 `"$guestScript`""
        Start-Process -FilePath $Plink -ArgumentList $arg -WindowStyle Hidden -WorkingDirectory $Root | Out-Null
        return $true
    }

    function Test-RestartInProgress {
        # Lock file only вЂ” scanning Win32_Process blocks the minute loop for minutes on Windows.
        $lock = Join-Path $RunDir "qemu-auto-restart.lock"
        if (-not (Test-Path $lock)) { return $false }
        $age = ((Get-Date) - (Get-Item $lock).LastWriteTime).TotalMinutes
        if ($age -lt 20) { return $true }
        Remove-Item $lock -Force -ErrorAction SilentlyContinue
        return $false
    }

    function Start-GuestDockerCacheLoad {
        param([string]$HostKey)
        if (-not (Test-Path $Plink) -or -not $HostKey) { return $false }
        $guestScript = @"
echo '=== guest cache load `$(date -Iseconds) ===' >> /var/log/korus-bootstrap.log
pkill -f 'docker pull' 2>/dev/null || true
sleep 2
if [ -f /mnt/korus/deploy/qemu/vm-bootstrap/korus-docker-image-load.sh ]; then
  KORUS_DOCKER_CACHE_ATTEMPTS=36 KORUS_DOCKER_CACHE_SLEEP=5 sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-docker-image-load.sh || true
fi
if ! pgrep -f run-ansible-local >/dev/null 2>&1; then
  nohup env KORUS_BUILD=1 sh /mnt/korus/deploy/qemu/vm-bootstrap/run-ansible-local.sh server </dev/null &
  echo 'restarted ansible after cache load' >> /var/log/korus-bootstrap.log
fi
"@
        $arg = "-batch -hostkey $HostKey -pw korus -P 12221 korus@127.0.0.1 `"$guestScript`""
        Start-Process -FilePath $Plink -ArgumentList $arg -WindowStyle Hidden -WorkingDirectory $Root | Out-Null
        return $true
    }

    function Start-HostDockerCacheBuild {
        . (Join-Path $Root "deploy\qemu\lib\Korus-DockerImageCache.ps1")
        Start-KorusDockerImageCacheBackground | Out-Null
        return $true
    }

    function Invoke-AnalyzedStackRestart {
        param(
            [hashtable]$State,
            [string]$PullFp,
            [double]$StuckMin,
            [string]$Trigger
        )
        if (Test-RestartInProgress) {
            Write-RemLog "SKIP restart already in progress"
            return @{ Started = $false; Summary = "restart in progress" }
        }

        . (Join-Path $Root "deploy\qemu\lib\Invoke-KorusQemuPreRestartAnalysis.ps1")
        $analysis = Invoke-KorusQemuPreRestartAnalysis -RunDir $RunDir -Root $Root `
            -ServerHostKey $ServerHostKey -PullFingerprint $PullFp -State $State
        $plan = Get-KorusQemuRestartPlan -Analysis $analysis -State $State `
            -PullFingerprint $PullFp -StuckMin $StuckMin
        Write-KorusRestartAnalysisLog -RemLog $RemLog -Analysis $analysis -Plan $plan

        if ($plan.Action -eq "skip-restart") {
            Write-RemLog "SKIP restart: $($plan.Reason)"
            return @{ Started = $false; Summary = "skip restart: $($plan.Reason)" }
        }

        if ($plan.Action -eq "guest-load-cache") {
            Write-RemLog "ACTION guest-load-cache (no VM restart): $($plan.Reason)"
            Start-GuestDockerCacheLoad -HostKey $ServerHostKey | Out-Null
            $state.cacheLoadAttempts = [int]$State.cacheLoadAttempts + 1
            $State.pullFingerprintSince = (Get-Date).ToString("o")
            return @{ Started = $false; Summary = "guest cache load (attempt $($State.cacheLoadAttempts)): $($plan.Reason)" }
        }

        if ($plan.Action -eq "build-host-cache") {
            Write-RemLog "ACTION build-host-cache + guest load (no VM restart): $($plan.Reason)"
            Start-HostDockerCacheBuild | Out-Null
            Start-GuestDockerCacheLoad -HostKey $ServerHostKey | Out-Null
            $State.cacheLoadAttempts = [int]$State.cacheLoadAttempts + 1
            $State.pullFingerprintSince = (Get-Date).ToString("o")
            return @{ Started = $false; Summary = "host cache build + guest load (attempt $($State.cacheLoadAttempts))" }
        }

        $fresh = -not $plan.KeepDisks
        $lock = Join-Path $RunDir "qemu-auto-restart.lock"
        Set-Content -Path $lock -Value (Get-Date).ToString("o") -Encoding ascii
        $script = Join-Path $Root "scripts\qemu-auto-restart.ps1"
        $restartArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $script)
        if ($fresh) { $restartArgs += "-FreshDisks" }
        Write-RemLog "ACTION spawn qemu-auto-restart.ps1 keepDisks=$(-not $fresh) trigger=$Trigger reason=$($plan.Reason)"
        Start-Process -FilePath "powershell.exe" `
            -ArgumentList $restartArgs `
            -WorkingDirectory $Root `
            -WindowStyle Hidden | Out-Null

        $hist = [System.Collections.Generic.List[object]]::new()
        foreach ($h in @($State.restartHistory)) { $hist.Add($h) | Out-Null }
        $hist.Add(@{
            at          = (Get-Date).ToString("o")
            fingerprint = $PullFp
            keepDisks   = -not $fresh
            reason      = [string]$plan.Reason
            action      = [string]$plan.Action
        }) | Out-Null
        while ($hist.Count -gt 8) { $hist.RemoveAt(0) }
        $State.restartHistory = @($hist)
        $State.lastRestartAt = (Get-Date).ToString("o")
        $State.pullFingerprint = ""
        $State.pullFingerprintSince = $null
        $State.guestFixForFingerprint = ""
        if ($fresh) { $State.cacheLoadAttempts = 0 }

        $mode = if ($fresh) { "fresh disks" } else { "KeepDisks" }
        return @{ Started = $true; Summary = "restart ($mode): $($plan.Reason)" }
    }

    function Start-KorusQemuStackRestart {
        param(
            [hashtable]$State,
            [string]$PullFp = "",
            [double]$StuckMin = 0,
            [string]$Trigger = "unknown"
        )
        $r = Invoke-AnalyzedStackRestart -State $State -PullFp $PullFp -StuckMin $StuckMin -Trigger $Trigger
        return $r.Started
    }

    $stackReady = $Health.Core -and $Health.Web -and $Health.Ready
    if ($stackReady) {
        if (Test-Path $StatePath) { Remove-Item $StatePath -Force -ErrorAction SilentlyContinue }
        return @{ Actions = @(); Summary = "" }
    }

    $state = Get-RemState
    $serverPidNow = 0
    $serverPidFile = Join-Path $RunDir "server.pid"
    if (Test-Path $serverPidFile) {
        $rawPid = (Get-Content $serverPidFile -Raw).Trim()
        if ($rawPid -match '^\d+$') { $serverPidNow = [int]$rawPid }
    }
    if ($serverPidNow -gt 0 -and $state.serverVmPid -ne $serverPidNow) {
        Write-RemLog "RESET state server VM PID $($state.serverVmPid) -> $serverPidNow"
        $state.serverVmPid = $serverPidNow
        $state.bootstrapErrorFingerprint = ""
        $state.bootstrapErrorSince = $null
        $state.pullFingerprint = ""
        $state.pullFingerprintSince = $null
        $state.guestFixForFingerprint = ""
    }

    $loadingState = Get-KorusQemuLoadingState -ServerBootstrapText $ServerBootstrapText `
        -WebBootstrapText $WebBootstrapText -Activity $Activity -BootstrapStates $BootstrapStates
    if ($loadingState.Loading) {
        $actions.Add("loading $($loadingState.Kind): wait ($($loadingState.Detail))") | Out-Null
        Write-RemLog "WAIT loading kind=$($loadingState.Kind) detail=$($loadingState.Detail)"
    }

    $orphans = @(Stop-KorusOrphanQemu)
    if ($orphans.Count -gt 0) {
        $msg = "killed orphan QEMU: $($orphans -join ',')"
        $actions.Add($msg) | Out-Null
        Write-RemLog $msg
    }

    $korusQemuUp = Test-KorusQemuStackRunning -RunDir $RunDir

    if (-not $korusQemuUp -and -not $stackReady) {
        if (Test-CooldownOk -LastAt $state.lastRestartAt -Minutes $CooldownMinutes) {
            $r = Invoke-AnalyzedStackRestart -State $state -PullFp $state.pullFingerprint -StuckMin 0 -Trigger "vms-down"
            if ($r.Summary) { $actions.Add($r.Summary) | Out-Null }
            Set-RemState $state
            return @{ Actions = $actions; Summary = ($actions -join "; ") }
        }
        $actions.Add("VMs down (cooldown, skip restart)") | Out-Null
        Set-RemState $state
        return @{ Actions = $actions; Summary = ($actions -join "; ") }
    }

    $pullStuck = ($Activity -eq "docker pull") -or ($BootstrapStates -contains "docker-pull") -or ($loadingState.Kind -eq 'docker-pull')
    if ($pullStuck -and $korusQemuUp -and -not $loadingState.Loading) {
        $fp = Get-PullFingerprintFromText -Text $ServerBootstrapText
        if ($fp) {
            if ($state.pullFingerprint -ne $fp) {
                $state.pullFingerprint = $fp
                $state.pullFingerprintSince = (Get-Date).ToString("o")
                $state.guestFixForFingerprint = ""
            }
            $stuckMin = Get-StuckAgeMinutes -Since $state.pullFingerprintSince
            $guestDone = ($state.guestFixForFingerprint -eq $fp)

            if ($stuckMin -ge $GuestFixAfterMinutes -and -not $guestDone -and (Test-CooldownOk -LastAt $state.guestFixAt -Minutes 5)) {
                $actions.Add("pull stuck ${stuckMin}m -> guest diag (docker restart + re-bootstrap)") | Out-Null
                Write-RemLog "ACTION guest remediation (background) server stuck=${stuckMin}m fp=$fp"
                Start-GuestPullRemediation -Role "server" -HostKey $ServerHostKey | Out-Null
                $state.guestFixForFingerprint = $fp
                $state.guestFixAt = (Get-Date).ToString("o")
            }
            elseif ($stuckMin -ge $RestartAfterMinutes -and (Test-CooldownOk -LastAt $state.lastRestartAt -Minutes $CooldownMinutes)) {
                $r = Invoke-AnalyzedStackRestart -State $state -PullFp $fp -StuckMin $stuckMin -Trigger "pull-stuck"
                if ($r.Summary) { $actions.Add($r.Summary) | Out-Null }
            }
            elseif ($stuckMin -ge $GuestFixAfterMinutes) {
                $actions.Add("pull stuck ${stuckMin}m (waiting cooldown/restart threshold)") | Out-Null
            }
            else {
                $actions.Add("pull monitoring ${stuckMin}m (guest fix at ${GuestFixAfterMinutes}m)") | Out-Null
            }
        } else {
            $actions.Add("pull stuck (no fingerprint yet)") | Out-Null
        }
    }

    $bootstrapErr = ($Activity -eq "bootstrap errors") -or ($BootstrapStates -contains "error")
    if ($bootstrapErr -and (Test-KorusBootstrapNoiseError -BootstrapText $ServerBootstrapText -LoadingState $loadingState)) {
        $bootstrapErr = $false
    }
    $coreDown = -not $Health.Core
    $sshServerUp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue |
        ForEach-Object { [bool]$_.TcpTestSucceeded }
    if ($korusQemuUp -and $coreDown -and $sshServerUp -and -not $loadingState.Loading -and ($bootstrapErr -or $Health.Web)) {
        $errFp = ""
        if ($ServerBootstrapText) {
            $errLines = @($ServerBootstrapText -split "`n" | Where-Object {
                $_ -match 'FAILED|fatal:|ERROR|409|no space|OOM'
            } | Select-Object -Last 3) -join "|"
            if ($errLines) {
                $errFp = $errLines.Substring(0, [Math]::Min(160, $errLines.Length))
            }
        }
        if (-not $errFp) { $errFp = "core-down-web=$($Health.Web)" }
        if ($state.bootstrapErrorFingerprint -ne $errFp) {
            $state.bootstrapErrorFingerprint = $errFp
            $state.bootstrapErrorSince = (Get-Date).ToString("o")
        }
        $errMin = Get-StuckAgeMinutes -Since $state.bootstrapErrorSince

        if ($errMin -ge 3 -and (Test-CooldownOk -LastAt $state.lastServerRedeployAt -Minutes 10)) {
            Write-RemLog "ACTION background redeploy server errMin=${errMin}m fp=$errFp"
            $rd = Start-KorusQemuGuestRedeploy -Role server -RunDir $RunDir -Root $Root -Reason "core down / bootstrap ${errMin}m"
            if ($rd.Summary) { $actions.Add($rd.Summary) | Out-Null }
            $state.lastServerRedeployAt = (Get-Date).ToString("o")
        }
        elseif ($errMin -ge 12 -and (Test-CooldownOk -LastAt $state.lastRestartAt -Minutes $CooldownMinutes)) {
            $r = Invoke-AnalyzedStackRestart -State $state -PullFp $errFp -StuckMin $errMin -Trigger "bootstrap-error"
            if ($r.Summary) { $actions.Add($r.Summary) | Out-Null }
        }
        elseif ($bootstrapErr -or $coreDown) {
            $actions.Add("server stack down ${errMin}m (redeploy at 3m, VM restart at 12m)") | Out-Null
        }
    }

    Set-RemState $state
    $summary = if ($actions.Count) { $actions -join "; " } else { "" }
    return @{ Actions = @($actions); Summary = $summary }
}
