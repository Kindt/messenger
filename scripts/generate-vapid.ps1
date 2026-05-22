# Generate VAPID keys for Web Push (web-client + push-worker).
# Requires Node.js/npx. Prints keys and optional .env snippet.
param(
    [string]$EnvSnippetPath
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot

Write-Host "Generating VAPID keys (web-push)..." -ForegroundColor Cyan
$out = & npx --yes web-push generate-vapid-keys 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "npx web-push failed. Install Node.js or run: npm install -g web-push"
}
Write-Host $out

$public = ($out | Select-String -Pattern "Public Key:\s*(.+)$").Matches.Groups[1].Value.Trim()
$private = ($out | Select-String -Pattern "Private Key:\s*(.+)$").Matches.Groups[1].Value.Trim()
if (-not $public -or -not $private) {
    Write-Warning "Could not parse keys from output; copy manually from above."
    exit 0
}

$snippet = @"
# Web Push VAPID (same public key on web-client and push-worker)
WEB_CLIENT_VAPID_PUBLIC_KEY=$public
PUSH_VAPID_PUBLIC_KEY=$public
PUSH_VAPID_PRIVATE_KEY=$private
PUSH_VAPID_SUBJECT=mailto:notify@localhost
"@

Write-Host ""
Write-Host "Suggested env:" -ForegroundColor Green
Write-Host $snippet

if ($EnvSnippetPath) {
    $snippet | Set-Content -Path $EnvSnippetPath -Encoding UTF8
    Write-Host "Wrote $EnvSnippetPath" -ForegroundColor Green
}
