# Tail QEMU guest logs and print a short analysis (serial + bootstrap via SSH).
param(
    [ValidateSet("server", "web", "all")]
    [string]$Role = "all",
    [int]$Tail = 20,
    [int]$SshTimeoutSec = 25,
    [switch]$Follow,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\qemu-logs.ps1 [-Role server|web|all] [-Tail N] [-Follow]"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Get-HostKeyFromSerial {
    param([string]$SerialPath, [string]$Role, [int]$SshPort)
    return Get-KorusEd25519HostKey -SerialPath $SerialPath -Role $Role -SshPort $SshPort
}

function Show-SerialTail {
    param([string]$Name)
    $path = Join-Path $RunDir "$Name-serial.log"
    Write-Host "`n=== serial: $Name ===" -ForegroundColor Cyan
    if (-not (Test-Path $path)) {
        Write-Host "  (no file)" -ForegroundColor Yellow
        return
    }
    $lines = Get-Content $path -Tail $Tail -ErrorAction SilentlyContinue
    $lines | ForEach-Object { Write-Host $_ }
    $ci = $lines | Where-Object { $_ -match "Cloud-init.*finished" }
    $err = $lines | Where-Object { $_ -match "ERROR|failed|no space" -and $_ -notmatch "fd0|GRUB failed boot" }
    if ($ci) { Write-Host "  >> cloud-init: finished" -ForegroundColor Green }
    if ($err) { Write-Host "  >> serial warnings/errors: $($err.Count) line(s)" -ForegroundColor Yellow }
}

function Show-Bootstrap {
    param(
        [string]$Name,
        [int]$Port,
        [string]$HostKey,
        [int]$SshTimeout
    )
    Write-Host "`n=== bootstrap: $Name (SSH :$Port) ===" -ForegroundColor Cyan
    if (-not (Test-Path $Plink)) {
        Write-Host "  plink not found; skip SSH bootstrap" -ForegroundColor Yellow
        return
    }
    if (-not $HostKey) {
        Write-Host "  host key not in serial log yet" -ForegroundColor Yellow
        return
    }
    $cmd = "tail -$Tail /var/log/korus-bootstrap.log 2>&1; echo '---'; df -h / | tail -1; sudo docker ps -a --format '{{.Names}} {{.Status}}' 2>&1 | head -12"
    $job = Start-Job {
        param($Plink, $HostKey, $Port, $Cmd)
        & $Plink -batch -hostkey $HostKey -pw korus -P $Port "korus@127.0.0.1" $Cmd 2>&1
    } -ArgumentList $Plink, $HostKey, $Port, $cmd
    $done = Wait-Job $job -Timeout $SshTimeoutSec
    if (-not $done) {
        Stop-Job $job -Force
        Remove-Job $job -Force
        Write-Host "  SSH timeout (guest busy or Gradle build)" -ForegroundColor Yellow
        return
    }
    $out = Receive-Job $job
    Remove-Job $job -Force
    $out | ForEach-Object { Write-Host $_ }

    $text = $out -join "`n"
    if ($text -match "server stack up done|web stack up done") {
        Write-Host "  >> bootstrap: COMPLETE" -ForegroundColor Green
    } elseif ($text -match "gradle|installDist|distTar|Building|DONE") {
        Write-Host "  >> bootstrap: docker build in progress (Gradle)" -ForegroundColor Yellow
    } elseif ($text -match "waiting for server API") {
        Write-Host "  >> bootstrap: waiting for server API on 10.0.2.2:18080" -ForegroundColor Yellow
    } elseif ($text -match "ERROR|failed|no space") {
        Write-Host "  >> bootstrap: errors detected" -ForegroundColor Red
    } elseif ($text -match "Downloading|Extracting|Pulling") {
        Write-Host "  >> bootstrap: pulling Docker images" -ForegroundColor Yellow
    } else {
        Write-Host "  >> bootstrap: running or log empty" -ForegroundColor DarkGray
    }
}

