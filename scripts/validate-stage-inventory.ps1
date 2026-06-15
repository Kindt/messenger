# Validate stage/prod inventory before first deploy (T601 preflight). ASCII-only.
param(
    [ValidateSet("stage", "prod")]
    [string]$Inventory = "stage",
    [switch]$SkipVaultCheck
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$invDir = Join-Path $repoRoot "deploy\ansible\inventory\$Inventory"
$allYml = Join-Path $invDir "group_vars\all.yml"
$hostsYml = Join-Path $invDir "hosts.yml"
$vaultYml = Join-Path $invDir "group_vars\vault.yml"

$fail = 0
function Fail([string]$msg) { Write-Host "[FAIL] $msg"; $script:fail++ }
function Ok([string]$msg) { Write-Host "[OK] $msg" }

if (-not (Test-Path $hostsYml)) { Fail "missing hosts.yml: $hostsYml" } else {
    $hosts = Get-Content $hostsYml -Raw
    if ($hosts -match "example\.com") { Fail "hosts.yml still has example.com placeholders" } else { Ok "hosts.yml customized" }
}
if (-not (Test-Path $allYml)) { Fail "missing all.yml" } else {
    $all = Get-Content $allYml -Raw
    if ($all -notmatch "korus_tls_domain") { Fail "all.yml missing korus_tls_domain" }
    else { Ok "all.yml has TLS domain" }
}
if (-not $SkipVaultCheck) {
    if (-not (Test-Path $vaultYml)) {
        Fail "vault.yml missing (copy from vault.yml.example and ansible-vault encrypt)"
    } else {
        $head = Get-Content $vaultYml -TotalCount 3 -ErrorAction SilentlyContinue
        if ($head -join "`n" -match '\$ANSIBLE_VAULT') { Ok "vault.yml is encrypted" }
        elseif ($head -join "`n" -match "CHANGE_ME") { Fail "vault.yml is plaintext with CHANGE_ME" }
        else { Ok "vault.yml present" }
    }
}
exit $fail
