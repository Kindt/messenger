# Shared auth + project key resolution for lab report scripts.

function Get-SonarLabAuthHeaders {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $pair = "${SonarQemuAdminUser}:${SonarQemuAdminPassword}"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    return @{ Authorization = "Basic $b64" }
}

function Get-SonarLabProjectKey {
    param([string]$RepoRoot, [string]$ProjectKey)
    if ($ProjectKey) { return $ProjectKey }
    . (Join-Path $PSScriptRoot "ProjectProps.ps1")
    $root = Resolve-SonarLabRepoRoot -RepoRoot $RepoRoot
    $props = Get-SonarPropertiesMap -RepoRoot $root
    return (Get-SonarProjectIdentity -Props $props).Key
}

function Get-SonarLabProjectKeyEncoded {
    param([string]$RepoRoot, [string]$ProjectKey)
    $key = Get-SonarLabProjectKey -RepoRoot $RepoRoot -ProjectKey $ProjectKey
    return [uri]::EscapeDataString($key)
}
