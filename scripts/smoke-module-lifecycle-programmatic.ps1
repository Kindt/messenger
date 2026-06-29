#Requires -Version 5.1
# VPP-2 programmatic: admin override roundtrip for ALL catalog addons (spec 030). No SKIP.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$MatrixPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-module-lifecycle-programmatic.ps1 [-BaseUrl http://127.0.0.1:18080]"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
if (-not $MatrixPath) {
    $MatrixPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-programmatic-override-matrix.json"
}
if (-not (Test-Path $MatrixPath)) { throw "missing matrix: $MatrixPath" }

$matrix = Get-Content -Raw $MatrixPath | ConvertFrom-Json
$API = "$BaseUrl/api/v1"

function Get-AdminHeaders {
    $login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
        -Body '{"username":"csadmin","password":"csadmin"}'
    if (-not $login.access_token) { throw "admin login failed" }
    return @{ Authorization = "Bearer $($login.access_token)" }
}

function Invoke-GateProbe {
    param(
        [hashtable]$Headers,
        [object]$Probe,
        [string]$Phase
    )
    if (-not $Probe) { return }
    $expectKey = if ($Phase -eq "disabled") { "expect_disabled" } else { "expect_enabled" }
    $expect = @($Probe.$expectKey)
    if ($expect.Count -eq 0) { return }

    $uri = "$API/$($Probe.path)"
    $code = 0
    try {
        if ($Probe.method -eq "GET") {
            Invoke-WebRequest -Method GET -Uri $uri -Headers $Headers -UseBasicParsing -TimeoutSec 15 | Out-Null
            $code = 200
        } else {
            $body = if ($Probe.body) { $Probe.body } else { "{}" }
            Invoke-WebRequest -Method $Probe.method -Uri $uri -Headers $Headers -ContentType "application/json" `
                -Body $body -UseBasicParsing -TimeoutSec 15 | Out-Null
            $code = 200
        }
    } catch {
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } else { throw $_ }
    }
    if ($expect -notcontains $code) {
        throw "probe $($Probe.method) $($Probe.path) [$Phase] expected [$($expect -join ',')] got $code"
    }
}

function Test-CapabilitiesState {
    param([string]$AddonId, [bool]$ExpectEnabled)
    $cap = Invoke-RestMethod -Method GET -Uri "$API/platform/capabilities" -TimeoutSec 15
    $mod = $cap.modules.$AddonId
    if (-not $mod) { throw "capabilities.modules missing $AddonId" }
    if ($ExpectEnabled) {
        if ($mod.admin_enabled -eq $false) { throw "expected admin_enabled=true for $AddonId after restore" }
        if (-not $mod.selected) { throw "expected selected=true for $AddonId on regression stack" }
    } else {
        if ($mod.admin_enabled -ne $false) { throw "expected admin_enabled=false for $AddonId after programmatic disable" }
    }
    return $mod
}

function Invoke-RestWithRetry {
    param(
        [scriptblock]$Call,
        [int]$MaxAttempts = 5,
        [int]$DelaySec = 5
    )
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try {
            return & $Call
        } catch {
            if ($i -ge $MaxAttempts) { throw }
            Write-Host "  retry $i/$MaxAttempts after: $($_.Exception.Message)" -ForegroundColor DarkYellow
            Start-Sleep -Seconds $DelaySec
        }
    }
}

Write-Host "=== programmatic: admin grid ===" -ForegroundColor Cyan
$headers = Invoke-RestWithRetry { Get-AdminHeaders }
$grid = Invoke-RestWithRetry { Invoke-RestMethod -Method GET -Uri "$API/admin/ui/product-modules" -Headers $headers }
if (-not $grid.base) { throw "product-modules grid missing base" }

$addonRows = @($matrix.addons)
if ($addonRows.Count -lt 17) { throw "matrix must list all 17 addons, got $($addonRows.Count)" }

$restored = @()
foreach ($entry in $addonRows) {
    $addonId = $entry.id
    $row = @($grid.addons | Where-Object { $_.id -eq $addonId }) | Select-Object -First 1
    if (-not $row) { throw "admin grid missing addon row $addonId" }
    if (-not $row.selected) {
        throw "addon $addonId not selected on stack - run qemu-enable-regression-addons.ps1"
    }

    Write-Host ""
    Write-Host "  -> override roundtrip: $addonId ($($entry.disabled_behavior))" -ForegroundColor DarkGray

    $disableBody = @{ disabled = $true; override_reason = "admin_override" } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method PUT -Uri "$API/admin/ui/product-modules/$addonId/override" `
        -Headers $headers -ContentType "application/json" -Body $disableBody | Out-Null
    Start-Sleep -Milliseconds 500

    $modDisabled = Test-CapabilitiesState -AddonId $addonId -ExpectEnabled $false
    if ($entry.verify_fallback -and $modDisabled.state) {
        Write-Host "    capabilities state when disabled: $($modDisabled.state)" -ForegroundColor DarkGray
    }

    if ($entry.capabilities_only) {
        Write-Host "    probe: capabilities-only" -ForegroundColor DarkGray
    } else {
        Invoke-GateProbe -Headers $headers -Probe $entry.probe -Phase "disabled"
    }

    $enableBody = @{ disabled = $false } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method PUT -Uri "$API/admin/ui/product-modules/$addonId/override" `
        -Headers $headers -ContentType "application/json" -Body $enableBody | Out-Null
    Start-Sleep -Milliseconds 400

    Test-CapabilitiesState -AddonId $addonId -ExpectEnabled $true | Out-Null
    if (-not $entry.capabilities_only) {
        Invoke-GateProbe -Headers $headers -Probe $entry.probe -Phase "enabled"
    }

    $restored += $addonId
    Write-Host "    [OK] $addonId" -ForegroundColor Green
}

Write-Host ""
Write-Host ('[OK] programmatic lifecycle: {0}/{0} addon override roundtrips' -f $restored.Count) -ForegroundColor Green
