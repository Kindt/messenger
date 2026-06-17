# PID-aware lock: one long guest SSH/plink task per VM role (integrations|server|web).

function Get-KorusGuestTaskLockPath {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest
    )
    Join-Path $RunDir "guest-task-$Guest.lock"
}

function Get-KorusGuestTaskLockMaxAgeMin {
    param([ValidateSet("integrations", "server", "web")][string]$Guest)
    switch ($Guest) {
        "integrations" { 120 }
        "server" { 90 }
        "web" { 45 }
    }
}

function Read-KorusGuestTaskLock {
    param([string]$LockPath)
    if (-not (Test-Path -LiteralPath $LockPath)) { return $null }
    $lines = @(Get-Content -LiteralPath $LockPath -ErrorAction SilentlyContinue)
    $started = if ($lines.Count -ge 1) { "$($lines[0])".Trim() } else { "" }
    $procId = 0
    $task = ""
    if ($lines.Count -ge 2 -and "$($lines[1])" -match '^\d+$') { $procId = [int]$lines[1] }
    if ($lines.Count -ge 3) { $task = "$($lines[2])".Trim() }
    return [PSCustomObject]@{
        Started = $started; ProcessId = $procId; TaskName = $task; Path = $LockPath
    }
}

function Clear-KorusStaleGuestTaskLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest,
        [int]$MaxAgeMin = 0,
        [switch]$Quiet
    )
    if ($MaxAgeMin -le 0) { $MaxAgeMin = Get-KorusGuestTaskLockMaxAgeMin -Guest $Guest }
    $lock = Get-KorusGuestTaskLockPath -RunDir $RunDir -Guest $Guest
    if (-not (Test-Path -LiteralPath $lock)) { return $false }

    $info = Read-KorusGuestTaskLock -LockPath $lock
    $ageMin = ((Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime).TotalMinutes
    $alive = $false
    if ($info.ProcessId -gt 0) {
        $alive = $null -ne (Get-Process -Id $info.ProcessId -ErrorAction SilentlyContinue)
    }
    if ($alive) { return $false }

    $stale = ($ageMin -ge $MaxAgeMin) -or ($info.ProcessId -gt 0)
    if (-not $stale) { return $false }

    Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
    if (-not $Quiet) {
        Write-Host "Removed stale guest-task-$Guest lock (task=$($info.TaskName) age=$([math]::Round($ageMin,1))m pid=$($info.ProcessId))" -ForegroundColor Yellow
    }
    return $true
}

function Test-KorusGuestTaskLockActive {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest
    )
    Clear-KorusStaleGuestTaskLock -RunDir $RunDir -Guest $Guest -Quiet | Out-Null
    return Test-Path -LiteralPath (Get-KorusGuestTaskLockPath -RunDir $RunDir -Guest $Guest)
}

function Enter-KorusGuestTaskLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest,
        [Parameter(Mandatory)][string]$TaskName,
        [int]$ProcessId = $PID,
        [switch]$Force
    )
    Clear-KorusStaleGuestTaskLock -RunDir $RunDir -Guest $Guest | Out-Null
    $lock = Get-KorusGuestTaskLockPath -RunDir $RunDir -Guest $Guest
    if (Test-Path -LiteralPath $lock) {
        if (-not $Force) { return $false }
        Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
        Write-Host "[WARN] Forced guest-task-$Guest lock (previous task overwritten)" -ForegroundColor Yellow
    }
    Set-Content -LiteralPath $lock -Value @(
        (Get-Date).ToString("o"),
        "$ProcessId",
        $TaskName
    ) -Encoding ascii
    return $true
}

function Exit-KorusGuestTaskLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest,
        [int]$ProcessId = $PID
    )
    $lock = Get-KorusGuestTaskLockPath -RunDir $RunDir -Guest $Guest
    if (-not (Test-Path -LiteralPath $lock)) { return }
    $info = Read-KorusGuestTaskLock -LockPath $lock
    if ($info.ProcessId -eq 0 -or $info.ProcessId -eq $ProcessId) {
        Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue
    }
}

function Assert-KorusGuestTaskLock {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest,
        [Parameter(Mandatory)][string]$TaskName,
        [switch]$Force
    )
    if (Enter-KorusGuestTaskLock -RunDir $RunDir -Guest $Guest -TaskName $TaskName -Force:$Force) {
        return
    }
    $lock = Get-KorusGuestTaskLockPath -RunDir $RunDir -Guest $Guest
    $info = Read-KorusGuestTaskLock -LockPath $lock
    $age = [math]::Round(((Get-Date) - (Get-Item -LiteralPath $lock).LastWriteTime).TotalMinutes, 1)
    throw "Guest task busy: $Guest task='$($info.TaskName)' pid=$($info.ProcessId) age=${age}m. Wait or use -ForceLock."
}

function Invoke-KorusGuestTaskLocked {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][ValidateSet("integrations", "server", "web")]
        [string]$Guest,
        [Parameter(Mandatory)][string]$TaskName,
        [Parameter(Mandatory)][scriptblock]$Action,
        [switch]$ForceLock
    )
    Assert-KorusGuestTaskLock -RunDir $RunDir -Guest $Guest -TaskName $TaskName -Force:$ForceLock
    try {
        & $Action
    } finally {
        Exit-KorusGuestTaskLock -RunDir $RunDir -Guest $Guest
    }
}
