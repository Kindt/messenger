# Stage readiness checklist (spec 007 T601-T607 prep). ASCII-only for PS 5.1.
param(
    [switch]$Strict
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$checks = @(
    @{ id = "T601-inventory"; path = "deploy/ansible/inventory/stage/hosts.yml"; kind = "file" },
    @{ id = "T601-vault-example"; path = "deploy/ansible/inventory/stage/group_vars/vault.yml.example"; kind = "file" },
    @{ id = "T601-readme"; path = "deploy/ansible/inventory/stage/README.md"; kind = "file" },
    @{ id = "T602-tls-runbook"; path = "docs/review/stage-tls-smoke-runbook.md"; kind = "file" },
    @{ id = "T603-e2ee-checklist"; path = "docs/review/e2ee-staging-checklist.md"; kind = "file" },
    @{ id = "T403-e2ee-packet"; path = "docs/review/e2ee-security-signoff-packet-2026-06-15.md"; kind = "file" },
    @{ id = "T604-k6-scripts"; path = "scripts/load/pilot-health.js"; kind = "file" },
    @{ id = "T604-k6-runner"; path = "scripts/run-k6-qemu-baseline.ps1"; kind = "file" },
    @{ id = "T605-hotplug-script"; path = "scripts/apply-hotplug-signoff.ps1"; kind = "file" },
    @{ id = "T605-hotplug-template"; path = "docs/review/hotplug-signoff-request-template.md"; kind = "file" },
    @{ id = "T607-tls-smoke-script"; path = "scripts/smoke-tls-redirect.ps1"; kind = "file" }
)

$fail = 0
foreach ($c in $checks) {
    $full = Join-Path $repoRoot $c.path
    $ok = Test-Path $full
    if ($ok) {
        Write-Host "[OK] $($c.id) $($c.path)"
    } else {
        Write-Host "[MISS] $($c.id) $($c.path)"
        $fail++
    }
}

# Operator-only (cannot auto-verify without stage host)
Write-Host ""
Write-Host "Operator backlog (manual on stage host):"
Write-Host "  - T601: customize hosts.yml + encrypt vault.yml"
Write-Host "  - T602: smoke-tls-redirect.ps1 with real HTTPS URL"
Write-Host "  - T603: e2ee-staging-checklist rows 4-6"
Write-Host "  - T605: apply-hotplug-signoff.ps1 with named signers"
Write-Host "  - T606: E2EE QA formal sign (ops-signoff-log US7 row 8)"
Write-Host "  - T607: ansible-playbook --tags tls_smoke on prod inventory"

if ($Strict -and $fail -gt 0) {
    Write-Error "Stage prep checklist failed: $fail missing artifacts"
}
exit $fail
