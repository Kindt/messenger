function Write-KorusCycleStatus {
    param(
        [Parameter(Mandatory)][string]$Phase,
        [string]$Detail = "",
        [string]$Status = "running",
        [string]$RunDir = ""
    )
    if (-not $RunDir) {
        $RunDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "deploy\qemu\run"
    }
    $path = Join-Path $RunDir "cycle-unattended-status.json"
    $obj = [ordered]@{
        updated_at       = (Get-Date).ToUniversalTime().ToString("o")
        updated_at_local = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        phase            = $Phase
        status           = $Status
        detail           = $Detail
        pid              = $PID
    }
    ($obj | ConvertTo-Json -Depth 4) | Set-Content -Path $path -Encoding UTF8
}
