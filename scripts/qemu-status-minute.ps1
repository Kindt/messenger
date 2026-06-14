# Compact QEMU status for chat / minute agent loop (serial, SSH bootstrap, health).
param(
    [int]$SerialTail = 5,
    [int]$BootstrapTail = 5,
    [int]$SshTimeoutSec = 20,
    [int]$StuckGuestFixMinutes = 8,
    [int]$StuckRestartMinutes = 15,
    [int]$RemediateCooldownMinutes = 8,
    [switch]$Once,
    [switch]$NoLog,
    [switch]$NoRemediate,
    [switch]$Help
)

$ErrorActionPreference = "SilentlyContinue"
function Strip-Ansi {
    param([string]$Text)
    if (-not $Text) { return $Text }
    return ($Text -replace '\x1b\[[0-9;?]*[ -/]*[@-~]', '')
}

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-status-minute.ps1 [-Once] [-NoRemediate] [-SerialTail 5] [-BootstrapTail 5]"
    Write-Host "  Appends to deploy/qemu/run/status-minute.log unless -NoLog."
    Write-Host "  Auto-remediate (orphans, stuck docker pull, VM down): on by default; log: status-remediate.log"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
. (Join-Path $Root "deploy\qemu\lib\Test-KorusQemuProcess.ps1")
$LogPath = Join-Path $RunDir "status-minute.log"
$TickPath = Join-Path $RunDir "status-minute.tick"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
$ApiUrl = "http://127.0.0.1:18080"
$WebUrl = "http://127.0.0.1:19088/"

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

if (-not (Test-Path $RunDir)) { New-Item -ItemType Directory -Path $RunDir -Force | Out-Null }

$tick = 1
if (Test-Path $TickPath) {
    $tick = [int](Get-Content $TickPath -Raw).Trim() + 1
}
Set-Content -Path $TickPath -Value $tick -Encoding ascii -NoNewline

function Test-TcpPort {
    param([int]$Port)
    $r = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    return [bool]$r.TcpTestSucceeded
}

function Get-MeaningfulSerialLines {
    param([string]$Name, [int]$Tail)
    $path = Join-Path $RunDir "$Name-serial.log"
    if (-not (Test-Path $path)) { return @() }
    $raw = Get-Content $path -Tail ([Math]::Max($Tail * 4, 20)) -ErrorAction SilentlyContinue
    $filtered = $raw | Where-Object {
        $_ -and $_ -notmatch 'fd0|GRUB failed boot|^\s*$|^\[\s*\d+\.\d+\]\s*$'
    } | Where-Object {
        $_ -match 'Cloud-init|cloud-init|ERROR|error|failed|gradle|Gradle|docker|Docker|ansible|Ansible|login:|SSH|systemd|Finished|Starting|no space|OOM|pull|Pulling|Building|DONE|korus'
    }
    if ($filtered.Count -eq 0) {
        $filtered = $raw | Where-Object { $_ -and $_ -notmatch 'fd0|GRUB failed boot' } | Select-Object -Last $Tail
    } else {
        $filtered = $filtered | Select-Object -Last $Tail
    }
    return @($filtered)
}

function Get-BootstrapSnippet {
    param([string]$Role, [int]$Port, [string]$HostKey, [int]$Tail = 0)
    if ($Tail -le 0) { $Tail = $BootstrapTail }
    if (-not (Test-Path $Plink) -or -not $HostKey) {
        return @{ Lines = @(); Text = ""; State = "no-ssh" }
    }
    $cmd = "tail -$Tail /var/log/korus-bootstrap.log 2>&1"
    $job = Start-Job {
        param($Plink, $HostKey, $Port, $Cmd)
        & $Plink -batch -hostkey $HostKey -pw korus -P $Port "korus@127.0.0.1" $Cmd 2>&1
    } -ArgumentList $Plink, $HostKey, $Port, $cmd
    $done = Wait-Job $job -Timeout $SshTimeoutSec
    if (-not $done) {
        Stop-Job $job | Out-Null
        Remove-Job $job | Out-Null
        return @{ Lines = @("(SSH timeout)"); Text = ""; State = "ssh-busy" }
    }
    $out = @(Receive-Job $job)
    Remove-Job $job | Out-Null
    $text = ($out | ForEach-Object { "$_" }) -join "`n"
    $state = "running"
    if ($text -match "server stack up done|web stack up done|QEMU server ansible deploy done|QEMU web ansible deploy done") { $state = "complete" }
    elseif ($text -match "gradle|Gradle|installDist|distTar|:runDist|:build|docker build|Step [0-9]+/[0-9]+") { $state = "gradle" }
    elseif ($text -match "waiting for server API") { $state = "wait-api" }
    elseif ($text -match "Downloading|Extracting|Pulling|Pull complete|Download complete") { $state = "docker-pull" }
    elseif ($text -match "ERROR|failed|no space") {
        if ($text -match 'curl: \(22\).*404' -and $text -match 'gradle|Gradle|docker|Pulling|Downloading|Building') {
            $state = "running"
        } else {
            $state = "error"
        }
    }
    $lines = $out | Where-Object { "$_" -match '\S' } | Select-Object -Last $Tail
    return @{ Lines = @($lines); Text = $text; State = $state }
}

