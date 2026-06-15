# Non-blocking background qemu-redeploy for one guest (used by auto-remediate).
function Start-KorusQemuGuestRedeploy {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("server", "web")]
        [string]$Role,
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [string]$Reason = "",
        [switch]$Force
    )

    $lockLib = Join-Path $Root "deploy\qemu\lib\Korus-QemuRedeployLock.ps1"
    if (Test-Path $lockLib) { . $lockLib }

    if (Test-KorusRedeployLockActive -RunDir $RunDir) {
        $lock = Get-KorusRedeployLockPath -RunDir $RunDir -Role $Role
        $age = if (Test-Path -LiteralPath $lock) {
            [math]::Round(((Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime).TotalMinutes, 1)
        } else { 0 }
        return @{ Started = $false; Summary = "redeploy-$Role already running (${age}m)" }
    }

    $redeploy = Join-Path $Root "scripts\qemu-redeploy.ps1"
    $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $redeploy)
    if ($Role -eq "server") { $args += "-ServerOnly" } else { $args += "-WebOnly" }
    if ($Force) { $args += "-Force" }

    $log = Join-Path $RunDir "status-remediate.log"
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [redeploy-$Role] spawn background reason=$Reason" | Add-Content -Path $log -Encoding utf8

    Start-Process -FilePath "powershell.exe" -ArgumentList $args -WorkingDirectory $Root -WindowStyle Hidden | Out-Null
    return @{ Started = $true; Summary = "background redeploy-${Role}: $Reason" }
}
