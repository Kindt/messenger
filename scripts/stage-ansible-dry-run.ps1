# Prints ansible --check command for stage/prod (operator runs on Linux control node).
param(
    [ValidateSet("stage", "prod")]
    [string]$Inventory = "stage"
)

$ErrorActionPreference = "Stop"
Write-Host "Run on Ansible control node (Linux/WSL), not Windows host Docker:"
Write-Host ""
Write-Host "  cd deploy/ansible"
Write-Host "  ansible-playbook -i inventory/$Inventory/hosts.yml playbooks/site.yml --ask-vault-pass --check --diff"
Write-Host ""
Write-Host "Preflight on Windows host first:"
Write-Host "  .\scripts\preflight-stage-deploy.ps1 -Inventory $Inventory"
