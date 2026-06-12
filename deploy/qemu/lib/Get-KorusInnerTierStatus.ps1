# Inner-tier status for US9 fast acceptance (outer gate reads this).

function Get-KorusInnerTierStatusPath {
    param([Parameter(Mandatory)][string]$RunDir)
    Join-Path $RunDir "inner-tier-status.json"
}

function Get-KorusPlaywrightTiersManifest {
    param([Parameter(Mandatory)][string]$Root)
    $path = Join-Path $Root "tests\e2e-web\playwright-tiers.json"
    if (-not (Test-Path $path)) { throw "missing playwright-tiers.json" }
    Get-Content $path -Raw | ConvertFrom-Json
}

function Get-KorusInnerTierStatus {
    param([Parameter(Mandatory)][string]$RunDir)
    $path = Get-KorusInnerTierStatusPath -RunDir $RunDir
    $empty = @{ tiers = @{}; allInnerPass = $false; at = $null }
    if (-not (Test-Path $path)) { return $empty }
    try {
        $o = Get-Content $path -Raw | ConvertFrom-Json
        $tiers = @{}
        if ($o.tiers) {
            $o.tiers.PSObject.Properties | ForEach-Object {
                $tiers[$_.Name] = @{
                    pass      = [bool]$_.Value.pass
                    at        = [string]$_.Value.at
                    lastError = [string]$_.Value.lastError
                }
            }
        }
        return @{
            tiers        = $tiers
            allInnerPass = [bool]$o.allInnerPass
            at           = [string]$o.at
        }
    } catch {
        return $empty
    }
}

function Set-KorusInnerTierResult {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$TierName,
        [Parameter(Mandatory)][bool]$Pass,
        [string]$LastError = ""
    )
    $manifest = Get-KorusPlaywrightTiersManifest -Root $Root
    $inner = @($manifest.innerTierNames)
    $state = Get-KorusInnerTierStatus -RunDir $RunDir
    $tiers = @{}
    foreach ($k in $state.tiers.Keys) { $tiers[$k] = $state.tiers[$k] }
    $tiers[$TierName] = @{
        pass      = $Pass
        at        = if ($Pass) { (Get-Date).ToString('o') } else { $null }
        lastError = if ($Pass) { "" } else { $LastError }
    }
    $allPass = $true
    foreach ($n in $inner) {
        if (-not $tiers.ContainsKey($n) -or -not $tiers[$n].pass) { $allPass = $false; break }
    }
    $out = @{
        at           = (Get-Date).ToString('o')
        allInnerPass = $allPass
        tiers        = $tiers
    }
    $out | ConvertTo-Json -Depth 5 | Set-Content -Path (Get-KorusInnerTierStatusPath -RunDir $RunDir) -Encoding utf8
    return $out
}

function Test-KorusAllInnerTiersPass {
    param([Parameter(Mandatory)][string]$RunDir)
    $s = Get-KorusInnerTierStatus -RunDir $RunDir
    return [bool]$s.allInnerPass
}
