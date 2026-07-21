# Spec 011 T01124 partial: cell-upgrade idempotency + manifest validate (host + guest).
# Full 2-Cell blast-radius test requires commercial multi-cell host (LSO-020, Sep 2026+).
param(
    [string]$CellId = "internal-dev",
    [string]$ImagesTag = "dev-smoke",
    [switch]$HostOnly,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-cell-upgrade-idempotency.ps1 [-CellId internal-dev] [-ImagesTag dev-smoke] [-HostOnly]"
    Write-Host "HostOnly: validate manifest twice + inventory/playbook checks (no SSH)."
    Write-Host "Default: HostOnly checks, then plink ansible twice on server guest when VM up."
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$manifest = Join-Path $repoRoot "deploy\cloud\cells\$CellId.yaml"
$inventory = Join-Path $repoRoot "deploy\ansible\inventory\cells\$CellId\hosts.yml"
$playbook = Join-Path $repoRoot "deploy\ansible\playbooks\cell-upgrade.yml"
$validator = Join-Path $repoRoot "scripts\validate-cell-manifest.py"

function Invoke-ManifestValidateTwice {
    param([string]$Path)
    if (-not (Test-Path $validator)) {
        Write-Host "[FAIL] validator missing: $validator"
        exit 1
    }
    if (-not (Test-Path $Path)) {
        Write-Host "[FAIL] manifest missing: $Path"
        exit 1
    }
    Write-Host "[host] validate-cell-manifest (pass 1)"
    & python $validator $Path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "[host] validate-cell-manifest (pass 2, idempotency)"
    & python $validator $Path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "[OK] manifest validate x2"
}

Write-Host "=== cell-upgrade idempotency smoke (single cell) ==="
Write-Host "  cell_id=$CellId images_tag=$ImagesTag"
Write-Host ""

Invoke-ManifestValidateTwice -Path $manifest

if (-not (Test-Path $inventory)) {
    Write-Host "[FAIL] inventory missing: $inventory"
    exit 1
}
if (-not (Test-Path $playbook)) {
    Write-Host "[FAIL] playbook missing: $playbook"
    exit 1
}
Write-Host "[OK] inventory + playbook present"

if ($HostOnly) {
    Write-Host "[OK] HostOnly complete; guest ansible deferred"
    exit 0
}

$runDir = Join-Path $repoRoot "deploy\qemu\run"
$serverPid = Join-Path $runDir "server.pid"
if (-not (Test-Path $serverPid)) {
    Write-Host "[SKIP] server VM not running; guest ansible manual:"
    Write-Host @"
  cd /opt/korus/repo/deploy/ansible
  ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml \
    -e cell_id=$CellId -e images_tag=$ImagesTag
  ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml \
    -e cell_id=$CellId -e images_tag=$ImagesTag
"@
    Write-Host "[OK] host checks passed; formal 2-Cell LSO-020 deferred to Sep 2026+"
    exit 0
}

. (Join-Path $repoRoot "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort 12221
if (-not $hostKey) {
    Write-Host "[SKIP] SSH host key unavailable; run guest ansible manually (see above)"
    exit 0
}

$plinkExe = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
if (-not (Test-Path $plinkExe)) {
    $plinkCmd = Get-Command plink -ErrorAction SilentlyContinue
    if ($plinkCmd) { $plinkExe = $plinkCmd.Source }
}
if (-not (Test-Path $plinkExe)) {
    Write-Host "[SKIP] plink not found"
    exit 0
}

$guestCmd = "cd /opt/korus/repo/deploy/ansible && ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml -e cell_id=$CellId -e images_tag=$ImagesTag && ansible-playbook -i inventory/cells/$CellId/hosts.yml playbooks/cell-upgrade.yml -e cell_id=$CellId -e images_tag=$ImagesTag && echo CELL_UPGRADE_IDEMPOTENCY_OK"

Write-Host "[guest] cell-upgrade.yml x2 via plink"
$output = & $plinkExe -batch -ssh -P 12221 -hostkey $hostKey -pw korus korus@127.0.0.1 $guestCmd 2>&1 | Out-String
Write-Host $output
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] guest ansible exit $LASTEXITCODE"
    exit $LASTEXITCODE
}
if ($output -notmatch 'CELL_UPGRADE_IDEMPOTENCY_OK' -or $output -match '\[ERROR\]') {
    Write-Host "[FAIL] guest cell-upgrade did not complete cleanly"
    exit 1
}
Write-Host "[OK] guest cell-upgrade x2; formal 2-Cell LSO-020 deferred to Sep 2026+"
