#Requires -Version 5.1
# VPP single pass — returns result hashtable (spec 030). Dot-source from run-vpp-full.ps1.

function Invoke-VppFullRun {
    param(
        [ValidateSet('quick', 'standard', 'full')]
        [string]$Level = 'full',
        [string]$ApiBaseUrl = "http://127.0.0.1:18080",
        [string]$WebBaseUrl = "http://127.0.0.1:19088",
        [switch]$SkipBuild,
        [switch]$SkipIntegrations,
        [switch]$SkipPlaywright,
        [switch]$SkipLoad,
        [int]$Attempt = 1,
        [string]$ResumeCheckpoint = ""
    )

    $Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $isFull = ($Level -eq 'full')

    . (Join-Path $Root "deploy\qemu\lib\Get-KorusStackWaitParams.ps1")
    $script:VppStackWait = Get-KorusStackWaitParams
    function Invoke-VppPlaywrightTier {
        param(
            [Parameter(Mandatory)][string]$TierName,
            [int]$StartAfterTestIndex = 0
        )
        $pwLoop = Join-Path $Root "scripts\playwright-dev-loop.ps1"
        $pwArgs = @{
            Tier = $TierName; WaitForStack = $true
            WaitTimeoutMinutes = $script:VppStackWait.TimeoutMinutes
            WaitIntervalSec = $script:VppStackWait.IntervalSec
            WaitBusyIntervalSec = $script:VppStackWait.BusyIntervalSec
            MaxMaintenanceMinutes = $script:VppStackWait.MaxMaintenanceMinutes
        }
        if ($StartAfterTestIndex -gt 0) { $pwArgs['StartAfterTestIndex'] = $StartAfterTestIndex }
        & $pwLoop @pwArgs
    }

    if ($isFull -and ($SkipIntegrations -or $SkipPlaywright -or $SkipLoad)) {
        Write-Host "[WARN] full VPP ignores -SkipIntegrations/-SkipPlaywright/-SkipLoad (comprehensive zero-SKIP)" -ForegroundColor Yellow
        $SkipIntegrations = $false
        $SkipPlaywright = $false
        $SkipLoad = $false
    }

    $writeEvidence = Join-Path $Root "scripts\Write-VppEvidence.ps1"
    $writeCoverage = Join-Path $Root "scripts\Write-VppCoverageReport.ps1"
    $writeAudit = Join-Path $Root "scripts\vpp\Invoke-VppCoverageAudit.ps1"
    $preflight = Join-Path $Root "scripts\vpp\Invoke-VppPreflight.ps1"
    $updateProgress = Join-Path $Root "scripts\vpp\Update-VppLiveProgress.ps1"

    function Sync-VppLiveProgress {
        param(
            [string]$GateKey = "",
            [string]$Status = "",
            [string]$CurrentGate = "",
            [string]$Phase = "running",
            [switch]$Init
        )
        if (-not $isFull -or -not (Test-Path $updateProgress)) { return }
        & $updateProgress -Level $Level -Attempt $Attempt -GateKey $GateKey -Status $Status `
            -Gates $gates -LastFailedGate $script:LastFailedGate -LastExitCode $script:LastExitCode `
            -CurrentGate $CurrentGate -Phase $Phase -Init:$Init
    }

    $gates = @{}
    $dimensions = @{}
    $artifacts = @()
    $sessionStart = Get-Date
    $script:LastFailedGate = ""
    $script:LastExitCode = 0
    $script:hardFail = $false
    $script:VppPwResumeGate = ""
    $script:VppPwStartAfter = 0

    function Vpp-Step {
        param(
            [string]$Dim,
            [string]$GateKey,
            [string]$Name,
            [scriptblock]$Body,
            [switch]$SubsampleOnly
        )
        if ($script:hardFail) { return }
        if ($SubsampleOnly -and $isFull) { $SubsampleOnly = $false }

        if ($SubsampleOnly) {
            $gates[$GateKey] = "SKIP"
            if (-not $dimensions.ContainsKey($Dim)) { $dimensions[$Dim] = @{ status = "SKIP"; gates = @{} } }
            $dimensions[$Dim].gates[$GateKey] = "SKIP"
            return
        }

        $gateEvent = Join-Path $Root "scripts\vpp\Write-VppGateEvent.ps1"
        $gateFix = Join-Path $Root "scripts\vpp\Invoke-VppGateAutoFix.ps1"
        $inlineRetry = $true
        if ($env:VPP_INLINE_GATE_RETRY -eq '0') { $inlineRetry = $false }

        Write-Host ""
        Write-Host "=== VPP [$Dim] $Name ===" -ForegroundColor Cyan
        Sync-VppLiveProgress -CurrentGate $GateKey
        if (Test-Path $gateEvent) {
            $passSoFar = @($gates.Values | Where-Object { $_ -eq 'PASS' }).Count
            try { & $gateEvent -GateId $GateKey -Status START -PassCount $passSoFar -TotalGates 145 | Out-Null } catch { Write-Host "[warn] gate event START: $_" -ForegroundColor DarkYellow }
        }

        $maxTries = 1
        if ($inlineRetry) {
            $maxTries = 5
            if ($env:VPP_INLINE_GATE_MAX) {
                $n = 0
                if ([int]::TryParse($env:VPP_INLINE_GATE_MAX, [ref]$n) -and $n -gt 0) { $maxTries = $n }
            }
        }
        for ($gateAttempt = 1; $gateAttempt -le $maxTries; $gateAttempt++) {
            if ($gateAttempt -gt 1 -and (Test-Path $gateEvent)) {
                try {
                    & $gateEvent -GateId $GateKey -Status RETRY -PassCount (@($gates.Values | Where-Object { $_ -eq 'PASS' }).Count) `
                        -TotalGates 145 -Detail "attempt $gateAttempt/$maxTries" | Out-Null
                } catch { Write-Host "[warn] gate event RETRY: $_" -ForegroundColor DarkYellow }
            }
            $global:LASTEXITCODE = 0
            try {
                & $Body
            } catch {
                Write-Host "[FAIL] VPP gate $GateKey threw: $($_.Exception.Message)" -ForegroundColor Red
                $global:LASTEXITCODE = 1
                if (-not $env:VPP_LAST_GATE_DETAIL) { $env:VPP_LAST_GATE_DETAIL = $_.Exception.Message }
            }
            if ($LASTEXITCODE -ne 0) {
                $logTail = Join-Path $Root 'deploy\qemu\run\vpp-until-green.log'
                if (Test-Path $logTail) {
                    $lastFail = @(Get-Content -LiteralPath $logTail -Tail 30 -ErrorAction SilentlyContinue |
                        Where-Object { $_ -match '(?i)\[FAIL\]|FAIL |throw|Timed out|not reachable' } | Select-Object -Last 1)
                    if ($lastFail) { $env:VPP_LAST_GATE_DETAIL = [string]$lastFail }
                }
            }
            if ($LASTEXITCODE -eq 0) { break }
            if ($gateAttempt -lt $maxTries -and (Test-Path $gateFix)) {
                Write-Host "[VPP] gate $GateKey failed (exit $LASTEXITCODE) - auto-fix before retry..." -ForegroundColor Yellow
                & $gateFix -GateKey $GateKey
            }
        }

        if ($LASTEXITCODE -ne 0) {
            $script:LastFailedGate = $GateKey
            $script:LastExitCode = $LASTEXITCODE
            $gates[$GateKey] = "FAIL"
            if (-not $dimensions.ContainsKey($Dim)) { $dimensions[$Dim] = @{ status = "FAIL"; gates = @{} } }
            $dimensions[$Dim].gates[$GateKey] = "FAIL"
            Write-Host "[FAIL] VPP gate $GateKey (exit $LASTEXITCODE)" -ForegroundColor Red
            $script:hardFail = $true
            Sync-VppLiveProgress -GateKey $GateKey -Status "FAIL" -Phase "failed"
            $saveCp = Join-Path $Root "scripts\vpp\Save-VppCheckpoint.ps1"
            if (Test-Path $saveCp) {
                try {
                    & $saveCp -Reason "gate FAIL: $GateKey (exit $LASTEXITCODE)" -ResumeGate $GateKey | Out-Null
                } catch { Write-Host "[warn] checkpoint save: $_" -ForegroundColor DarkYellow }
            }
            if (Test-Path $gateEvent) {
                $passSoFar = @($gates.Values | Where-Object { $_ -eq 'PASS' }).Count
                try { & $gateEvent -GateId $GateKey -Status FAIL -ExitCode $LASTEXITCODE -PassCount $passSoFar -TotalGates 145 | Out-Null } catch { Write-Host "[warn] gate event FAIL: $_" -ForegroundColor DarkYellow }
            }
            return
        }
        $gates[$GateKey] = "PASS"
        if (-not $dimensions.ContainsKey($Dim)) { $dimensions[$Dim] = @{ status = "PASS"; gates = @{} } }
        $dimensions[$Dim].gates[$GateKey] = "PASS"
        Sync-VppLiveProgress -GateKey $GateKey -Status "PASS"
        if (Test-Path $gateEvent) {
            $passSoFar = @($gates.Values | Where-Object { $_ -eq 'PASS' }).Count
            try { & $gateEvent -GateId $GateKey -Status PASS -PassCount $passSoFar -TotalGates 145 | Out-Null } catch { Write-Host "[warn] gate event PASS: $_" -ForegroundColor DarkYellow }
        }
    }

    function Finalize-Dimension([string]$Dim) {
        if (-not $dimensions.ContainsKey($Dim)) {
            $dimensions[$Dim] = @{ status = "SKIP"; gates = @{} }
            return
        }
        $g = $dimensions[$Dim].gates
        if ($g.Values -contains "FAIL") { $dimensions[$Dim].status = "FAIL" }
        elseif ($g.Values -contains "SKIP" -and $isFull) { $dimensions[$Dim].status = "FAIL" }
        elseif (($g.Values | Where-Object { $_ -eq "PASS" }).Count -gt 0) { $dimensions[$Dim].status = "PASS" }
        else { $dimensions[$Dim].status = "SKIP" }
    }

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Magenta
    Write-Host "  Vseobemlyayushchaya proverka produkta (VPP)" -ForegroundColor Magenta
    Write-Host "  Level: $Level | attempt: $Attempt | spec 030" -ForegroundColor Magenta
    if ($isFull) { Write-Host "  Policy: comprehensive zero-SKIP - ALL lab gates mandatory" -ForegroundColor Magenta }
    Write-Host "========================================" -ForegroundColor Magenta

    $gateRunner = Join-Path $Root "scripts\vpp\Invoke-VppGateRunner.ps1"
    $manifestPath = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-comprehensive-gates.json"

    if ($isFull) {
        $env:VPP_ZERO_SKIP = "1"
        $checkpointPath = $ResumeCheckpoint
        if (-not $checkpointPath -and $env:VPP_RESUME_CHECKPOINT) { $checkpointPath = $env:VPP_RESUME_CHECKPOINT }
        if ($checkpointPath -and (Test-Path $checkpointPath)) {
            $cp = Get-Content -Raw $checkpointPath | ConvertFrom-Json
            if ($cp.gates_passed) {
                foreach ($p in $cp.gates_passed.PSObject.Properties) {
                    if ($p.Value -eq 'PASS') { $gates[$p.Name] = 'PASS' }
                }
            }
            if ($cp.session_start) {
                try { $sessionStart = [datetime]::Parse($cp.session_start) } catch { }
            }
            if ($cp.playwright_partial -and $cp.playwright_partial.gate -and $cp.playwright_partial.test_index) {
                $script:VppPwResumeGate = [string]$cp.playwright_partial.gate
                $script:VppPwStartAfter = [int]$cp.playwright_partial.test_index
            }
            $passN = @($gates.Values | Where-Object { $_ -eq 'PASS' }).Count
            Write-Host "  [resume] checkpoint: $passN gates PASS; continue from $($cp.resume_from_gate)" -ForegroundColor Yellow
            if ($script:VppPwStartAfter -gt 0) {
                Write-Host "  [resume] playwright tier $($script:VppPwResumeGate) from test index $($script:VppPwStartAfter)" -ForegroundColor Yellow
            }
            Sync-VppLiveProgress -Gates $gates -CurrentGate ([string]$cp.resume_from_gate) -Phase "running"
        } else {
            Sync-VppLiveProgress -Init -Phase "running"
        }
        $manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
        Write-Host "  Gates: $(@($manifest.comprehensive_gates_ordered | Where-Object { $_ -ne 'coverage_report' }).Count) comprehensive (manifest v$($manifest.schema_version))" -ForegroundColor Magenta

        foreach ($gateId in @($manifest.comprehensive_gates_ordered)) {
            if ($gateId -eq "coverage_report") { continue }
            if ($script:hardFail) { break }
            if ($gates[$gateId] -eq 'PASS') { continue }

            $def = $manifest.gates.$gateId
            if (-not $def) {
                $gates[$gateId] = "FAIL"
                $script:LastFailedGate = $gateId
                $script:hardFail = $true
                Write-Host "[FAIL] missing gate definition: $gateId" -ForegroundColor Red
                continue
            }

            $dim = if ($def.dimension) { $def.dimension } else { "VPP-0" }
            $stepName = if ($def.note) { "$gateId ($($def.note))" } else { $gateId }

            if ($gateId -eq "buildIntegrity" -and $SkipBuild) {
                $gates[$gateId] = "SKIP"
                if (-not $dimensions.ContainsKey($dim)) { $dimensions[$dim] = @{ status = "FAIL"; gates = @{} } }
                $dimensions[$dim].gates[$gateId] = "SKIP"
                $dimensions[$dim].status = "FAIL"
                $script:LastFailedGate = $gateId
                $script:hardFail = $true
                Write-Host "[FAIL] full VPP cannot SkipBuild" -ForegroundColor Red
                continue
            }

            Vpp-Step -Dim $dim -GateKey $gateId -Name $stepName {
                $pwStart = 0
                if ($script:VppPwResumeGate -and $script:VppPwStartAfter -gt 0 -and $gateId -eq $script:VppPwResumeGate) {
                    $pwStart = $script:VppPwStartAfter
                }
                $defType = [string]$def.type
                if ($defType -eq 'playwright-tier') {
                    Invoke-VppPlaywrightTier -TierName $def.tier -StartAfterTestIndex $pwStart
                } else {
                    & $gateRunner -GateId $gateId -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl `
                        -StartAfterTestIndex $pwStart
                }
                if ($pwStart -gt 0 -and $LASTEXITCODE -eq 0) {
                    $script:VppPwStartAfter = 0
                    $script:VppPwResumeGate = ''
                }
            }
        }

        foreach ($dim in ($dimensions.Keys | Sort-Object)) { Finalize-Dimension $dim }

        $coverageOk = $true
        $coveragePath = ""
        if (-not $script:hardFail) {
            Write-Host ""
            Write-Host "=== VPP coverage report (comprehensive zero-SKIP) ===" -ForegroundColor Cyan
            try {
                $coveragePath = & $writeCoverage -Gates $gates
                if ($LASTEXITCODE -ne 0) {
                    $gates.coverage_report = "FAIL"
                    $coverageOk = $false
                    $script:hardFail = $true
                    $script:LastFailedGate = "coverage_report"
                } else {
                    $gates.coverage_report = "PASS"
                }
            } catch {
                $gates.coverage_report = "FAIL"
                $coverageOk = $false
                $script:hardFail = $true
                $script:LastFailedGate = "coverage_report"
            }
        } else {
            $gates.coverage_report = "NOT_RUN"
            $coverageOk = $false
        }
    } else {
    if (-not $SkipBuild) {
        Vpp-Step -Dim "VPP-1" -GateKey "buildIntegrity" -Name "buildIntegrity" {
            Push-Location $Root
            $prevEap = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                & .\gradlew.bat buildIntegrity --no-daemon -q 2>&1 | Out-Host
            } finally {
                $ErrorActionPreference = $prevEap
                Pop-Location
            }
            if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        }
    } else {
        $gates.buildIntegrity = "SKIP"
        $dimensions["VPP-1"] = @{ status = if ($isFull) { "FAIL" } else { "PASS" }; gates = @{ buildIntegrity = "SKIP" } }
        if ($isFull) { $script:hardFail = $true; $script:LastFailedGate = "buildIntegrity" }
    }

    if (-not $script:hardFail) {
        Vpp-Step -Dim "VPP-1" -GateKey "stack_health" -Name "stack ready" {
            $readySh = Join-Path $Root "scripts\smoke-ready.ps1"
            if (Test-Path $readySh) { & $readySh -BaseUrl $ApiBaseUrl } else { & bash (Join-Path $Root "scripts/smoke-ready.sh") }
        }
    }

    Vpp-Step -Dim "VPP-1" -GateKey "korus_web" -Name "korus web shell" -SubsampleOnly:($Level -eq 'quick') {
        & (Join-Path $Root "scripts\smoke-korus-web.ps1") -WebBaseUrl $WebBaseUrl
    }
    Finalize-Dimension "VPP-1"

    if (-not $script:hardFail) {
        Vpp-Step -Dim "VPP-2" -GateKey "platform_capabilities" -Name "platform capabilities" {
            & (Join-Path $Root "scripts\smoke-platform-capabilities.ps1") -BaseUrl $ApiBaseUrl
        }
    }

    Vpp-Step -Dim "VPP-2" -GateKey "module_lifecycle" -Name "module lifecycle (programmatic + physical)" -SubsampleOnly:($Level -eq 'quick') {
        & (Join-Path $Root "scripts\smoke-module-lifecycle.ps1") -BaseUrl $ApiBaseUrl -SkipPlugins
    }

    Vpp-Step -Dim "VPP-2" -GateKey "integrations_gate" -Name "integrations gate" -SubsampleOnly:($SkipIntegrations -or $Level -eq 'quick') {
        & (Join-Path $Root "scripts\smoke-integrations-gate.ps1")
    }

    Vpp-Step -Dim "VPP-2" -GateKey "plugin_lifecycle" -Name "plugin lifecycle (all bridges)" -SubsampleOnly:($SkipIntegrations -or $Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-plugin-lifecycle.ps1")
    }

    Vpp-Step -Dim "VPP-2" -GateKey "plugin_qemu" -Name "plugin platform qemu" -SubsampleOnly:($SkipIntegrations -or $Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-plugin-qemu.ps1")
    }

    Vpp-Step -Dim "VPP-2" -GateKey "addon_smokes_all" -Name "all addon smokes" -SubsampleOnly:($Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-vpp-addon-smokes.ps1") -ApiBaseUrl $ApiBaseUrl
    }

    Vpp-Step -Dim "VPP-2" -GateKey "hotplug_indexer" -Name "hotplug indexer" -SubsampleOnly:($Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-hotplug-indexer.ps1")
    }
    Finalize-Dimension "VPP-2"

    if (-not $SkipPlaywright) {
        Vpp-Step -Dim "VPP-3" -GateKey "ui_ux_smoke" -Name "ui-ux-smoke (~138 scenarios)" -SubsampleOnly:($isFull) {
            Invoke-VppPlaywrightTier -TierName ui-ux-smoke
        }
        Vpp-Step -Dim "VPP-3" -GateKey "ui_ux_full" -Name "ui-ux-full (~1400 scenarios)" -SubsampleOnly:(-not $isFull) {
            Invoke-VppPlaywrightTier -TierName ui-ux-full
        }
        Vpp-Step -Dim "VPP-3" -GateKey "vpp_ui_blocks" -Name "vpp-ui-blocks" -SubsampleOnly:($Level -eq 'quick') {
            Invoke-VppPlaywrightTier -TierName vpp-ui-blocks
        }
        Vpp-Step -Dim "VPP-3" -GateKey "ui_interaction_audit" -Name "ui-interaction-audit" -SubsampleOnly:($Level -ne 'full') {
            Invoke-VppPlaywrightTier -TierName ui-interaction-audit
        }
        Vpp-Step -Dim "VPP-3" -GateKey "ui_mobile" -Name "ui-mobile" -SubsampleOnly:($Level -ne 'full') {
            Invoke-VppPlaywrightTier -TierName ui-mobile
        }
        Vpp-Step -Dim "VPP-3" -GateKey "ui_visual" -Name "ui-visual" -SubsampleOnly:($Level -ne 'full') {
            Invoke-VppPlaywrightTier -TierName ui-visual
        }
        Vpp-Step -Dim "VPP-3" -GateKey "ui_visual_regression" -Name "ui-visual-regression" -SubsampleOnly:($Level -ne 'full') {
            Invoke-VppPlaywrightTier -TierName ui-visual-regression
        }
        Vpp-Step -Dim "VPP-3" -GateKey "playwright_all_inner" -Name "playwright all-inner-core" -SubsampleOnly:($Level -ne 'full') {
            $innerTier = if ($isFull) { 'all-inner-core' } else { 'all-inner' }
            Invoke-VppPlaywrightTier -TierName $innerTier
        }
    } else {
        @("ui_ux_smoke", "ui_ux_full", "vpp_ui_blocks", "ui_interaction_audit", "ui_mobile", "ui_visual", "ui_visual_regression", "playwright_all_inner") | ForEach-Object {
            $gates[$_] = "SKIP"
            if ($isFull) { $script:hardFail = $true }
        }
    }
    Finalize-Dimension "VPP-3"

    Vpp-Step -Dim "VPP-4" -GateKey "module_interactions" -Name "module interactions (all chains)" -SubsampleOnly:($Level -eq 'quick') {
        if ($Level -eq 'standard') {
            & (Join-Path $Root "scripts\smoke-module-interactions.ps1") -Quick -ApiBaseUrl $ApiBaseUrl
        } else {
            & (Join-Path $Root "scripts\smoke-module-interactions.ps1") -ApiBaseUrl $ApiBaseUrl
        }
    }

    if (-not $script:hardFail) {
        Vpp-Step -Dim "VPP-4" -GateKey "web_parity_api" -Name "web parity API" {
            & (Join-Path $Root "scripts\smoke-web-parity-api.ps1")
        }
    }

    Vpp-Step -Dim "VPP-4" -GateKey "local_regression" -Name "local regression (W1b + portability)" -SubsampleOnly:($Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-local-regression.ps1") -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl
    }

    Vpp-Step -Dim "VPP-4" -GateKey "messaging_e2e" -Name "messaging e2e" -SubsampleOnly:($Level -ne 'full') {
        $messaging = Join-Path $Root "scripts\smoke-messaging-e2e.ps1"
        if (Test-Path $messaging) { & $messaging -BaseUrl $ApiBaseUrl }
        else { & bash (Join-Path $Root "scripts/smoke-messaging-e2e.sh") }
    }
    Finalize-Dimension "VPP-4"

    if (-not $SkipPlaywright) {
        Vpp-Step -Dim "VPP-5" -GateKey "playwright_admin" -Name "admin playwright" {
            & (Join-Path $Root "scripts\run-playwright-admin-qemu.ps1")
        }
        Vpp-Step -Dim "VPP-5" -GateKey "admin_extended" -Name "admin extended" -SubsampleOnly:($Level -eq 'quick') {
            Invoke-VppPlaywrightTier -TierName ui-admin-extended
        }
        Vpp-Step -Dim "VPP-5" -GateKey "admin_smokes" -Name "admin smokes" -SubsampleOnly:($Level -ne 'full') {
            & (Join-Path $Root "scripts\smoke-vpp-admin-smokes.ps1") -ApiBaseUrl $ApiBaseUrl
        }
    } else {
        @("playwright_admin", "admin_extended", "admin_smokes") | ForEach-Object { $gates[$_] = "SKIP" }
    }
    Finalize-Dimension "VPP-5"

    if (-not $SkipPlaywright) {
        Vpp-Step -Dim "VPP-6" -GateKey "playwright_api" -Name "API tier" {
            Invoke-VppPlaywrightTier -TierName api
        }
        Vpp-Step -Dim "VPP-6" -GateKey "playwright_actions" -Name "playwright matrix VppFull/W_SPEC" -SubsampleOnly:($Level -eq 'quick') {
            $pwProfile = if ($Level -eq 'full') { 'VppFull' } elseif ($Level -eq 'standard') { 'L4-light' } else { 'L4++' }
            & (Join-Path $Root "scripts\run-playwright-qemu-matrix.ps1") -Profile $pwProfile
        }
    } else {
        $gates.playwright_api = "SKIP"
        $gates.playwright_actions = "SKIP"
    }

    Vpp-Step -Dim "VPP-6" -GateKey "media_wave" -Name "media wave (turn + voice)" -SubsampleOnly:($Level -ne 'full') {
        & (Join-Path $Root "scripts\smoke-vpp-media-wave.ps1") -ApiBaseUrl $ApiBaseUrl
    }

    if (-not $SkipLoad) {
        Vpp-Step -Dim "VPP-6" -GateKey "export_compliance_chain" -Name "export compliance chain" -SubsampleOnly:($Level -ne 'full') {
            & (Join-Path $Root "scripts\run-export-compliance-chain.ps1") -ApiBaseUrl $ApiBaseUrl
        }
        Vpp-Step -Dim "VPP-6" -GateKey "security_gate" -Name "security gate" -SubsampleOnly:($Level -ne 'full') {
            & (Join-Path $Root "scripts\security-gate.ps1") -SkipBuild -BaseUrl $ApiBaseUrl
        }
        Vpp-Step -Dim "VPP-6" -GateKey "network_catalog" -Name "network profile catalog" -SubsampleOnly:($Level -ne 'full') {
            & (Join-Path $Root "scripts\smoke-network-profile-catalog.ps1")
        }
        Vpp-Step -Dim "VPP-6" -GateKey "deploy_acceptance" -Name "deploy acceptance (QEMU)" -SubsampleOnly:($Level -ne 'full') {
            & (Join-Path $Root "scripts\smoke-deploy-acceptance-qemu.ps1") -ApiBaseUrl $ApiBaseUrl -WebBaseUrl $WebBaseUrl
        }
    } else {
        @("export_compliance_chain", "security_gate", "network_catalog", "deploy_acceptance") | ForEach-Object { $gates[$_] = "SKIP" }
    }
    Finalize-Dimension "VPP-6"

        $coverageOk = $true
        $coveragePath = ""
    }

    $skipGateOk = if ($isFull) { ($gates.Values -notcontains "SKIP") } else { $true }
    $ok = -not $script:hardFail -and $coverageOk -and ($gates.Values -notcontains "FAIL") -and $skipGateOk -and (
        ($dimensions.Values | Where-Object { $_.status -eq "FAIL" }).Count -eq 0
    )

    return (New-VppRunResult -Ok $ok -Root $Root -Level $Level -Attempt $Attempt -Gates $gates -Dimensions $dimensions -Artifacts $artifacts -SessionStart $sessionStart -WriteEvidence $writeEvidence -WriteCoverage $writeCoverage -CoveragePath $coveragePath -LastFailedGate $script:LastFailedGate -LastExitCode $script:LastExitCode)
}

