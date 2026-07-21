# Shared DELETE cancel with 409 race handling (job finished before cancel).
function Invoke-ExportJobCancel {
    param(
        [Parameter(Mandatory)][string]$CancelUri,
        [Parameter(Mandatory)][hashtable]$Headers,
        [Parameter(Mandatory)][string]$StatusUri,
        [switch]$AllowFinishedEarly
    )
    try {
        Invoke-RestMethod -Uri $CancelUri -Headers $Headers -Method Delete | Out-Null
        return @{ Ok = $true; FinishedEarly = $false }
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        if ($code -ne 409) { throw }

        $st = Invoke-RestMethod -Uri $StatusUri -Headers $Headers -Method Get
        if ($st.status -eq "export_cancelled") {
            return @{ Ok = $true; FinishedEarly = $false }
        }
        if ($st.status -in @("export_v1", "stub_written")) {
            return @{ Ok = [bool]$AllowFinishedEarly; FinishedEarly = $true; Status = $st.status }
        }
        throw
    }
}
