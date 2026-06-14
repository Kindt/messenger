function Test-KorusQemuProcess {
    param([int]$ProcessId)
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction SilentlyContinue).CommandLine
    if (-not $cmd) { return $false }
    return ($cmd -match 'korus-server|korus-web|korus-whpx-probe|deploy[/\\]qemu[/\\]images[/\\](server|web)(-(dev|full))?\.qcow2')
}

function Test-KorusQemuStackRunning {
    param([string]$RunDir)
    foreach ($role in @("server", "web")) {
        $pf = Join-Path $RunDir "$role.pid"
        if (-not (Test-Path $pf)) { continue }
        $id = (Get-Content $pf -Raw).Trim()
        if ($id -notmatch '^\d+$') { continue }
        if (-not (Get-Process -Id ([int]$id) -ErrorAction SilentlyContinue)) { continue }
        if (Test-KorusQemuProcess -ProcessId ([int]$id)) { return $true }
    }
    return $false
}
