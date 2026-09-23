param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Command,

    [Parameter(Position = 1, ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

$ErrorActionPreference = "Stop"

$sshHost = "edol-server"
$dockerHost = "127.0.0.1"
$dockerPort = 23750
$remoteDockerSocket = "/var/run/docker.sock"
$testcontainersHost = "192.168.0.200"

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    $client = [System.Net.Sockets.TcpClient]::new()

    try {
        $result = $client.BeginConnect($HostName, $Port, $null, $null)

        if (-not $result.AsyncWaitHandle.WaitOne(200)) {
            return $false
        }

        $client.EndConnect($result)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Resolve-SshExecutable {
    $sshOnPath = Get-Command "ssh.exe" -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty Source

    $candidates = @(
        $sshOnPath
        (Join-Path $env:WINDIR "System32\OpenSSH\ssh.exe")
        (Join-Path $env:ProgramFiles "Git\usr\bin\ssh.exe")
        (Join-Path ${env:ProgramFiles(x86)} "Git\usr\bin\ssh.exe")
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return $candidate
        }
    }

    return $null
}

if (Test-TcpPort $dockerHost $dockerPort) {
    Write-Error "Port ${dockerHost}:${dockerPort} is already in use. Refusing to start a Docker tunnel."
    exit 2
}

$sshExecutable = Resolve-SshExecutable

if ($null -eq $sshExecutable) {
    Write-Error "OpenSSH Client was not found. Install it or make ssh.exe available in PATH."
    exit 3
}

$sshArgs = @(
    "-N"
    "-T"
    "-o", "ExitOnForwardFailure=yes"
    "-o", "ServerAliveInterval=30"
    "-o", "ServerAliveCountMax=3"
    "-L", "${dockerHost}:${dockerPort}:${remoteDockerSocket}"
    $sshHost
)

$sshProcess = $null

try {
    Write-Host "Starting remote Docker tunnel..."
    Write-Host "Using SSH client: $sshExecutable"

    $sshProcess = Start-Process `
        -FilePath $sshExecutable `
        -ArgumentList $sshArgs `
        -WindowStyle Hidden `
        -PassThru

    $ready = $false

    for ($i = 0; $i -lt 50; $i++) {
        if ($sshProcess.HasExited) {
            throw "SSH tunnel exited unexpectedly with code $($sshProcess.ExitCode)."
        }

        if (Test-TcpPort $dockerHost $dockerPort) {
            $ready = $true
            break
        }

        Start-Sleep -Milliseconds 100
    }

    if (-not $ready) {
        throw "Docker tunnel did not become ready on ${dockerHost}:${dockerPort}."
    }

    $env:DOCKER_HOST = "tcp://${dockerHost}:${dockerPort}"
    $env:TESTCONTAINERS_HOST_OVERRIDE = $testcontainersHost

    Write-Host "Remote Docker ready: $env:DOCKER_HOST"
    Write-Host "Running: $Command $($CommandArgs -join ' ')"

    & $Command @CommandArgs

    $exitCode = $LASTEXITCODE

    if ($null -eq $exitCode) {
        $exitCode = if ($?) { 0 } else { 1 }
    }

    exit $exitCode
}
finally {
    if ($null -ne $sshProcess -and -not $sshProcess.HasExited) {
        Write-Host "Stopping remote Docker tunnel..."
        Stop-Process -Id $sshProcess.Id -Force
        $sshProcess.WaitForExit()
    }
}
