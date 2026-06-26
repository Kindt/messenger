$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$Log = Join-Path $PSScriptRoot "evidence/2026-06-25_core-api-rebuild-minute.log"
$deadline = (Get-Date).AddMinutes(45)
while ((Get-Date) -lt $deadline) {
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $jobOut = & (Join-Path $Root "scripts/qemu-guest-job.ps1") 2>&1 | Out-String
    $jobOneLine = ($jobOut.Trim() -replace "\s+", " ")
    $hc = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health" 2>$null
    if (-not $hc) { $hc = "down" }
    $line = "$ts | host18080=$hc | $jobOneLine"
    Add-Content -Path $Log -Value $line
    Write-Host $line
    if ($jobOut -match "\[0\] core-api-rebuild finished" -and $hc -eq "200") { exit 0 }
    if ($jobOut -match "\[\.\.\] core-api-rebuild running") {
        Start-Sleep -Seconds 60
        continue
    }
    if ($jobOut -match "\[1\] core-api-rebuild finished") {
        if ($hc -eq "200") { exit 0 }
        exit 1
    }
    Start-Sleep -Seconds 60
}
exit 1
