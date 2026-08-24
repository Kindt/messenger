function Write-KorusMobilePipelineStatus {
    param(
        [Parameter(Mandatory)][string]$Phase,
        [Parameter(Mandatory)][string]$Role,
        [string]$PlanReviewStatus = "",
        [string]$UxReviewStatus = "",
        [string]$QaStatus = "",
        [string]$Detail = "",
        [string]$RunDir = ""
    )
    if (-not $RunDir) {
        $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $RunDir = Join-Path $Root "deploy\mobile\run"
    }
    if (-not (Test-Path $RunDir)) {
        New-Item -ItemType Directory -Path $RunDir -Force | Out-Null
    }
    $path = Join-Path $RunDir "pipeline-status.json"
    $existing = @{}
    if (Test-Path $path) {
        try {
            $existing = Get-Content $path -Raw | ConvertFrom-Json -AsHashtable
        } catch {
            $existing = @{}
        }
    }
    $obj = [ordered]@{
        updated_at       = (Get-Date).ToUniversalTime().ToString("o")
        updated_at_local   = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        phase              = $Phase
        role               = $Role
        detail             = $Detail
        pid                = $PID
    }
    if ($PlanReviewStatus) {
        $obj.plan_review = [ordered]@{
            wave      = $Phase
            status    = $PlanReviewStatus
            reviewed_at = (Get-Date).ToUniversalTime().ToString("o")
        }
    } elseif ($existing.plan_review) {
        $obj.plan_review = $existing.plan_review
    }
    if ($UxReviewStatus) {
        $obj.ux_review = [ordered]@{
            wave   = $Phase
            status = $UxReviewStatus
            at     = (Get-Date).ToUniversalTime().ToString("o")
        }
    } elseif ($existing.ux_review) {
        $obj.ux_review = $existing.ux_review
    }
    if ($QaStatus) {
        $obj.qa = [ordered]@{
            wave   = $Phase
            status = $QaStatus
            at     = (Get-Date).ToUniversalTime().ToString("o")
        }
    } elseif ($existing.qa) {
        $obj.qa = $existing.qa
    }
    ($obj | ConvertTo-Json -Depth 6) | Set-Content -Path $path -Encoding UTF8
    return $path
}
