#Requires -Version 5.1
<#
.SYNOPSIS
  Конвейер агентов desktop Java client (spec 031) — статус, промпты, волны W0–W4.

.EXAMPLE
  .\scripts\Start-KorusDesktopPipeline.ps1 -InitFullDelivery

.EXAMPLE
  .\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt

.EXAMPLE
  .\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved -Advance

.EXAMPLE
  .\scripts\Start-KorusDesktopPipeline.ps1 -CompleteWave
#>
[CmdletBinding()]
param(
    [ValidateSet('W0', 'W1', 'W2', 'W3', 'W4')]
    [string] $Wave,

    [ValidateSet('Analyst', 'Architect', 'Designer', 'UxEvaluator', 'PlanReviewer', 'Engineer', 'QaVerifier', 'Done')]
    [string] $Role,

    [ValidateSet('D1', 'D2', 'D3', 'D5')]
    [string] $Pipeline,

    [ValidateSet('Pending', 'Approved', 'ChangesRequested')]
    [string] $SetPlanReview,

    [ValidateSet('Pending', 'PASS', 'FAIL', 'N/A')]
    [string] $SetUxReview,

    [ValidateSet('IN_PROGRESS', 'COMPLETE')]
    [string] $ProductDelivery,

    [switch] $InitFullDelivery,
    [switch] $EmitPrompt,
    [switch] $Continuous,
    [switch] $SinglePhase,
    [switch] $Advance,
    [switch] $Rollback,
    [switch] $CompleteWave,
    [switch] $Reset
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repoRoot 'deploy\desktop\run'
$statusPath = Join-Path $runDir 'pipeline-status.json'
$feedPath = Join-Path $runDir 'pipeline-latest-prompt.txt'
$matrixPath = Join-Path $repoRoot 'specs\031-desktop-java-client\contracts\feature-parity-matrix.json'
$promptCatalog = Join-Path $repoRoot '.cursor\prompts\desktop-client-pipeline.md'
$fullDeliveryPrompt = Join-Path $repoRoot '.cursor\prompts\desktop-client-full-delivery.md'

$Sequences = @{
    D1 = @('Analyst', 'Engineer', 'QaVerifier', 'Done')
    D2 = @('Analyst', 'Architect', 'Designer', 'UxEvaluator', 'PlanReviewer', 'Engineer', 'QaVerifier', 'Done')
    D3 = @('Analyst', 'Architect', 'Designer', 'UxEvaluator', 'PlanReviewer', 'Done')
    D5 = @('QaVerifier', 'Done')
}

$WaveOrder = @('W0', 'W1', 'W2', 'W3', 'W4')

function Write-Utf8File {
    param([string] $Path, [string] $Content)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $utf8Bom = New-Object System.Text.UTF8Encoding $true
    [System.IO.File]::WriteAllText($Path, $Content, $utf8Bom)
}

function Get-DefaultStatus {
    param([string] $W, [string] $P, [string] $R)
    [ordered]@{
        schema_version   = 1
        spec             = '031-desktop-java-client'
        wave             = $W
        pipeline         = $P
        role             = $R
        plan_review      = 'PENDING'
        ux_review        = 'PENDING'
        product_delivery = 'IN_PROGRESS'
        started_at       = (Get-Date).ToUniversalTime().ToString('o')
        updated_at       = (Get-Date).ToUniversalTime().ToString('o')
        history          = @()
    }
}

function Read-Status {
    if (-not (Test-Path $statusPath)) { return $null }
    $status = Get-Content -Raw -Path $statusPath -Encoding UTF8 | ConvertFrom-Json
    if (-not $status.ux_review) { $status | Add-Member -NotePropertyName ux_review -NotePropertyValue 'PENDING' -Force }
    $status
}

function Write-Status {
    param($Status)
    if (-not (Test-Path $runDir)) {
        New-Item -ItemType Directory -Path $runDir -Force | Out-Null
    }
    $Status.updated_at = (Get-Date).ToUniversalTime().ToString('o')
    Write-Utf8File -Path $statusPath -Content ($Status | ConvertTo-Json -Depth 12)
}

function Get-RoleSkill {
    param([string] $R)
    switch ($R) {
        'Analyst'      { '.cursor/skills/korus-desktop-role-analyst/SKILL.md' }
        'Architect'    { '.cursor/skills/korus-desktop-role-architect/SKILL.md' }
        'Designer'     { '.cursor/skills/korus-desktop-role-designer/SKILL.md' }
        'UxEvaluator'  { '.cursor/skills/korus-desktop-role-ux-evaluator/SKILL.md' }
        'PlanReviewer' { '.cursor/skills/korus-desktop-role-plan-reviewer/SKILL.md' }
        'Engineer'     { '.cursor/skills/korus-desktop-role-engineer/SKILL.md' }
        'QaVerifier'   { '.cursor/skills/korus-desktop-role-qa-verifier/SKILL.md' }
        default        { '.cursor/skills/korus-desktop-orchestrator/SKILL.md' }
    }
}

function Get-WaveGuide {
    param([string] $W)
    ".cursor/skills/korus-desktop-orchestrator/waves/$W.md"
}

function Get-MatrixRowsForWave {
    param([string] $W)
    if (-not (Test-Path $matrixPath)) { return @() }
    $matrix = Get-Content -Raw -Path $matrixPath -Encoding UTF8 | ConvertFrom-Json
    @($matrix.rows | Where-Object { $_.wave -eq $W })
}

function Get-SmokeCommandsForWave {
    param([string] $W)
    switch ($W) {
        'W0' { @('.\scripts\smoke-desktop-health.ps1', '.\scripts\smoke-desktop-auth.ps1 -SkipUi') }
        'W1' { @('.\scripts\smoke-desktop-profiles.ps1', '.\scripts\smoke-desktop-multi-server.ps1') }
        'W2' { @('.\scripts\smoke-desktop-messaging.ps1') }
        'W3' { @('.\scripts\smoke-desktop-capabilities.ps1', '.\scripts\smoke-desktop-search.ps1', '.\scripts\smoke-desktop-calls.ps1') }
        'W4' { @('.\scripts\smoke-desktop-update-manifest.ps1', '.\scripts\smoke-desktop-full-parity.ps1') }
        default { @() }
    }
}

function Get-ArtifactDir {
    param([string] $W)
    "specs/031-desktop-java-client/artifacts/waves/$W"
}

function Add-History {
    param($Status, [string] $Note)
    if (-not $Status.history) { $Status.history = @() }
    $Status.history += @{
        at   = (Get-Date).ToUniversalTime().ToString('o')
        note = $Note
        wave = $Status.wave
        role = $Status.role
    }
}

function Get-Sequence {
    param([string] $P)
    $key = if ($Sequences.ContainsKey($P)) { $P } else { 'D2' }
    $Sequences[$key]
}

function Build-RolePrompt {
    param($Status, [bool] $ContinuousMode)

    $w = [string]$Status.wave
    $role = [string]$Status.role
    $pipe = [string]$Status.pipeline
    $skill = Get-RoleSkill -R $role
    $guide = Get-WaveGuide -W $w
    $artifactDir = Get-ArtifactDir -W $w
    $rows = Get-MatrixRowsForWave -W $w
    $rowLines = ($rows | ForEach-Object { "- $($_.id) ($($_.status))" }) -join "`n"
    if (-not $rowLines) { $rowLines = '- (none in matrix)' }

    $smokes = (Get-SmokeCommandsForWave -W $w) -join "`n  "
    $planReview = [string]$Status.plan_review
    $uxReview = if ($Status.ux_review) { [string]$Status.ux_review } else { 'PENDING' }

    $engineerBlock = ''
    if ($role -eq 'Engineer' -and $pipe -in @('D2', 'D3')) {
        if ($planReview -ne 'APPROVED') {
            $engineerBlock = @"

!!! BLOCKED: plan_review=$planReview — ENGINEER forbidden.
Run PlanReviewer or: .\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved
"@
        }
        elseif ($uxReview -notin @('PASS', 'N/A')) {
            $engineerBlock = @"

!!! BLOCKED: ux_review=$uxReview — ENGINEER forbidden until UX PASS or N/A (+SDK_ONLY).
Run UxEvaluator or: .\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview PASS
"@
        }
    }

    $reviewerBlock = ''
    if ($role -eq 'PlanReviewer') {
        $reviewerBlock = @"

Precondition: ux_review should be PASS or N/A (current: $uxReview)

After verdict:
  APPROVED:     .\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved -Advance
  CHANGES:      .\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview ChangesRequested -Rollback
"@
    }

    $uxBlock = ''
    if ($role -eq 'UxEvaluator') {
        $uxBlock = @"

After verdict:
  PASS:  .\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview PASS -Advance
  FAIL:  .\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview FAIL -Rollback
  N/A:   .\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview N/A -Advance   # +SDK_ONLY only
"@
    }

    $qaBlock = ''
    if ($role -eq 'QaVerifier') {
        $qaBlock = @"

After PASS:
  .\scripts\Start-KorusDesktopPipeline.ps1 -Advance
  .\scripts\Start-KorusDesktopPipeline.ps1 -CompleteWave
"@
    }

    $flowBlock = if ($ContinuousMode) {
        @"

=== CONTINUOUS CONVEYOR — do NOT stop after one role ===
Build track: Engineer/QA on current wave (fix until green).
Prepare track: Analyst/Architect/Designer on W+1 in parallel when possible.
After gate PASS: auto -Advance / -SetUxReview PASS / -SetPlanReview Approved in same session.
Stop only: product_delivery=COMPLETE or hard blocker (WHPX, creds).
Guide: specs/031-desktop-java-client/design/continuous-conveyor.md
"@
    } else {
        @"

=== SINGLE PHASE (legacy) — one role then pause ===
"@
    }

    @"
[DESKTOP-PIPELINE] wave=$w pipeline=$pipe role=$role plan_review=$planReview ux_review=$uxReview product_delivery=$($Status.product_delivery)
$flowBlock

Full product delivery: .cursor/prompts/desktop-client-full-delivery.md

1. Orchestrator: .cursor/skills/korus-desktop-orchestrator/SKILL.md
2. Role skill: $skill
3. Wave guide: $guide
4. Artifact dir: $artifactDir/
5. Role details (Russian): .cursor/prompts/desktop-client-pipeline.md — section $role

Matrix rows ($w):
$rowLines

Gradle (Engineer/QA):
  .\gradlew.bat :modules:desktop-client-sdk:test
  .\gradlew.bat buildIntegrity

Smokes ($w):
  $smokes

Precondition: http://127.0.0.1:18080/api/v1/health -> 200 (else qemu-stack-cycle)
$engineerBlock
$uxBlock
$reviewerBlock
$qaBlock

Next tick: .\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt
Advance role: .\scripts\Start-KorusDesktopPipeline.ps1 -Advance
"@
}

# --- Main ---

if ($Reset -and (Test-Path $statusPath)) {
    Remove-Item -Force $statusPath
    Write-Host "Reset: removed $statusPath"
}

$status = Read-Status

if ($InitFullDelivery) {
    $status = Get-DefaultStatus -W 'W0' -P 'D2' -R 'Analyst'
    $status.product_delivery = 'IN_PROGRESS'
    Add-History -Status $status -Note 'InitFullDelivery (reset)'
    Write-Status $status
    Write-Host 'Initialized full delivery: W0 / Analyst / D2' -ForegroundColor Green
}
elseif ($null -eq $status) {
    $status = Get-DefaultStatus -W ($(if ($Wave) { $Wave } else { 'W0' })) `
        -P ($(if ($Pipeline) { $Pipeline } else { 'D2' })) `
        -R ($(if ($Role) { $Role } else { 'Analyst' }))
    Write-Status $status
}

if ($Wave) { $status.wave = $Wave }
if ($Pipeline) { $status.pipeline = $Pipeline }
if ($Role) { $status.role = $Role }
if ($ProductDelivery) { $status.product_delivery = $ProductDelivery }

if ($SetPlanReview) {
    $status.plan_review = $SetPlanReview
    Add-History -Status $status -Note "SetPlanReview=$SetPlanReview"
}

if ($SetUxReview) {
    $status.ux_review = $SetUxReview
    Add-History -Status $status -Note "SetUxReview=$SetUxReview"
}

if ($Rollback) {
    $seq = Get-Sequence -P ([string]$status.pipeline)
    $current = [string]$status.role
    $idx = [array]::IndexOf($seq, $current)
    if ($idx -gt 0) {
        $prev = $seq[$idx - 1]
        if ($current -eq 'Engineer' -or $current -eq 'PlanReviewer') {
            $status.role = 'Architect'
        }
        elseif ($current -eq 'UxEvaluator') {
            $status.role = 'Designer'
        }
        else {
            $status.role = $prev
        }
        if ($current -in @('Engineer', 'PlanReviewer', 'UxEvaluator')) {
            $status.plan_review = 'PENDING'
        }
        if ($current -in @('Engineer', 'PlanReviewer', 'UxEvaluator')) {
            $status.ux_review = 'PENDING'
        }
        Add-History -Status $status -Note "Rollback from $current"
    }
}

if ($Advance) {
    $seq = Get-Sequence -P ([string]$status.pipeline)
    $current = [string]$status.role
    $idx = [array]::IndexOf($seq, $current)
    if ($idx -lt 0) { $idx = 0 }
    $nextIdx = [Math]::Min($idx + 1, $seq.Length - 1)
    $next = $seq[$nextIdx]
    Add-History -Status $status -Note "Advance $current -> $next"
    $status.role = $next
}

if ($CompleteWave) {
    $w = [string]$status.wave
    $idx = [array]::IndexOf($WaveOrder, $w)
    if ($idx -ge 0 -and $idx -lt ($WaveOrder.Length - 1)) {
        $nextWave = $WaveOrder[$idx + 1]
        Add-History -Status $status -Note "CompleteWave $w -> $nextWave"
        $status.wave = $nextWave
        $status.role = 'Analyst'
        $status.plan_review = 'PENDING'
        $status.ux_review = 'PENDING'
    }
    elseif ($w -eq 'W4') {
        $status.role = 'Done'
        $status.product_delivery = 'COMPLETE'
        Add-History -Status $status -Note 'Product delivery COMPLETE'
    }
}

Write-Status $status

$prompt = Build-RolePrompt -Status $status -ContinuousMode:(
    $Continuous -or (($EmitPrompt -or $InitFullDelivery -or $PSBoundParameters.Count -eq 0) -and -not $SinglePhase)
)

Write-Host ''
Write-Host '=== Korus Desktop Pipeline (spec 031) ===' -ForegroundColor Cyan
Write-Host "Status: $statusPath"
Write-Host "Wave: $($status.wave)  Pipeline: $($status.pipeline)  Role: $($status.role)"
Write-Host "Plan review: $($status.plan_review)  UX review: $($status.ux_review)  Delivery: $($status.product_delivery)"
Write-Host ''

if ($EmitPrompt -or $InitFullDelivery -or $PSBoundParameters.Count -eq 0) {
    Write-Host $prompt
    Write-Utf8File -Path $feedPath -Content $prompt
    Write-Host ''
    Write-Host "Prompt saved (UTF-8 BOM): $feedPath" -ForegroundColor DarkGray
}

if (Test-Path $fullDeliveryPrompt) {
    Write-Host "Full delivery: $fullDeliveryPrompt" -ForegroundColor DarkGray
}
