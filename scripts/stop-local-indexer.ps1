# Stop orphan :services:indexer:run / gradlew processes (Windows).
$ErrorActionPreference = "SilentlyContinue"
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'IndexerServiceApp|services:indexer:run' } |
    ForEach-Object {
        Write-Host "Stopping PID $($_.ProcessId) ..." -ForegroundColor Yellow
        taskkill.exe /PID $_.ProcessId /T /F | Out-Null
    }
Write-Host "[OK] local indexer processes stopped (if any were running)" -ForegroundColor Green