function Get-StackHealth {
    $core = $false; $web = $false; $ready = $false
    try {
        $h = Invoke-WebRequest -Uri "$ApiUrl/api/v1/health" -UseBasicParsing -TimeoutSec 8 -ErrorAction Stop
        $core = ($h.StatusCode -eq 200)
    } catch {}
    try {
        $w = Invoke-WebRequest -Uri $WebUrl -UseBasicParsing -TimeoutSec 8 -ErrorAction Stop
        $web = ($w.StatusCode -eq 200)
    } catch {}
    if ($core) {
        try {
            $rd = Invoke-RestMethod -Uri "$ApiUrl/api/v1/health/ready" -TimeoutSec 8 -ErrorAction Stop
            $ready = [bool]$rd.database_ok
        } catch {}
    }
    return @{ Core = $core; Web = $web; Ready = $ready }
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("--- QEMU minute status $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') tick=$tick ---")

$qemuProcs = @(Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Where-Object {
    Test-KorusQemuProcess -ProcessId $_.Id
})
if ($qemuProcs.Count -eq 0) {
    [void]$sb.AppendLine("QEMU: (no korus VMs running)")
} else {
    foreach ($p in $qemuProcs) {
        $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)" -ErrorAction SilentlyContinue).CommandLine
        $serverPidFile = Join-Path $RunDir "server.pid"
        $webPidFile = Join-Path $RunDir "web.pid"
        $n = if ($cmd -match "-name\s+(\S+)") { $Matches[1] } else { "?" }
        if ($n -eq "?" -and (Test-Path $serverPidFile) -and $p.Id -eq [int](Get-Content $serverPidFile -Raw)) { $n = "korus-server" }
        if ($n -eq "?" -and (Test-Path $webPidFile) -and $p.Id -eq [int](Get-Content $webPidFile -Raw)) { $n = "korus-web" }
        $a = if ($cmd -match "-accel\s+(\S+)") { $Matches[1] } else { "?" }
        $ram = [int]($p.WS / 1MB)
        [void]$sb.AppendLine("QEMU: $n PID=$($p.Id) accel=$a RAM=${ram}MB")
    }
}

$ssh21 = Test-TcpPort 12221
$ssh22 = Test-TcpPort 12222
[void]$sb.AppendLine("SSH: :12221=$(if ($ssh21) { 'up' } else { 'down' }) :12222=$(if ($ssh22) { 'up' } else { 'down' })")

$h = Get-StackHealth
[void]$sb.AppendLine("Health: core=$($h.Core) web=$($h.Web) ready=$($h.Ready)")

$activityParts = @()
$bootstrapStates = @()
$serverBootstrapText = ""
$webBootstrapText = ""
$serverHostKey = ""
$webHostKey = ""
$serverBootstrapTail = if ($NoRemediate) { $BootstrapTail } else { [Math]::Max($BootstrapTail, 12) }

foreach ($role in @("server", "web")) {
    $port = if ($role -eq "server") { 12221 } else { 12222 }
    $serial = Get-MeaningfulSerialLines -Name $role -Tail $SerialTail
    [void]$sb.AppendLine("serial-$role (last $($serial.Count)):")
    if ($serial.Count -eq 0) {
        [void]$sb.AppendLine("  (no log yet)")
    } else {
        foreach ($line in $serial) { [void]$sb.AppendLine("  $(Strip-Ansi $line)") }
    }
    if ($serial -match "Cloud-init.*finished|cloud-init.*finished") { $activityParts += "${role}: cloud-init done" }
    elseif ($serial -match "Cloud-init|cloud-init") { $activityParts += "${role}: cloud-init" }

    $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "$role-serial.log") -Role $role -SshPort $port
    $tail = if ($role -eq "server") { $serverBootstrapTail } else { $BootstrapTail }
    $bs = Get-BootstrapSnippet -Role $role -Port $port -HostKey $hk -Tail $tail
    $bootstrapStates += $bs.State
    if ($role -eq "server") {
        $serverBootstrapText = $bs.Text
        $serverHostKey = $hk
    } elseif ($role -eq "web") {
        $webBootstrapText = $bs.Text
        $webHostKey = $hk
    }
    [void]$sb.AppendLine("bootstrap-${role}:")
    if ($bs.Lines.Count -eq 0) {
        [void]$sb.AppendLine("  (unavailable)")
    } else {
        foreach ($line in $bs.Lines) { [void]$sb.AppendLine("  $(Strip-Ansi $line)") }
    }
}

