# Detect active image/repo/build loading on Korus QEMU guests (wait instead of redeploy/restart).

function Get-KorusGuestActiveBuildProcesses {

    param(

        [string]$ServerHostKey = "",

        [string]$WebHostKey = ""

    )



    $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"

    if (-not (Test-Path $Plink)) { return @() }



    $procPat = 'gradle|Gradle|installDist|distTar|:runDist|:build|ansible-playbook|run-ansible-local|docker pull|docker build|docker-compose.*build|compose build'

    $found = New-Object System.Collections.Generic.List[string]



    foreach ($entry in @(

        @{ Role = "server"; Port = 12221; Key = $ServerHostKey },

        @{ Role = "web"; Port = 12222; Key = $WebHostKey }

    )) {

        if (-not $entry.Key) { continue }

        $cmd = "pgrep -af '$procPat' 2>/dev/null | head -5 || true"

        try {

            $out = & $Plink -batch -hostkey $entry.Key -pw korus -P $entry.Port "korus@127.0.0.1" $cmd 2>$null

            foreach ($line in @($out)) {

                $t = ("$line").Trim()

                if ($t -and $t -notmatch 'pgrep -af') {

                    $found.Add("$($entry.Role):$t") | Out-Null

                }

            }

        } catch {}

    }



    return @($found)

}



function Get-KorusQemuLoadingState {

    param(

        [string]$ServerBootstrapText = "",

        [string]$WebBootstrapText = "",

        [string]$Activity = "",

        [string[]]$BootstrapStates = @(),

        [string]$ServerHostKey = "",

        [string]$WebHostKey = ""

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

    if ($loading -and ($ServerHostKey -or $WebHostKey)) {

        $active = @(Get-KorusGuestActiveBuildProcesses -ServerHostKey $ServerHostKey -WebHostKey $WebHostKey)

        if ($active.Count -eq 0) {

            $loading = $false

            $kind = 'none'

            $detail = 'stale bootstrap log (no guest build processes)'

        } elseif (-not $detail -or $detail -match 'bootstrap state') {

            $detail = ($active | Select-Object -First 1).Substring(0, [Math]::Min(120, ($active | Select-Object -First 1).Length))

        }

    }



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

    if ($BootstrapText -match 'curl: \(22\).*404') {
        if ($BootstrapText -match 'korus-docker-image-load|docker-base-images|18890|:18890') { return $true }
        if ($BootstrapText -match '8080|health|api/v1') { return $true }
    }

    return $false

}

