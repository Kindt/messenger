# Verifies OpenAPI document lists export-compliance-prep (requires running core-api on :8080).
param(
    [string]$BaseUrl = "http://localhost:8080"
)
$ErrorActionPreference = "Stop"

$paths = @(
    "/api/openapi.json",
    "/api/openapi.yaml"
)
$found = $false
foreach ($p in $paths) {
    try {
        $doc = Invoke-WebRequest -Uri "$BaseUrl$p" -UseBasicParsing
        if ($doc.Content -match "export-compliance-prep") {
            Write-Host "[OK] export-compliance-prep in OpenAPI at $p" -ForegroundColor Green
            $found = $true
            break
        }
    } catch {
        Write-Host "  skip $p ($($_.Exception.Message))" -ForegroundColor DarkGray
    }
}
if (-not $found) {
    throw "export-compliance-prep not found in OpenAPI (is core-api up on $BaseUrl?)"
}
