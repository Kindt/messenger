function Invoke-SonarGuest {
    param(
        [Parameter(Mandatory)][ValidateSet("run", "put")][string]$Action,
        [Parameter(Mandatory)][string[]]$Args
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $py = Join-Path $SonarQemuToolsDir "guest_ssh.py"
    $python = $null
    foreach ($candidate in @(
            $env:PYTHON,
            "C:\Users\GKindt\AppData\Local\Python\bin\python.exe",
            "py",
            "python"
        )) {
        if (-not $candidate) { continue }
        if ($candidate -match '[\\/]' -and -not (Test-Path $candidate)) { continue }
        if ($candidate -notmatch '[\\/]' -and -not (Get-Command $candidate -ErrorAction SilentlyContinue)) { continue }
        & $candidate -c "import sys; print(sys.version)" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $python = $candidate; break }
    }
    if (-not $python) { throw "python not found (required for guest SSH)" }
    $all = @(
        $py,
        "--host", "127.0.0.1",
        "--port", "$SonarQemuSshHostPort",
        "--user", $SonarQemuGuestUser,
        "--password", $SonarQemuGuestPassword,
        $Action
    ) + $Args
    # Capture so Python stdout is not returned as the function's success value.
    $out = & $python @all 2>&1
    $code = $LASTEXITCODE
    foreach ($line in @($out)) { Write-Host $line }
    return , [int]$code
}

function Invoke-SonarGuestCommand {
    param([Parameter(Mandatory)][string]$Remote)
    $code = Invoke-SonarGuest -Action run -Args @($Remote)
    if ($code -ne 0) { throw "Guest command failed ($code): $Remote" }
}

function Send-SonarGuestFile {
    param(
        [Parameter(Mandatory)][string]$Local,
        [Parameter(Mandatory)][string]$Remote
    )
    $code = Invoke-SonarGuest -Action put -Args @($Local, $Remote)
    if ($code -ne 0) { throw "Guest upload failed ($code): $Local -> $Remote" }
}
