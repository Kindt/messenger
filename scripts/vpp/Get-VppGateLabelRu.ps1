#Requires -Version 5.1
# Russian display names for VPP gates (spec 030). Labels loaded from UTF-8 JSON.

function Get-VppGateLabelRu {
    param([Parameter(Mandatory)][string]$GateId)

    if (-not $script:VppGateLabelsRu) {
        $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $path = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-gate-labels-ru.json"
        $script:VppGateLabelsRu = @{}
        if (Test-Path $path) {
            $j = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
            foreach ($p in $j.PSObject.Properties) { $script:VppGateLabelsRu[$p.Name] = [string]$p.Value }
        }
    }
    if ($script:VppGateLabelsRu.ContainsKey($GateId)) {
        return $script:VppGateLabelsRu[$GateId]
    }
    return ($GateId -replace '_', ' ')
}

function Get-VppChatStringsRu {
    if (-not $script:VppChatStringsRu) {
        $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $path = Join-Path $Root "specs\030-vpp-product-verification\contracts\vpp-chat-strings-ru.json"
        if (Test-Path $path) {
            $script:VppChatStringsRu = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
        } else {
            $script:VppChatStringsRu = @{}
        }
    }
    return $script:VppChatStringsRu
}

function Format-VppElapsedRu {
    param([double]$Minutes)
    $s = Get-VppChatStringsRu
    $h = [math]::Floor($Minutes / 60)
    $m = [math]::Round($Minutes % 60)
    if ($h -gt 0) { return [string]::Format([string]$s.time_h_min, $h, $m) }
    return [string]::Format([string]$s.time_min, $m)
}

function Get-VppStatusRu {
    param([string]$Status)
    $s = Get-VppChatStringsRu
    switch ($Status) {
        "GREEN" { return [string]$s.status_green }
        "STALLED" { return [string]$s.status_stalled }
        "FAILED" { return [string]$s.status_failed }
        "RUNNING" { return [string]$s.status_running }
        default { return [string]$s.status_starting }
    }
}
