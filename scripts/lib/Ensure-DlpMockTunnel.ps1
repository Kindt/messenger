# Host-side SSH forward for dlp-mock on korus-integrations guest Docker (8098 -> localhost:18098).
function Ensure-DlpMockTunnel {
    param(
        [int]$HostPort = 18098,
        [int]$GuestPort = 8098,
        [int]$SshPort = 12223
    )

    $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $HostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
    if ($ok) { return "http://127.0.0.1:${HostPort}" }

    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port $SshPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if (-not $tcp.TcpTestSucceeded) { throw "integrations SSH :$SshPort not ready (run qemu-integrations-up.ps1)" }

    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { throw "PuTTY plink not found for DLP mock tunnel" }

    $runDir = Join-Path $Root "deploy\qemu\run"
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "integrations-serial.log") -Role integrations -SshPort $SshPort
    if (-not $hostKey) { throw "integrations SSH host key not ready for DLP mock tunnel" }

    $portTag = ":$HostPort"
    Get-CimInstance Win32_Process -Filter "name='plink.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match [regex]::Escape($portTag) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    Write-Host "  starting DLP mock tunnel :$HostPort -> guest :$GuestPort ..." -ForegroundColor DarkGray
    $argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $SshPort -L ${HostPort}:127.0.0.1:${GuestPort} korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "DLP mock tunnel plink exited early (code=$($proc.ExitCode))" }

    $deadline = (Get-Date).AddSeconds(25)
    while ((Get-Date) -lt $deadline) {
        $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $HostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
        if ($ok) { return "http://127.0.0.1:${HostPort}" }
        if ($proc.HasExited) { throw "DLP mock tunnel plink exited before :$HostPort opened (code=$($proc.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "DLP mock tunnel :$HostPort not open"
}
