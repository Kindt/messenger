# DEPRECATED alias (kept for backward compatibility).
# Canonical path: smoke-export-compliance-flow.ps1 -IncludeFile.
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [int]$PollSeconds = 120,
    [switch]$SkipPrep
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$flowArgs = @{
    BaseUrl      = $BaseUrl
    PollSeconds  = $PollSeconds
    IncludeFile  = $true
}
if ($ChatId) { $flowArgs["ChatId"] = $ChatId }
if ($SkipPrep) { $flowArgs["SkipPrep"] = $true }

& "$scriptDir\smoke-export-compliance-flow.ps1" @flowArgs
