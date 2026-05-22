# One-shot: seed chat + retention policy + wait for pass + verify export.suggested audit.
param(
    [string]$ChatId = "",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$RetentionMetricsUrl = "http://localhost:9192/metrics",
    [switch]$CreateGroup
)
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$args = @{
    BaseUrl             = $BaseUrl
    RetentionMetricsUrl = $RetentionMetricsUrl
    Seed                = $true
    Prepare             = $true
}
if ($ChatId) { $args["ChatId"] = $ChatId } elseif ($CreateGroup) { $args["CreateGroup"] = $true }
else { $args["CreateGroup"] = $true }
& "$scriptDir\smoke-retention-export-suggested.ps1" @args
