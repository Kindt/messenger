#Requires -Version 5.1

# Static VPP comprehensive manifest audit — all lab gates resolvable before QEMU run (spec 030).

param(

    [string]$ManifestPath = "",

    [string]$CatalogPath = "",

    [string]$OutPath = "",

    [switch]$Help

)



$ErrorActionPreference = "Stop"

if ($Help) {

    Write-Host "Usage: .\scripts\vpp\Invoke-VppCoverageAudit.ps1"

    exit 0

}



$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if (-not $ManifestPath) {

    $ManifestPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json"

}

if (-not $CatalogPath) {

    $CatalogPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-coverage-catalog.json"

}

$manifest = Get-Content -Raw $ManifestPath | ConvertFrom-Json



$EvDir = Join-Path $Root "deploy\qemu\run\vpp-evidence"

if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }

if (-not $OutPath) {

    $OutPath = Join-Path $EvDir ("vpp-catalog-audit-" + (Get-Date -Format "yyyy-MM-dd-HHmmss") + ".json")

}



$gaps = @()

$checks = @()



function Add-Check([string]$Id, [bool]$Ok, [string]$Detail) {

    $script:checks += @{ id = $Id; ok = $Ok; detail = $Detail }

    if (-not $Ok) { $script:gaps += @{ check = $Id; detail = $Detail } }

}