function New-VppRunResult {
    param(
        [bool]$Ok,
        $Root,
        [string]$Level,
        [int]$Attempt,
        [hashtable]$Gates,
        [hashtable]$Dimensions,
        [array]$Artifacts,
        $SessionStart,
        [string]$WriteEvidence,
        [string]$WriteCoverage = "",
        [string]$CoveragePath = "",
        [string]$LastFailedGate = "",
        [int]$LastExitCode = 0
    )

    $addons = @(
        "addon-productivity", "addon-engage", "addon-search", "addon-collaboration", "addon-ai",
        "addon-live", "addon-retention", "addon-archive", "addon-deep-archive", "addon-export",
        "addon-enterprise-auth", "addon-e2ee", "addon-bots", "addon-integrations",
        "addon-federation", "addon-dlp", "addon-migration-import", "addon-directory"
    )

    $uxSummary = @{}
    $vppArtifacts = Join-Path $Root "tests\e2e-web\artifacts\vpp-runs\summary.json"
    if (Test-Path $vppArtifacts) {
        $Artifacts += $vppArtifacts
        try {
            $ux = Get-Content -Raw $vppArtifacts | ConvertFrom-Json
            $uxSummary = @{
                click_coverage_pct = $ux.coveragePct
                blocks_checked = $ux.blocksChecked
                artifact_dir = "tests/e2e-web/artifacts/vpp-runs"
            }
        } catch { }
    }

    $durationSec = [math]::Round(((Get-Date) - $SessionStart).TotalSeconds, 1)
    foreach ($d in $Dimensions.Keys) {
        $Dimensions[$d].duration_sec = $durationSec
    }

    $overallStatus = if ($Ok) { "GREEN" } else { "FAIL" }
    $extraGates = $Gates.Clone()
    $extraGates.overall_status = $overallStatus
    $extraGates.attempt = "$Attempt"

    $evPath = & $WriteEvidence -Level $Level -Gates $extraGates -Dimensions $Dimensions `
        -Artifacts $Artifacts -AddonsEnabled $addons -UxSummary $uxSummary

    $coveragePathOut = $CoveragePath
    if (-not $coveragePathOut -and $WriteCoverage -and (Test-Path $WriteCoverage)) {
        try { $coveragePathOut = & $WriteCoverage -Gates $Gates } catch { }
    }

    return @{
        Ok = $Ok
        Level = $Level
        Attempt = $Attempt
        DurationSec = $durationSec
        Gates = $Gates
        Dimensions = $Dimensions
        EvidencePath = $evPath
        CoveragePath = $coveragePathOut
        LastFailedGate = $LastFailedGate
        LastExitCode = if ($LastExitCode -ne 0) { $LastExitCode } else { if ($Ok) { 0 } else { 1 } }
    }
}
