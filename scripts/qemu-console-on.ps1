# Enable bootstrap/Docker log on VGA (GTK window) for already-running VMs.
param(
    [ValidateSet("server", "web", "all")]
    [string]$Role = "all",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
$env:KORUS_DEBUG_SESSION = "6eddca"

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $Root "deploy\qemu\lib\Write-KorusDebugLog.ps1")
. (Join-Path $Root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-console-on.ps1 [-Role server|web|all]"
    exit 0
}
if (-not (Test-Path $Plink)) { throw "PuTTY plink not found: $Plink" }

Start-KorusRepoHttp | Out-Null

function Enable-ConsoleMonitor {
    param([string]$Name, [int]$Port)
    Write-Host "[console] $Name port $Port" -ForegroundColor DarkGray
    $serial = Join-Path $RunDir "$Name-serial.log"
    $hk = Get-KorusEd25519HostKey -SerialPath $serial -Role $Name -SshPort $Port
    if (-not $hk) {
        Write-Host "[SKIP] $Name SSH host key not ready" -ForegroundColor Yellow
        return $false
    }
    Update-KorusGuestRepo -Role $Name -SshPort $Port -HostKey $hk -Plink $Plink | Out-Null
    Write-Host "Enabling VGA bootstrap monitor on $Name..." -ForegroundColor Cyan
    $cmd = @"
sudo timeout 120 sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-console-setup.sh $Name
echo setup-exit:$?
sudo systemctl is-active korus-console-tail.service
sudo journalctl -u korus-console-tail.service -n 5 --no-pager 2>/dev/null || true
"@
    $code = 1
    try {
        Invoke-PlinkShell -Plink $Plink -HostKey $hk -Port $Port -Script $cmd
        $code = 0
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
    Write-KorusDebugLog -Location "qemu-console-on.ps1" -Message "console setup" -HypothesisId "H7" -Data @{
        Role = $Name; ok = ($code -eq 0); exitCode = $code
    }
    if ($code -eq 0) {
        Write-Host "[OK] $Name GTK window shows bootstrap log" -ForegroundColor Green
        return $true
    }
    Write-Host "[FAIL] $Name console setup exit=$code" -ForegroundColor Red
    return $false
}

$anyOk = $false
foreach ($r in $(if ($Role -eq "all") { @("server", "web") } else { @($Role) })) {
    $port = if ($r -eq "server") { 12221 } else { 12222 }
    if (Enable-ConsoleMonitor -Name $r -Port $port) { $anyOk = $true }
}
if (-not $anyOk) { exit 1 }