function Resolve-Script([string]$Rel) {

    if (-not $Rel) { return $null }

    $p = Join-Path $Root ($Rel -replace '/', '\')

    if (Test-Path $p) { return $Rel }

    return $null

}



$tiersPath = Join-Path $Root "tests\e2e-web\playwright-tiers.json"

$playwrightTiers = @{}

if (Test-Path $tiersPath) {

    $tierDoc = Get-Content -Raw $tiersPath | ConvertFrom-Json

    foreach ($t in @($tierDoc.tiers.PSObject.Properties)) {

        $playwrightTiers[$t.Name] = $true

    }

}



Add-Check "manifest_mode" ($manifest.coverage_policy.mode -eq "comprehensive_zero_skip") "mode=$($manifest.coverage_policy.mode)"



$gateIds = @($manifest.comprehensive_gates_ordered | Where-Object { $_ -ne "coverage_report" })

Add-Check "gate_count" ($gateIds.Count -ge 130) "expected >=130 comprehensive gates, got $($gateIds.Count)"



foreach ($gateId in $gateIds) {

    $def = $manifest.gates.$gateId

    if (-not $def) {

        Add-Check "gate_def:$gateId" $false "missing gates.$gateId"

        continue

    }

    switch ($def.type) {

        "script" {

            $scriptRel = $def.script

            Add-Check "gate_script:$gateId" ([bool](Resolve-Script $scriptRel)) $scriptRel

        }

        "playwright-tier" {

            $tier = $def.tier

            $ok = $playwrightTiers.ContainsKey($tier)

            Add-Check "gate_tier:$gateId" $ok "tier=$tier"

        }

        "playwright-matrix" {

            Add-Check "gate_matrix:$gateId" ([bool](Resolve-Script "scripts/run-playwright-qemu-matrix.ps1")) "profile=$($def.profile)"

        }

        "playwright-admin" {

            Add-Check "gate_admin:$gateId" ([bool](Resolve-Script "scripts/run-playwright-admin-qemu.ps1")) "playwright-admin"

        }

        "gradlew" {

            Add-Check "gate_gradlew:$gateId" (Test-Path (Join-Path $Root "gradlew.bat")) "gradlew.bat"

        }

        "catalog-audit" {

            Add-Check "gate_catalog_audit:$gateId" $true "self"

        }

        "preflight" {

            Add-Check "gate_preflight:$gateId" ([bool](Resolve-Script "scripts/vpp/Invoke-VppPreflight.ps1")) "preflight"

        }

        "coverage-report" {

            Add-Check "gate_coverage:$gateId" ([bool](Resolve-Script "scripts/Write-VppCoverageReport.ps1")) "coverage-report"

        }

        default {

            Add-Check "gate_type:$gateId" $false "unknown type $($def.type)"

        }

    }

}



if (Test-Path $CatalogPath) {

    $catalog = Get-Content -Raw $CatalogPath | ConvertFrom-Json

    $progMatrix = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-programmatic-override-matrix.json"

    if (Test-Path $progMatrix) {

        $prog = Get-Content -Raw $progMatrix | ConvertFrom-Json

        $progIds = @($prog.addons | ForEach-Object { $_.id })

        foreach ($a in @($catalog.addons)) {

            $id = $a.id

            if ($id -eq "addon-directory") { continue }

            Add-Check "programmatic:$id" ($progIds -contains $id) "vpp-programmatic-override-matrix.json"

        }

    }

    $interactionPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\module-interaction-matrix.json"

    if (Test-Path $interactionPath) {

        $interaction = Get-Content -Raw $interactionPath | ConvertFrom-Json

        $chainModules = @{}

        foreach ($c in @($interaction.chains)) {

            foreach ($m in @($c.modules)) {

                if (-not $chainModules.ContainsKey($m)) { $chainModules[$m] = @() }

                $chainModules[$m] += $c.id

            }

        }

        foreach ($a in @($catalog.addons)) {

            $id = $a.id

            if ($id -eq "addon-directory") { continue }

            Add-Check "interaction_chain:$id" ($chainModules.ContainsKey($id)) "module-interaction-matrix.json"

        }

    }

    foreach ($b in @($catalog.plugin_bridges)) {

        $smoke = Resolve-Script $b.smoke

        Add-Check "plugin_bridge:$($b.id)" ([bool]$smoke) $b.smoke

    }

}



$uiManifest = Join-Path $Root "specs\030-vpp-product-verification\contracts\ui-block-manifest.json"

if (Test-Path $uiManifest) {

    $ui = Get-Content -Raw $uiManifest | ConvertFrom-Json

    foreach ($block in @($ui.blocks)) {

        $specRel = "tests/e2e-web/$($block.playwright_spec)"

        Add-Check "ui_block:$($block.id)" ([bool](Resolve-Script $specRel)) $specRel

    }

}



$passed = @($checks | Where-Object { $_.ok }).Count

$total = $checks.Count

$pct = if ($total -gt 0) { [math]::Round(($passed / $total) * 1000) / 10 } else { 0 }

$complete = ($gaps.Count -eq 0)



$doc = [ordered]@{

    spec = "030-vpp-product-verification"

    title = "VPP comprehensive manifest static audit"

    timestamp = (Get-Date).ToUniversalTime().ToString("o")

    summary = @{

        checks_total = $total

        checks_pass = $passed

        coverage_pct = $pct

        catalog_complete = $complete

        gaps_count = $gaps.Count

        comprehensive_gates = $gateIds.Count

    }

    checks = $checks

    gaps = $gaps

    manifest = "specs/030-vpp-product-verification/contracts/vpp-comprehensive-gates.json"

}



$doc | ConvertTo-Json -Depth 8 | Set-Content -Path $OutPath -Encoding utf8

$latest = Join-Path $EvDir "vpp-catalog-audit-latest.json"

Copy-Item -Path $OutPath -Destination $latest -Force



Write-Host ""

Write-Host ('VPP comprehensive audit: {0}% ({1}/{2} checks pass, {3} gates)' -f $pct, $passed, $total, $gateIds.Count) -ForegroundColor $(if ($complete) { "Green" } else { "Yellow" })

if ($gaps.Count -gt 0) {

    foreach ($g in $gaps) {

        Write-Host "  [GAP] $($g.check): $($g.detail)" -ForegroundColor DarkYellow

    }

}

Write-Host "Report: $OutPath" -ForegroundColor Cyan



if (-not $complete) { exit 1 }

Write-Output $OutPath

