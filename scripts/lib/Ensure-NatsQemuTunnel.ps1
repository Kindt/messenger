# Host-side NATS tunnel to korus-server guest Docker (4222 -> localhost:14222).
function Ensure-NatsQemuTunnel {
    param(
        [int]$Port = 14222,
        [int]$SshPort = 12221
    )
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) { return "nats://127.0.0.1:$Port" }

    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { throw "PuTTY plink not found for NATS tunnel" }

    $runDir = Join-Path $Root "deploy\qemu\run"
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort $SshPort
    if (-not $hostKey) { throw "server SSH host key not ready for NATS tunnel" }

    Write-Host "  starting NATS tunnel :$Port -> server guest :4222..." -ForegroundColor DarkGray
    $argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $SshPort -L ${Port}:127.0.0.1:4222 korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "NATS tunnel plink exited early (code=$($proc.ExitCode))" }

    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $tcp2 = Test-NetConnection -ComputerName 127.0.0.1 -Port $Port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if ($tcp2.TcpTestSucceeded) { return "nats://127.0.0.1:$Port" }
        if ($proc.HasExited) { throw "NATS tunnel plink exited before :$Port opened (code=$($proc.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "NATS tunnel :$Port not open"
}
