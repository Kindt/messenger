# Pre-restart diagnostics: guest SSH probe + host cache/registry checks -> restart plan.
function Invoke-KorusQemuPreRestartAnalysis {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [string]$ServerHostKey = "",
        [string]$PullFingerprint = "",
        [hashtable]$State = @{},
        [int]$SshTimeoutSec = 25
    )

    $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    $findings = New-Object System.Collections.Generic.List[string]
    $metrics = @{}
    $bootstrapErrors = @()

    . (Join-Path $Root "deploy\qemu\lib\Korus-DockerImageCache.ps1")
    $cachePaths = Get-KorusDockerCachePaths

    if (Test-KorusDockerCacheFresh -Paths $cachePaths) {
        $findings.Add("host_cache_fresh") | Out-Null
        $metrics.host_cache_mb = [math]::Round((Get-Item $cachePaths.Tar).Length / 1MB, 1)
    }
    elseif (Test-Path $cachePaths.Tar) {
        $findings.Add("host_cache_stale") | Out-Null
    }
    else {
        $findings.Add("host_cache_missing") | Out-Null
    }

    if (Test-KorusDockerOnHost) { $findings.Add("host_docker_available") | Out-Null }

    $sameFpRestarts = 0
    $history = @($State.restartHistory)
    foreach ($entry in $history) {
        if ([string]$entry.fingerprint -eq $PullFingerprint -and $entry.keepDisks) {
            $sameFpRestarts++
        }
    }
    if ($sameFpRestarts -ge 2) {
        $findings.Add("repeated_keepDisks_no_progress") | Out-Null
    }
    $metrics.same_fp_keepDisks_restarts = $sameFpRestarts

    $rawDiag = ""
    if ((Test-Path $Plink) -and $ServerHostKey) {
        $diagScript = @'
echo "=== pre-restart analysis $(date -Iseconds) ==="
tail -80 /var/log/korus-bootstrap.log 2>&1
echo "---DISK---"
df -h / /var/lib/docker 2>&1 | tail -5
echo "---DOCKER---"
systemctl is-active docker 2>&1
echo "---IMAGES---"
docker images --format '{{.Repository}}:{{.Tag}}' 2>&1 | head -24
echo "---REGISTRY---"
curl -s -o /dev/null -w 'registry_http=%{http_code} time=%{time_total}s\n' --max-time 20 https://registry-1.docker.io/v2/ 2>&1 || echo registry_curl_failed
echo "---PROC---"
pgrep -af 'docker|ansible' 2>&1 | head -12
echo "---CACHE---"
test -f /var/lib/korus/docker-cache-loaded && echo cache_stamp=yes || echo cache_stamp=no
docker image inspect eclipse-temurin:25-jre >/dev/null 2>&1 && echo temurin=yes || echo temurin=no
'@
        $job = Start-Job {
            param($Plink, $HostKey, $Script)
            & $Plink -batch -hostkey $HostKey -pw korus -P 12221 "korus@127.0.0.1" $Script 2>&1
        } -ArgumentList $Plink, $ServerHostKey, $diagScript
        $done = Wait-Job $job -Timeout $SshTimeoutSec
        if ($done) {
            $rawDiag = (($job | Receive-Job) | ForEach-Object { "$_" }) -join "`n"
            Remove-Job $job -Force | Out-Null
            $findings.Add("guest_ssh_ok") | Out-Null
        }
        else {
            Stop-Job $job -Force | Out-Null
            Remove-Job $job -Force | Out-Null
            $findings.Add("guest_ssh_timeout") | Out-Null
            $rawDiag = "(SSH timeout after ${SshTimeoutSec}s)"
        }
    }
    else {
        $findings.Add("guest_ssh_unavailable") | Out-Null
    }

    if ($rawDiag) {
        if ($rawDiag -match 'temurin=yes') {
            $findings.Add("guest_temurin_present") | Out-Null
            $metrics.temurin = "yes"
        }
        elseif ($rawDiag -match 'temurin=no') {
            $findings.Add("guest_temurin_missing") | Out-Null
            $metrics.temurin = "no"
        }

        if ($rawDiag -match 'cache_stamp=yes') { $findings.Add("guest_cache_stamp") | Out-Null }
        if ($rawDiag -match 'registry_http=(\d+)') {
            $code = $Matches[1]
            $metrics.registry_http = $code
            if ($code -match '^2') { $findings.Add("registry_ok") | Out-Null }
            else { $findings.Add("registry_http_$code") | Out-Null }
        }
        elseif ($rawDiag -match 'registry_curl_failed') {
            $findings.Add("registry_unreachable") | Out-Null
        }

        if ($rawDiag -match 'docker\s+dead|failed|inactive') {
            $findings.Add("docker_daemon_down") | Out-Null
        }
        elseif ($rawDiag -match 'active') {
            $findings.Add("docker_daemon_active") | Out-Null
        }

        $diskLine = ($rawDiag -split "`n" | Where-Object { $_ -match '/dev/' -and $_ -match '\s/\s' } | Select-Object -First 1)
        if ($diskLine -match '(\d+)%') {
            $pct = [int]$Matches[1]
            $metrics.disk_used_pct = $pct
            if ($pct -ge 95) { $findings.Add("disk_full") | Out-Null }
            elseif ($pct -ge 85) { $findings.Add("disk_warn") | Out-Null }
        }

        $bootstrapErrors = @($rawDiag -split "`n" | Where-Object {
            $_ -match '(?i)\b(ERROR|failed|no space|OOM|denied|timeout)\b' -and $_ -notmatch 'registry_curl_failed'
        } | Select-Object -Last 6)
        if ($bootstrapErrors.Count -gt 0) { $findings.Add("bootstrap_errors") | Out-Null }

        if ($PullFingerprint -and $rawDiag -match [regex]::Escape($PullFingerprint.Substring(0, [Math]::Min(40, $PullFingerprint.Length)))) {
            $findings.Add("pull_fp_in_log") | Out-Null
        }
    }

    if ($State.guestFixForFingerprint -eq $PullFingerprint) {
        $findings.Add("guest_fix_already_tried") | Out-Null
    }

    $summaryParts = @($findings)
    if ($metrics.registry_http) { $summaryParts += "registry=$($metrics.registry_http)" }
    if ($metrics.temurin) { $summaryParts += "temurin=$($metrics.temurin)" }
    if ($metrics.same_fp_keepDisks_restarts) { $summaryParts += "sameFpRestarts=$($metrics.same_fp_keepDisks_restarts)" }

    return @{
        Findings         = @($findings)
        Metrics          = $metrics
        BootstrapErrors  = @($bootstrapErrors)
        Summary          = ($summaryParts -join ", ")
        RawDiag          = $rawDiag
    }
}

