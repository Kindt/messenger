# Push to origin bypassing corporate HTTP proxy (GitHub direct).
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GitArgs
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Push-Location $Root
try {
    if (-not $GitArgs -or $GitArgs.Count -eq 0) {
        $GitArgs = @("-u", "origin", "HEAD")
    }
    & git -c http.proxy= -c https.proxy= push @GitArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
