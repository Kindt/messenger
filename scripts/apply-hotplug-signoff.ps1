# Record Architecture / PO / Ops approval for hot-plug ADR (T048, T056).
# Run after reviewers confirm — does NOT substitute for real sign-off.
param(
    [Parameter(Mandatory = $true)]
    [string]$ArchitectureOwner,
    [Parameter(Mandatory = $true)]
    [string]$ProductOwner,
    [Parameter(Mandatory = $true)]
    [string]$OpsSre,
    [string]$PeerReviewer = "",
    [string]$Date = "",
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $Date) { $Date = (Get-Date -Format "yyyy-MM-dd") }

$adr = Join-Path $root "docs\adr\ADR-hotplug-deployment-split.md"
$proposal = Join-Path $root "docs\proposals\constitution-v1.1-hotplug-bounded-exception.md"
$constitution = Join-Path $root ".specify\memory\constitution.md"
$tasks = Join-Path $root "specs\001-system-review-refactoring\tasks.md"

function Set-FileText([string]$Path, [string]$Content) {
    if ($WhatIf) {
        Write-Host "[what-if] would update $Path" -ForegroundColor DarkGray
        return
    }
    Set-Content -Path $Path -Value $Content -Encoding utf8NoBOM
}

$adrText = Get-Content $adr -Raw
$adrText = $adrText -replace '\*\*Status:\*\* `proposed`[^\r\n]*', "**Status:** ``accepted`` ($Date)"
$adrText = $adrText -replace '\| Architecture/PO/Ops approval \| ⏳ pending[^\|]*\|', "| Architecture/PO/Ops approval | ✅ signed $Date |"
$adrText = $adrText -replace '- \[ \] Architecture owner approval', '- [x] Architecture owner approval'
$adrText = $adrText -replace '- \[ \] Product owner approval', '- [x] Product owner approval'
$adrText = $adrText -replace '- \[ \] Ops/SRE approval[^\r\n]*', '- [x] Ops/SRE approval for monitoring and runbook updates'
$adrText = $adrText -replace '- \[ \] Constitution exception[^\r\n]*', '- [x] Constitution exception/amendment note accepted for this bounded scope'
$adrText = $adrText -replace '\| Architecture Owner \|  \| Pending \|  \|  \|', "| Architecture Owner | $ArchitectureOwner | Accepted | $Date | Bounded deployment split |"
$adrText = $adrText -replace '\| Product Owner \|  \| Pending \|  \|  \|', "| Product Owner | $ProductOwner | Accepted | $Date | Indexer hot-plug scope |"
$adrText = $adrText -replace '\| Ops/SRE \|  \| Pending \|  \|  \|', "| Ops/SRE | $OpsSre | Accepted | $Date | Smoke runbook + metrics |"
if ($PeerReviewer) {
    $adrText = $adrText -replace '\| Reviewer 2 \(peer\) \|  \| Pending \|  \|  \|', "| Reviewer 2 (peer) | $PeerReviewer | Accepted | $Date | Peer review |"
}
Set-FileText $adr $adrText

$propText = Get-Content $proposal -Raw
$propText = $propText -replace '\*\*Status:\*\* `ready_for_review`[^\r\n]*', "**Status:** ``accepted`` ($Date)"
Set-FileText $proposal $propText

$constText = Get-Content $constitution -Raw
$amendment = @"

> **Bounded Deployment Split Exception**  
> Selected workers may run as separate deployable processes when all conditions are met:
> (1) compile-time dependency direction remains unchanged;  
> (2) all integration stays contract-first via documented NATS subjects/payloads;  
> (3) core-api supports graceful degradation if worker is absent;  
> (4) observability and smoke-test parity are provided;  
> (5) scope is explicitly approved via ADR and is feature-bounded.

"@
if ($constText -notmatch 'Bounded Deployment Split Exception') {
    $constText = $constText -replace '(### V\. Clean Architecture, Modular Monolith\r?\n[^\r\n]+\r?\n[^\r\n]+\r?\n[^\r\n]+\r?\n)', "`$1$amendment"
}
$constText = $constText -replace '\*\*Version\*\*: 1\.0\.0 \| \*\*Ratified\*\*: ([^|]+) \| \*\*Last Amended\*\*: [^\r\n]+', "**Version**: 1.1.0 | **Ratified**: `${1} | **Last Amended**: $Date"
Set-FileText $constitution $constText

$tasksText = Get-Content $tasks -Raw
$tasksText = $tasksText -replace '- \[ \] T048[^\r\n]*', "- [x] T048 Create and approve ADR — accepted $Date ($ArchitectureOwner, $ProductOwner, $OpsSre)"
$tasksText = $tasksText -replace '- \[ \] T056[^\r\n]*', "- [x] T056 [US3] Collect approvals in ADR and constitution exception — accepted $Date"
Set-FileText $tasks $tasksText

Write-Host "[OK] Hot-plug governance sign-off recorded ($Date)" -ForegroundColor Green
Write-Host "Review diffs, then commit: git add docs/ .specify/ specs/ && git commit -m 'docs: accept hot-plug ADR and constitution v1.1'" -ForegroundColor DarkGray
