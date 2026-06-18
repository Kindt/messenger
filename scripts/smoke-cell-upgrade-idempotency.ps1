# Spec 011 T01124 partial: run cell-upgrade.yml twice on internal-dev inventory (QEMU server guest).
# Full 2-Cell blast-radius test requires commercial multi-cell host (LSO-020, Sep 2026+).
param(
    [string]$CellId = "internal-dev",
    [string]$ImagesTag = "dev-smoke",
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-cell-upgrade-idempotency.ps1 [-CellId internal-dev] [-ImagesTag dev-smoke]"
    Write-Host "Run on server guest via SSH or qemu guest exec. Host: orchestrates plink smoke only."
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$inventory = Join-Path $repoRoot "deploy\ansible\inventory\cells\$CellId\hosts.yml"
$playbook = Join-Path $repoRoot "deploy\ansible\playbooks\cell-upgrade.yml"
if (-not (Test-Path $inventory)) {
    Write-Host "[FAIL] inventory missing: $inventory"
    exit 1
}
if (-not (Test-Path $playbook)) {
    Write-Host "[FAIL] playbook missing: $playbook"
    exit 1
}

Write-Host "=== cell-upgrade idempotency smoke (single cell) ==="
Write-Host "  cell_id=$CellId images_tag=$ImagesTag"
Write-Host "  inventory=$inventory"
Write-Host ""
Write-Host "On server guest (192.168.76.10), run twice:"
Write-Host @"
  cd /opt/korus/repo/deploy/ansible
  ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml \
    -e cell_id=$CellId -e images_tag=$ImagesTag
  ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml \
    -e cell_id=$CellId -e images_tag=$ImagesTag
"@
Write-Host ""
Write-Host "[OK] scaffold documented; formal 2-Cell LSO-020 deferred to Sep 2026+"
