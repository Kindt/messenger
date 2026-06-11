# Detect active image/repo/build loading on Korus QEMU guests (wait instead of redeploy/restart).
function Get-KorusQemuLoadingState {
    param(
        [string]$ServerBootstrapText = "",
        [string]$WebBootstrapText = "",
        [string]$Activity = "",
        [string[]]$BootstrapStates = @()
    )

    $text = (($ServerBootstrapText + "`n" + $WebBootstrapText) -replace '\x1b\[[0-9;?]*[ -/]*[@-~]', '')

    $pullPat = 'Downloading|Download complete|Extracting|Pull complete|Pulling fs layer|Pulling from|Waiting to download|Verifying Checksum'
    $gradlePat = 'gradle|Gradle|installDist|distTar|:runDist|:build|:docker'
    $dockerBuildPat = 'docker build|Building \[|Step [0-9]+/[0-9]+|exporting to image|writing image sha256'
    $repoPat = 'repo-updated|repo\.tgz|curl.*repo\.tgz|Packing repo snapshot'
    $ansiblePat = 'PLAY \[|TASK \[|run-ansible-local|ansible-playbook'
    $cachePat = 'korus-docker-image-load|docker-cache|Loading image|Loaded image'

    $kind = 'none'
    $detail = ''

    if ($BootstrapStates -contains 'docker-pull' -or $Activity -eq 'docker pull') {
        $kind = 'docker-pull'
        $detail = 'bootstrap state docker-pull'
    }
    elseif ($BootstrapStates -contains 'gradle' -or $Activity -match 'gradle|docker build') {
        $kind = 'gradle'
        $detail = 'bootstrap state gradle/build'
    }

    if ($kind -ne 'none' -and $text -match $pullPat) {
        $kind = 'docker-pull'
        $line = ($text -split "`n" | Where-Object { $_ -match $pullPat } | Select-Object -Last 1)
        if ($line) { $detail = ($line.Trim() -replace '\s+', ' ').Substring(0, [Math]::Min(120, $line.Trim().Length)) }
    }
    elseif ($kind -eq 'none' -and ($text -match $dockerBuildPat -or ($Activity -match 'docker build'))) {
        $kind = 'docker-build'
        $line = ($text -split "`n" | Where-Object { $_ -match $dockerBuildPat } | Select-Object -Last 1)
        if ($line) { $detail = ($line.Trim() -replace '\s+', ' ').Substring(0, [Math]::Min(120, $line.Trim().Length)) }
    }
    elseif ($kind -eq 'gradle' -and $text -match $gradlePat) {
        $line = ($text -split "`n" | Where-Object { $_ -match $gradlePat } | Select-Object -Last 1)
        if ($line) { $detail = ($line.Trim() -replace '\s+', ' ').Substring(0, [Math]::Min(120, $line.Trim().Length)) }
    }
    elseif ($kind -eq 'none' -and $text -match $gradlePat) {
        $kind = 'gradle'
        $line = ($text -split "`n" | Where-Object { $_ -match $gradlePat } | Select-Object -Last 1)
        if ($line) { $detail = ($line.Trim() -replace '\s+', ' ').Substring(0, [Math]::Min(120, $line.Trim().Length)) }
    }
    elseif ($kind -eq 'none' -and $text -match $cachePat) {
        $kind = 'image-cache-load'
        $detail = 'docker image cache load on guest'
    }
    elseif ($kind -eq 'none' -and $text -match $repoPat) {
        $kind = 'repo-sync'
        $detail = 'repo snapshot sync to guest'
    }
    elseif ($kind -eq 'none' -and $text -match $ansiblePat -and $text -notmatch 'PLAY RECAP') {
        $kind = 'ansible'
        $detail = 'ansible playbook running'
    }

    $loading = ($kind -ne 'none')
    if ($loading -and -not $detail) { $detail = $kind }

    return @{
        Loading = $loading
        Kind    = $kind
        Detail  = $detail
    }
}

function Test-KorusBootstrapNoiseError {
    param(
        [string]$BootstrapText,
        [hashtable]$LoadingState
    )
    if (-not $BootstrapText) { return $false }
    if ($LoadingState.Loading) { return $true }
    if ($BootstrapText -match 'curl: \(22\).*404' -and $BootstrapText -match '8080|health|api/v1') {
        return $true
    }
    return $false
}
