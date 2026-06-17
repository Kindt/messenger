function Get-KorusQemuHostMemoryMb {
    $cs = Get-CimInstance Win32_ComputerSystem -ErrorAction SilentlyContinue
    if (-not $cs -or -not $cs.TotalPhysicalMemory) {
        return 16384
    }
    return [int][Math]::Floor($cs.TotalPhysicalMemory / 1MB)
}

function Get-KorusRunningKorusVmMemoryMb {
    $total = 0
    Get-CimInstance Win32_Process -Filter "name='qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue |
        ForEach-Object {
            $cmd = $_.CommandLine
            if ($cmd -notmatch '\bkorus-(server|web|integrations)\b') { return }
            if ($cmd -match '\s-m\s+(\d+)') {
                $total += [int]$Matches[1]
            }
        }
    return $total
}

function Test-KorusQemuPeerVmsRunning {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web", "integrations")]
        [string]$Role
    )
    $pattern = switch ($Role) {
        "integrations" { "korus-(server|web)" }
        default { $null }
    }
    if (-not $pattern) { return $false }
    return [bool](Get-CimInstance Win32_Process -Filter "name='qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match $pattern })
}

function Get-KorusQemuVmMemoryMb {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web", "integrations")]
        [string]$Role
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")

    $envOverride = switch ($Role) {
        "server" { $env:KORUS_QEMU_SERVER_MEMORY_MB }
        "web" { $env:KORUS_QEMU_WEB_MEMORY_MB }
        "integrations" { $env:KORUS_QEMU_INTEGRATIONS_MEMORY_MB }
    }
    if ($envOverride -and $envOverride -match '^\d+$') {
        return [Math]::Max(1024, [int]$envOverride)
    }

    $threeVm = ($env:KORUS_QEMU_THREE_VM -eq "1")

    $base = switch ($Role) {
        "server" {
            if ($threeVm) { $KorusQemuThreeVmServerMemoryMb } else { $KorusQemuServerMemoryMb }
        }
        "web" {
            if ($threeVm) { $KorusQemuThreeVmWebMemoryMb } else { $KorusQemuWebMemoryMb }
        }
        "integrations" {
            if ($env:KORUS_QEMU_INTEGRATIONS_HEAVY -eq "1") {
                $KorusQemuIntegrationsMemoryMbHeavy
            } elseif ($threeVm -or (Test-KorusQemuPeerVmsRunning -Role integrations)) {
                $KorusQemuIntegrationsMemoryMbDev
            } else {
                $KorusQemuIntegrationsMemoryMb
            }
        }
    }

    if ($Role -ne "integrations") {
        return $base
    }

    $hostMb = Get-KorusQemuHostMemoryMb
    $runningMb = Get-KorusRunningKorusVmMemoryMb
    $hostReserveMb = if ($env:KORUS_QEMU_HOST_RESERVE_MB -match '^\d+$') {
        [int]$env:KORUS_QEMU_HOST_RESERVE_MB
    } else {
        2048
    }
    $budget = $hostMb - $runningMb - $hostReserveMb
    $floor = $KorusQemuIntegrationsMemoryMbMin
    if ($budget -lt $floor) {
        Write-Host "integrations: host RAM budget ${budget}MB < min ${floor}MB (host ${hostMb}MB, peers ${runningMb}MB). Restart with KORUS_QEMU_THREE_VM=1." -ForegroundColor Red
        return $floor
    }
    if ($budget -lt $base) {
        $use = [Math]::Max($floor, $budget)
        Write-Host "integrations RAM ${base}MB -> ${use}MB (host ~${hostMb}MB, peers ~${runningMb}MB, reserve ${hostReserveMb}MB)" -ForegroundColor Yellow
        return $use
    }
    return $base
}

function Test-KorusQemuIntegrationsMemoryFeasible {
    param([int]$MemoryMb)
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $hostMb = Get-KorusQemuHostMemoryMb
    $runningMb = Get-KorusRunningKorusVmMemoryMb
    $reserve = if ($env:KORUS_QEMU_HOST_RESERVE_MB -match '^\d+$') { [int]$env:KORUS_QEMU_HOST_RESERVE_MB } else { 2048 }
    $budget = $hostMb - $runningMb - $reserve
    if ($MemoryMb -le $budget -and $budget -ge $KorusQemuIntegrationsMemoryMbMin) {
        return $true
    }
    Write-Host "Host RAM budget ~${budget}MB for integrations (host ${hostMb}MB, running VMs ${runningMb}MB)" -ForegroundColor Yellow
    if ($budget -lt $KorusQemuIntegrationsMemoryMbMin) {
        Write-Host "Need KORUS_QEMU_THREE_VM=1 stack: qemu-down; `$env:KORUS_QEMU_THREE_VM='1'; qemu-up -KeepDisks -WithIntegrations" -ForegroundColor Yellow
        Write-Host "  (server 8192 + web 2560 + integrations 4096 MB; fits ~16 GB host with WHPX)" -ForegroundColor DarkGray
    }
    return $false
}
