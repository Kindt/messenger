# Host-side SSH forward for Solr on korus-server guest Docker (8983 -> localhost:18983).
function Ensure-GuestSolrTunnel {
    param(
        [int]$HostPort = 18983,
        [int]$GuestPort = 8983,
        [int]$SshPort = 12221
    )

    $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $HostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
    if ($ok) { return "http://127.0.0.1:${HostPort}" }

    $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { throw "PuTTY plink not found for Solr tunnel" }

    $runDir = Join-Path $Root "deploy\qemu\run"
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort $SshPort
    if (-not $hostKey) { throw "server SSH host key not ready for Solr tunnel" }

    $portTag = ":$HostPort"
    Get-CimInstance Win32_Process -Filter "name='plink.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match [regex]::Escape($portTag) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    Write-Host "  starting Solr tunnel :$HostPort -> guest :$GuestPort ..." -ForegroundColor DarkGray
    $argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P $SshPort -L ${HostPort}:127.0.0.1:${GuestPort} korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) { throw "Solr tunnel plink exited early (code=$($proc.ExitCode))" }

    $deadline = (Get-Date).AddSeconds(25)
    while ((Get-Date) -lt $deadline) {
        $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $HostPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue).TcpTestSucceeded
        if ($ok) { return "http://127.0.0.1:${HostPort}" }
        if ($proc.HasExited) { throw "Solr tunnel plink exited before :$HostPort opened (code=$($proc.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "Solr tunnel :$HostPort not open"
}
