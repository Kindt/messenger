# Host-side SSH forwards for worker Prometheus ports on korus-server guest Docker.
# export-replay :9193 -> localhost:19193, retention :9192 -> localhost:19192
function Ensure-GuestWorkerMetricsTunnels {
    param(
        [int]$ExportReplayHostPort = 19193,
        [int]$RetentionHostPort = 19192,
        [int]$GuestExportPort = 9193,
        [int]$GuestRetentionPort = 9192,
        [int]$SshPort = 12221
    )

    $expOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port $ExportReplayHostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
    $retOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port $RetentionHostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
    if ($expOk -and $retOk) { return }

    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { throw "PuTTY plink not found for worker metrics tunnel" }

    $runDir = Join-Path $Root "deploy\qemu\run"
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort $SshPort
    if (-not $hostKey) { throw "server SSH host key not ready for worker metrics tunnel" }

    $portTag = ":$ExportReplayHostPort"
    Get-CimInstance Win32_Process -Filter "name='plink.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match [regex]::Escape($portTag) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    Write-Host "  starting worker metrics tunnels :$ExportReplayHostPort->:$GuestExportPort, :$RetentionHostPort->:$GuestRetentionPort ..." -ForegroundColor DarkGray
    $argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $SshPort -L ${ExportReplayHostPort}:127.0.0.1:${GuestExportPort} -L ${RetentionHostPort}:127.0.0.1:${GuestRetentionPort} korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "worker metrics tunnel plink exited early (code=$($proc.ExitCode))" }

    $deadline = (Get-Date).AddSeconds(25)
    while ((Get-Date) -lt $deadline) {
        $expOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port $ExportReplayHostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
        $retOk = (Test-NetConnection -ComputerName 127.0.0.1 -Port $RetentionHostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
        if ($expOk -and $retOk) { return }
        if ($proc.HasExited) { throw "worker metrics tunnel plink exited before ports opened (code=$($proc.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "worker metrics tunnels not open (:$ExportReplayHostPort / :$RetentionHostPort)"
}
