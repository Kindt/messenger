# Runs k6 pilot-health baseline against QEMU-forwarded API (T604).
# Requires k6 on PATH (choco install k6 / winget install k6).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$OutJson = "deploy/qemu/run/k6-pilot-baseline.json",
    [int]$DurationSec = 30
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$health = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
if ($health.StatusCode -ne 200) {
    Write-Error "API health failed at $BaseUrl"
}

$k6 = Get-Command k6 -ErrorAction SilentlyContinue
if (-not $k6) {
    Write-Host "[WARN] k6 not found. Install: choco install k6"
    Write-Host "       Falling back to lightweight health probe (not a full baseline)."
    $outDir = Split-Path -Parent $OutJson
    if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
    $samples = @()
    $deadline = (Get-Date).AddSeconds($DurationSec)
    while ((Get-Date) -lt $deadline) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
            $sw.Stop()
            $samples += [pscustomobject]@{ ms = $sw.ElapsedMilliseconds; ok = ($r.StatusCode -eq 200) }
        } catch {
            $sw.Stop()
            $samples += [pscustomobject]@{ ms = $sw.ElapsedMilliseconds; ok = $false }
        }
        Start-Sleep -Milliseconds 200
    }
    $ok = $samples | Where-Object { $_.ok }
    $report = [pscustomobject]@{
        generated_at_utc = (Get-Date).ToUniversalTime().ToString("o")
        mode = "powershell-health-fallback"
        base_url = $BaseUrl
        duration_sec = $DurationSec
        requests = $samples.Count
        success_rate = if ($samples.Count -gt 0) { [math]::Round(100.0 * $ok.Count / $samples.Count, 2) } else { 0 }
        p95_ms = if ($ok.Count -gt 0) {
            $sorted = $ok.ms | Sort-Object
            $idx = [math]::Ceiling(0.95 * $sorted.Count) - 1
            $sorted[[math]::Max(0, $idx)]
        } else { $null }
    }
    $report | ConvertTo-Json -Depth 4 | Set-Content -Path $OutJson -Encoding UTF8
    Write-Host "Wrote fallback baseline: $OutJson"
    exit 0
}

$env:K6_BASE_URL = $BaseUrl
$outDir = Split-Path -Parent $OutJson
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

Write-Host "Running k6 pilot-health.js against $BaseUrl ..."
& k6 run --out "json=$OutJson" "$repoRoot/scripts/load/pilot-health.js"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Wrote k6 baseline: $OutJson"
