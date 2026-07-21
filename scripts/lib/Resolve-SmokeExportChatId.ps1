function Resolve-SmokeExportChatId {
    param(
        [string]$ChatId = "",
        [string]$BaseUrl = "http://localhost:8080",
        [string]$ScriptDir = ""
    )
    if ($ChatId) { return $ChatId }
    if (-not $ScriptDir) {
        $ScriptDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $ScriptDir = Join-Path $ScriptDir "scripts"
    }
    $prepOut = & (Join-Path $ScriptDir "smoke-admin-export-compliance-prep.ps1") -BaseUrl $BaseUrl
    $line = @($prepOut | Where-Object { $_ -match '^CHAT_ID=' } | Select-Object -Last 1)
    if ($line) { $ChatId = ($line -replace '^CHAT_ID=', '').Trim() }
    if (-not $ChatId) { throw "Could not resolve ChatId (pass -ChatId or run compliance prep)" }
    return $ChatId
}
