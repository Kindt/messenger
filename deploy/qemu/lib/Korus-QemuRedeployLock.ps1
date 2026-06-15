# Shared qemu-redeploy lock: PID-aware stale detection (W1-B1.2).

function Get-KorusRedeployLockPath {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("server", "web")][string]$Role
    )
    Join-Path $RunDir "qemu-redeploy-$Role.lock"
}

function Set-KorusRedeployLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("server", "web")][string]$Role,
        [int]$ProcessId = $PID
    )
    $lock = Get-KorusRedeployLockPath -RunDir $RunDir -Role $Role
    Set-Content -LiteralPath $lock -Value @((Get-Date).ToString("o"), "$ProcessId") -Encoding ascii
}

function Clear-KorusStaleRedeployLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("server", "web")][string]$Role,
        [int]$MaxAgeMin = 45,
        [double]$GraceSec = 30,
        [switch]$Quiet
    )
    $lock = Get-KorusRedeployLockPath -RunDir $RunDir -Role $Role
    if (-not (Test-Path -LiteralPath $lock)) { return $false }

    $item = Get-Item -LiteralPath $lock
    $ageMin = ((Get-Date) - $item.LastWriteTime).TotalMinutes
    $lines = @(Get-Content -LiteralPath $lock -ErrorAction SilentlyContinue)
    $procId = 0
    if ($lines.Count -ge 2 -and "$($lines[1])" -match '^\d+$') { $procId = [int]$lines[1] }

    $alive = $false
    if ($procId -gt 0) {
        $alive = $null -ne (Get-Process -Id $procId -ErrorAction SilentlyContinue)
    }
    if ($alive) { return $false }

    $stale = $false
    if ($ageMin -ge $MaxAgeMin) { $stale = $true }
    elseif ($procId -gt 0) { $stale = $true }
    elseif ($ageMin -ge ($GraceSec / 60.0)) { $stale = $true }

    if (-not $stale) { return $false }

    Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
    if (-not $Quiet) {
        Write-Host "Removing stale qemu-redeploy-$Role lock (age $([math]::Round($ageMin,1))m pid=$procId alive=$alive)" -ForegroundColor Yellow
    }
    return $true
}

function Test-KorusRedeployLockActive {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [int]$MaxAgeMin = 45
    )
    $active = $false
    foreach ($role in @("server", "web")) {
        Clear-KorusStaleRedeployLock -RunDir $RunDir -Role $role -MaxAgeMin $MaxAgeMin -Quiet | Out-Null
        if (Test-Path -LiteralPath (Get-KorusRedeployLockPath -RunDir $RunDir -Role $role)) {
            $active = $true
        }
    }
    return $active
}

function Enter-KorusRedeployLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("server", "web")][string]$Role,
        [int]$ProcessId = $PID
    )
    Clear-KorusStaleRedeployLock -RunDir $RunDir -Role $Role | Out-Null
    $lock = Get-KorusRedeployLockPath -RunDir $RunDir -Role $Role
    if (Test-Path -LiteralPath $lock) { return $false }
    Set-KorusRedeployLock -RunDir $RunDir -Role $Role -ProcessId $ProcessId
    return $true
}

function Exit-KorusRedeployLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("server", "web")][string]$Role
    )
    Remove-Item -LiteralPath (Get-KorusRedeployLockPath -RunDir $RunDir -Role $Role) -Force -ErrorAction SilentlyContinue
}