function Get-KorusQemuRestartPlan {
    param(
        [hashtable]$Analysis,
        [hashtable]$State,
        [string]$PullFingerprint,
        [double]$StuckMin
    )

    $f = @($Analysis.Findings)
    $sameFp = [int]$Analysis.Metrics.same_fp_keepDisks_restarts
    $cacheLoadAttempts = [int]$State.cacheLoadAttempts

    if ($f -contains "host_cache_fresh" -and $f -contains "guest_temurin_missing") {
        return @{
            Action    = "guest-load-cache"
            KeepDisks = $true
            Reason    = "host has fresh docker-base-images.tar; guest missing eclipse-temurin:25-jre"
        }
    }

    if ($f -contains "host_cache_missing" -and $f -contains "host_docker_available" -and $cacheLoadAttempts -lt 2) {
        return @{
            Action    = "build-host-cache"
            KeepDisks = $true
            Reason    = "no host image cache; pull on host then load into guest (skip blind VM restart)"
        }
    }

    if ($f -contains "disk_full") {
        return @{
            Action    = "restart-fresh-disks"
            KeepDisks = $false
            Reason    = "guest disk >=95% full; KeepDisks would preserve broken state"
        }
    }

    if ($f -contains "repeated_keepDisks_no_progress" -or $sameFp -ge 2) {
        return @{
            Action    = "restart-fresh-disks"
            KeepDisks = $false
            Reason    = "same pull fingerprint after $sameFp KeepDisks restart(s); fresh disks required"
        }
    }

    if ($f -contains "guest_fix_already_tried" -and $f -contains "pull_fp_in_log" -and $f -notcontains "host_cache_fresh") {
        return @{
            Action    = "restart-fresh-disks"
            KeepDisks = $false
            Reason    = "guest fix did not unblock pull and no host cache; fresh bootstrap"
        }
    }

    if ($f -contains "registry_unreachable" -and $f -notcontains "host_cache_fresh" -and $f -notcontains "host_docker_available") {
        return @{
            Action    = "skip-restart"
            KeepDisks = $true
            Reason    = "registry unreachable, no host docker/cache; VM restart will not fix network"
        }
    }

    if ($f -contains "docker_daemon_down") {
        return @{
            Action    = "restart-keep-disks"
            KeepDisks = $true
            Reason    = "docker daemon inactive; VM restart may recover daemon"
        }
    }

    return @{
        Action    = "restart-keep-disks"
        KeepDisks = $true
        Reason    = "pull stuck ${StuckMin}m; first/retry restart with analysis (no repeated same-fp loop yet)"
    }
}

function Write-KorusRestartAnalysisLog {
    param(
        [string]$RemLog,
        [hashtable]$Analysis,
        [hashtable]$Plan
    )
    function W([string]$Line) {
        "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Line" | Add-Content -Path $RemLog -Encoding utf8
    }
    W "ANALYSIS findings=$($Analysis.Summary)"
    foreach ($err in $Analysis.BootstrapErrors) {
        W "  bootstrap_err: $($err.Trim())"
    }
    $diagLines = ($Analysis.RawDiag -split "`n" | Where-Object { $_ -match '^(===|---|registry_|temurin=|cache_stamp=|/dev/)' } | Select-Object -First 12)
    foreach ($dl in $diagLines) { W "  diag: $($dl.Trim())" }
    W "PLAN action=$($Plan.Action) keepDisks=$($Plan.KeepDisks) reason=$($Plan.Reason)"
}
