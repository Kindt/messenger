$j = Get-Content (Join-Path $PSScriptRoot "run\issues.json") -Raw | ConvertFrom-Json
$j |
    Where-Object { $_.type -in @("BUG", "VULNERABILITY") -or $_.severity -eq "BLOCKER" } |
    Sort-Object type, severity, file, line |
    ForEach-Object {
        "{0,-10} {1,-15} {2,-14} L{3,-5} {4}" -f $_.severity, $_.type, $_.rule, $_.line, $_.file
        "  $($_.message)"
    }
