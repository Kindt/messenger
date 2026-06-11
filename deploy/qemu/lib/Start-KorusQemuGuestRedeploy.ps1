# Non-blocking background qemu-redeploy for one guest (used by auto-remediate).
function Start-KorusQemuGuestRedeploy {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("server", "web")]
        [string]$Role,
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [string]$Reason = ""
    )

    $lock = Join-Path $RunDir "qemu-redeploy-$Role.lock"
    if (Test-Path $lock) {
        $age = ((Get-Date) - (Get-Item $lock).LastWriteTime).TotalMinutes
        if ($age -lt 25) { return @{ Started = $false; Summary = "redeploy-$Role already running (${age}m)" } }
        Remove-Item $lock -Force -ErrorAction SilentlyContinue
    }

    Set-Content -Path $lock -Value ((Get-Date).ToString("o")) -Encoding ascii
    $redeploy = Join-Path $Root "scripts\qemu-redeploy.ps1"
    $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $redeploy)
    if ($Role -eq "server") { $args += "-ServerOnly" } else { $args += "-WebOnly" }

    $log = Join-Path $RunDir "status-remediate.log"
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [redeploy-$Role] spawn background reason=$Reason" | Add-Content -Path $log -Encoding utf8

    Start-Process -FilePath "powershell.exe" -ArgumentList $args -WorkingDirectory $Root -WindowStyle Hidden | Out-Null
    return @{ Started = $true; Summary = "background redeploy-${Role}: $Reason" }
}
