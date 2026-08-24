#Requires -Version 5.1
# Launch mobile client agent pipeline phase — prints prompt + updates pipeline-status.json
param(
    [ValidateSet('W0', 'W1', 'W2', 'W3', 'W4')]
    [string]$Phase = 'W0',
    [ValidateSet('ANALYST', 'ARCHITECT', 'DESIGNER', 'UX_EVALUATOR', 'PLAN_REVIEWER', 'ENGINEER', 'QA_VERIFIER')]
    [string]$Role = 'ANALYST',
    [ValidateSet('', 'APPROVED', 'REJECTED')]
    [string]$PlanReviewStatus = '',
    [ValidateSet('', 'PASS', 'FAIL', 'N/A')]
    [string]$UxReviewStatus = '',
    [ValidateSet('', 'PASS', 'FAIL')]
    [string]$QaStatus = '',
    [switch]$Advance,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$PromptPath = Join-Path $Root '.cursor\prompts\mobile-client-pipeline.md'
$StatusScript = Join-Path $Root 'scripts\lib\Write-KorusMobilePipelineStatus.ps1'
$StatusPath = Join-Path $Root 'deploy\mobile\run\pipeline-status.json'

$RoleOrder = @('ANALYST', 'ARCHITECT', 'DESIGNER', 'UX_EVALUATOR', 'PLAN_REVIEWER', 'ENGINEER', 'QA_VERIFIER')

function Get-RoleSlug([string]$r) {
    switch ($r) {
        'PLAN_REVIEWER' { return 'plan-reviewer' }
        'QA_VERIFIER' { return 'qa-verifier' }
        'UX_EVALUATOR' { return 'ux-evaluator' }
        'DESIGNER' { return 'designer' }
        default { return $r.ToLower() }
    }
}

if ($Help) {
    Write-Host @"
Usage:
  .\scripts\Start-KorusMobilePipeline.ps1 [-Phase W0] [-Role ANALYST]
  .\scripts\Start-KorusMobilePipeline.ps1 -Advance [-Phase W0]
  .\scripts\Start-KorusMobilePipeline.ps1 -Phase W0 -Role PLAN_REVIEWER -PlanReviewStatus APPROVED

Phases: W0 scaffold, W1 multi-profile/server, W2 messaging, W3 add-ons, W4 updates
Roles:  ANALYST → ARCHITECT → DESIGNER → UX_EVALUATOR → PLAN_REVIEWER → ENGINEER → QA_VERIFIER

Status: deploy/mobile/run/pipeline-status.json
Prompt: .cursor/prompts/mobile-client-pipeline.md
Spec:   specs/032-mobile-native-client/
"@
    exit 0
}

if ($Advance) {
    $nextRole = 'ANALYST'
    if (Test-Path $StatusPath) {
        try {
            $st = Get-Content $StatusPath -Raw | ConvertFrom-Json
            if ($st.phase) { $Phase = [string]$st.phase }
            if ($st.role) {
                $idx = [array]::IndexOf($RoleOrder, [string]$st.role)
                if ($idx -ge 0 -and $idx -lt ($RoleOrder.Count - 1)) {
                    $nextRole = $RoleOrder[$idx + 1]
                } elseif ($idx -eq ($RoleOrder.Count - 1)) {
                    Write-Host "[INFO] Pipeline at last role (QA_VERIFIER). Run next wave or review qa-evidence." -ForegroundColor Yellow
                    $nextRole = 'QA_VERIFIER'
                }
            }
        } catch {
            Write-Host "[WARN] Could not read status; defaulting to ANALYST" -ForegroundColor Yellow
        }
    }
    $Role = $nextRole
    Write-Host "[INFO] Advance → Phase=$Phase Role=$Role" -ForegroundColor Cyan
}

. $StatusScript
Write-KorusMobilePipelineStatus -Phase $Phase -Role $Role -PlanReviewStatus $PlanReviewStatus -UxReviewStatus $UxReviewStatus -QaStatus $QaStatus -Detail "pipeline launched" | Out-Null

$roleSlug = Get-RoleSlug $Role

Write-Host "[OK] Mobile pipeline phase=$Phase role=$Role" -ForegroundColor Green
Write-Host "  status: deploy/mobile/run/pipeline-status.json" -ForegroundColor DarkGray
Write-Host ""
Write-Host "=== AGENT_PROMPT (copy to chat) ===" -ForegroundColor Cyan
Write-Host ""

if (Test-Path $PromptPath) {
    $raw = Get-Content $PromptPath -Raw
    if ($raw -match '(?s)<!-- AGENT_PROMPT_START -->(.*)<!-- AGENT_PROMPT_END -->') {
        $body = $Matches[1].Trim()
    } else {
        $body = $raw
    }
    $body = $body -replace '<PHASE>', $Phase
    $body = $body -replace '<ROLE>', $roleSlug
    Write-Host $body
} else {
    Write-Host "Prompt file missing: $PromptPath" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Skill: .cursor/skills/korus-mobile-role-$roleSlug/SKILL.md" -ForegroundColor DarkGray
exit 0
