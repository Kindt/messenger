# Alias: compliance flow with include_file prep (see smoke-export-compliance-flow.ps1).
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