function Show-NginxLbLogs {
    param(
        [int]$Port,
        [string]$HostKey,
        [int]$Tail,
        [int]$SshTimeout
    )
    Write-Host "`n=== nginx lb (docker logs, web guest :$Port) ===" -ForegroundColor Cyan
    if (-not (Test-Path $Plink)) {
        Write-Host "  plink not found; skip lb logs" -ForegroundColor Yellow
        return
    }
    if (-not $HostKey) {
        Write-Host "  host key not in serial log yet" -ForegroundColor Yellow
        return
    }
    # Official nginx image symlinks /var/log/nginx/*.log -> stdout/stderr; tail inside exec blocks forever.
    $cmd = "sudo docker logs --tail $Tail korus-web-lb-1 2>&1 | tail -n $Tail"
    $job = Start-Job {
        param($Plink, $HostKey, $Port, $Cmd)
        & $Plink -batch -hostkey $HostKey -pw korus -P $Port "korus@127.0.0.1" $Cmd 2>&1
    } -ArgumentList $Plink, $HostKey, $Port, $cmd
    $done = Wait-Job $job -Timeout $SshTimeout
    if (-not $done) {
        Stop-Job $job -Force
        Remove-Job $job -Force
        Write-Host "  SSH timeout fetching lb docker logs" -ForegroundColor Yellow
        return
    }
    $out = Receive-Job $job
    Remove-Job $job -Force
    if (-not $out) {
        Write-Host "  (no lb container logs or korus-web-lb-1 missing)" -ForegroundColor Yellow
        return
    }
    $out | ForEach-Object { Write-Host $_ }
    $text = $out -join "`n"
    if ($text -match "worker_connections exceed open file resource limit") {
        Write-Host "  >> lb: worker_connections/rlimit warning (rebuild lb: qemu-redeploy -WebOnly -Force -Rebuild)" -ForegroundColor Yellow
    }
    if ($text -match "connect\(\) failed \(111: Connection refused\)|no live upstreams") {
        Write-Host "  >> lb: transient upstream errors during redeploy/restart (expected)" -ForegroundColor DarkGray
    } elseif ($text -match "\[error\]") {
        Write-Host "  >> lb: nginx errors in recent logs" -ForegroundColor Yellow
    }
}

Write-Host "=== QEMU launch log analysis $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -ForegroundColor Green
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | ForEach-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
    $n = if ($cmd -match "-name\s+(\S+)") { $Matches[1] } else { "?" }
    $a = if ($cmd -match "-accel\s+(\S+)") { $Matches[1] } else { "?" }
    $d = if ($cmd -match "-display\s+(\S+)") { ($Matches[1] -split ',')[0] } else { "none" }
    Write-Host "  $n PID=$($_.Id) accel=$a display=$d RAM=$([int]($_.WS/1MB))MB"
}
if (-not (Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)) {
    Write-Host "  No QEMU VMs running" -ForegroundColor Yellow
}

$roles = if ($Role -eq "all") { @("server", "web") } else { @($Role) }
foreach ($r in $roles) {
    Show-SerialTail -Name $r
    $port = if ($r -eq "server") { 12221 } else { 12222 }
    $hk = Get-HostKeyFromSerial -SerialPath (Join-Path $RunDir "$r-serial.log") -Role $r -SshPort $port
    Show-Bootstrap -Name $r -Port $port -HostKey $hk -SshTimeout $SshTimeoutSec
    if ($r -eq "web") {
        Show-NginxLbLogs -Port $port -HostKey $hk -Tail $Tail -SshTimeout $SshTimeoutSec
    }
}

Write-Host "`nHealth:" -ForegroundColor Cyan
foreach ($url in @("http://127.0.0.1:18080/api/v1/health", "http://127.0.0.1:19088/")) {
    try {
        $r = Invoke-WebRequest -Uri $url -TimeoutSec 8 -UseBasicParsing -ErrorAction Stop
        Write-Host "  [OK] $url -> $($r.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host "  [--] $url -> not ready" -ForegroundColor Yellow
    }
}

if ($Follow) {
    Write-Host "`nFollowing server-serial (Ctrl+C to stop)..." -ForegroundColor DarkGray
    Get-Content (Join-Path $RunDir "server-serial.log") -Wait -Tail 5
}
