# Sprint A entry: artifact checklist + inventory validation + next-step hints (T601-T607).
param(
    [ValidateSet("stage", "prod")]
    [string]$Inventory = "stage",
    [switch]$SkipVaultCheck
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & "$repoRoot\scripts\stage-readiness-checklist.ps1" -Strict
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & "$repoRoot\scripts\validate-stage-inventory.ps1" -Inventory $Inventory @(
        if ($SkipVaultCheck) { "-SkipVaultCheck" }
    )
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host ""
    Write-Host "Next steps (on control node with ansible + vault password):"
    Write-Host "  cd deploy/ansible"
    Write-Host "  ansible-playbook -i inventory/$Inventory/hosts.yml playbooks/site.yml --ask-vault-pass --check --diff"
    Write-Host "  ansible-playbook -i inventory/$Inventory/hosts.yml playbooks/site.yml --ask-vault-pass"
    Write-Host "  .\..\..\scripts\stage-tls-smoke.ps1 -Inventory $Inventory"
    Write-Host "  .\..\..\scripts\smoke-e2ee-staging.ps1 -BaseUrl https://<your-domain>"
    Write-Host "  .\..\..\scripts\run-k6-stage-baseline.ps1 -BaseUrl https://<your-domain>"
} finally {
    Pop-Location
}
