# TLS smoke wrapper: reads korus_tls_domain from inventory all.yml (T602/T607).
param(
    [ValidateSet("stage", "prod")]
    [string]$Inventory = "stage",
    [switch]$SkipTls
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$allYml = Join-Path $repoRoot "deploy\ansible\inventory\$Inventory\group_vars\all.yml"
if (-not (Test-Path $allYml)) { Write-Error "Missing $allYml" }
$domain = $null
foreach ($line in Get-Content $allYml) {
    if ($line -match '^\s*korus_tls_domain:\s*"(.+)"\s*$') { $domain = $Matches[1]; break }
    if ($line -match "^\s*korus_tls_domain:\s*(.+)\s*$") { $domain = $Matches[1].Trim('"'); break }
}
if (-not $domain -or $domain -match "example\.com") {
    Write-Error "Set real korus_tls_domain in $allYml before TLS smoke"
}
$http = "http://$domain"
$https = "https://$domain"
$args = @("-HttpUrl", $http, "-HttpsUrl", $https, "-ExpectedCertSubject", $domain)
if ($SkipTls) { $args += "-SkipTls" }
& "$repoRoot\scripts\smoke-tls-redirect.ps1" @args
