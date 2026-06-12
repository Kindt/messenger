# Install superpowers skills into project .cursor/skills/ via directory junctions.
# Windows-friendly: junctions work without admin or Developer Mode.
#
# Usage:
#   .\.cursor\install-superpowers.ps1
#   .\.cursor\install-superpowers.ps1 -Uninstall

param(
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SkillsSrc = Join-Path $ProjectRoot '.cursor\superpowers\skills'
$SkillsDst = Join-Path $ProjectRoot '.cursor\skills'
$Prefix = 'superpowers-'

function Test-IsJunction {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $item = Get-Item -LiteralPath $Path
    return ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
}

function Remove-SkillJunction {
    param([string]$LinkPath)
    if (Test-IsJunction -Path $LinkPath) {
        cmd /c rmdir `"$LinkPath`" | Out-Null
        return $true
    }
    if (Test-Path -LiteralPath $LinkPath) {
        Write-Warning "Skipping non-junction path: $LinkPath"
    }
    return $false
}

function New-SkillJunction {
    param(
        [string]$LinkPath,
        [string]$TargetPath
    )
    $targetFull = (Resolve-Path -LiteralPath $TargetPath).Path
    $parent = Split-Path -Parent $LinkPath
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $result = cmd /c mklink /J `"$LinkPath`" `"$targetFull`" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "mklink failed for $LinkPath : $result"
    }
}

function Get-SuperpowersJunctions {
    if (-not (Test-Path -LiteralPath $SkillsDst)) { return @() }
    Get-ChildItem -LiteralPath $SkillsDst -Directory |
        Where-Object { $_.Name -like "${Prefix}*" } |
        Where-Object { Test-IsJunction -Path $_.FullName }
}

function Uninstall-SuperpowersSkills {
    $removed = 0
    foreach ($link in Get-SuperpowersJunctions) {
        if (Remove-SkillJunction -LinkPath $link.FullName) {
            $removed++
        }
    }
    Write-Host "Removed $removed superpowers skill junctions from $SkillsDst\"
}

function Install-SuperpowersSkills {
    if (-not (Test-Path -LiteralPath $SkillsSrc)) {
        throw "Skills source not found: $SkillsSrc`nClone first: git -c http.proxy= -c https.proxy= clone https://github.com/kumekay/cursorpowers.git .cursor/superpowers"
    }

    foreach ($link in Get-SuperpowersJunctions) {
        Remove-SkillJunction -LinkPath $link.FullName | Out-Null
    }

    $count = 0
    $installed = New-Object System.Collections.Generic.List[string]

    Get-ChildItem -LiteralPath $SkillsSrc -Directory | ForEach-Object {
        $skillName = $_.Name
        $skillFile = Join-Path $_.FullName 'SKILL.md'
        if (-not (Test-Path -LiteralPath $skillFile)) { return }

        $linkPath = Join-Path $SkillsDst ($Prefix + $skillName)
        if (Test-Path -LiteralPath $linkPath) {
            Remove-SkillJunction -LinkPath $linkPath | Out-Null
        }
        New-SkillJunction -LinkPath $linkPath -TargetPath $_.FullName
        $count++
        $installed.Add($Prefix + $skillName)
    }

    Write-Host "Installed $count superpowers skills to $SkillsDst\"
    Write-Host ''
    Write-Host 'Skills installed:'
    $installed | Sort-Object | ForEach-Object { Write-Host "  $_" }
}

if ($Uninstall) {
    Uninstall-SuperpowersSkills
}
else {
    Install-SuperpowersSkills
}