$activity = "unknown"
if (-not $qemuProcs.Count) { $activity = "VMs stopped" }
elseif ($h.Core -and $h.Web -and $h.Ready) { $activity = "stack ready" }
elseif ($bootstrapStates -contains "complete" -and -not $h.Ready) { $activity = "bootstrap done, health pending" }
elseif ($bootstrapStates -contains "gradle") { $activity = "gradle / docker build" }
elseif ($bootstrapStates -contains "docker-pull") { $activity = "docker pull" }
elseif ($bootstrapStates -contains "wait-api") { $activity = "web waiting for API" }
elseif ($bootstrapStates -contains "ssh-busy") { $activity = "guest busy (SSH slow)" }
elseif ($bootstrapStates -contains "error") { $activity = "bootstrap errors" }
elseif ($activityParts -match "cloud-init") { $activity = ($activityParts -join "; ") }
elseif ($ssh21 -or $ssh22) { $activity = "SSH up, stack starting" }
else { $activity = "VM booting" }

. (Join-Path $Root "deploy\qemu\lib\Get-KorusQemuLoadingState.ps1")
$loadingState = Get-KorusQemuLoadingState -ServerBootstrapText $serverBootstrapText `
    -WebBootstrapText $webBootstrapText -Activity $activity -BootstrapStates $bootstrapStates `
    -ServerHostKey $serverHostKey -WebHostKey $webHostKey
if ($loadingState.Loading) {
    $activity = "loading: $($loadingState.Kind) ($($loadingState.Detail))"
}
[void]$sb.AppendLine("Activity: $activity")

$preview = $sb.ToString().TrimEnd()
$qemuLineTexts = @($preview -split "`n" | Where-Object { $_ -match '^QEMU:' })
if ($qemuLineTexts.Count -eq 0) { $qemuLineTexts = @("QEMU: (none running)") }

$remediateSummary = ""
if (-not $NoRemediate) {
    . (Join-Path $Root "deploy\qemu\lib\Invoke-KorusQemuAutoRemediate.ps1")
    $rem = Invoke-KorusQemuAutoRemediate -RunDir $RunDir -Root $Root -Health $h `
        -Activity $activity -BootstrapStates $bootstrapStates `
        -ServerBootstrapText $serverBootstrapText -WebBootstrapText $webBootstrapText `
        -ServerHostKey $serverHostKey -WebHostKey $webHostKey `
        -GuestFixAfterMinutes $StuckGuestFixMinutes `
        -RestartAfterMinutes $StuckRestartMinutes `
        -CooldownMinutes $RemediateCooldownMinutes
    if ($rem.Summary) {
        $remediateSummary = $rem.Summary
    }
}

$block = $sb.ToString().TrimEnd()
Write-Output $block

if ($remediateSummary) {
    $remLine = "Remediate: $remediateSummary"
    Write-Output $remLine
}

. (Join-Path $Root "deploy\qemu\lib\Get-KorusQemuMinuteReport.ps1")
. (Join-Path $Root "deploy\qemu\lib\Get-KorusQemuStackProfile.ps1")
$stackProfile = Get-KorusQemuStackProfile
$report = Get-KorusQemuMinuteReport -Tick $tick -QemuLines $qemuLineTexts -Health $h `
    -Ssh21 $ssh21 -Ssh22 $ssh22 -Activity $activity -BootstrapStates $bootstrapStates `
    -ServerBootstrapText $serverBootstrapText -RemediateSummary $remediateSummary `
    -LoadingState $loadingState -StackProfile $stackProfile
$report = Convert-KorusQemuReportToRussian -Report $report

$snapshotPath = Join-Path $RunDir "status-minute.snapshot.json"
$report | ConvertTo-Json -Depth 6 | Set-Content -Path $snapshotPath -Encoding utf8

Write-Output "--- CHAT (ru) ---"
Write-Output $report.summaryRu
if ($report.issues.Count -gt 0) {
    Write-Output "--- ISSUES ---"
    foreach ($iss in $report.issues) {
        Write-Output "  [$($iss.severity)] $($iss.message) -> $($iss.action)"
    }
}

if (-not $NoLog) {
    $logBlock = $block
    if ($remediateSummary) { $logBlock += "`nRemediate: $remediateSummary" }
    $logBlock += "`n--- CHAT (ru) ---`n$($report.summaryRu)"
    if ($report.issues.Count -gt 0) {
        foreach ($iss in $report.issues) {
            $logBlock += "`n  [$($iss.severity)] $($iss.message)"
        }
    }
    Add-Content -Path $LogPath -Value ($logBlock + "`n`n") -Encoding utf8
}

if ($Once) { exit 0 }


