# True only for current-session comprehensive GREEN (145/145), not stale standard runs.
function Test-VppComprehensiveGreen {
    param(
        [object]$Green,
        [datetime]$SessionStart = [datetime]::MinValue
    )
    if (-not $Green) { return $false }
    if ($Green.full_coverage -ne $true) { return $false }
    $total = [int]($Green.gates_total)
    $pass = [int]($Green.gates_pass)
    if ($total -lt 145 -or $pass -lt $total) { return $false }
    if ($Green.level -and "$($Green.level)" -ne 'full') { return $false }
    if ($SessionStart -gt [datetime]::MinValue -and $Green.timestamp) {
        try {
            $gt = [datetime]::Parse([string]$Green.timestamp)
            if ($gt.ToUniversalTime() -lt $SessionStart.ToUniversalTime()) { return $false }
        } catch { return $false }
    }
    return $true
}
