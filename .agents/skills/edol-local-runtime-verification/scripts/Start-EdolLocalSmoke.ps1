[CmdletBinding(DefaultParameterSetName = 'Run')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Run')]
    [ValidateSet('Core', 'Hub', 'Ams', 'Notify')]
    [string]$Module,

    [Parameter(ParameterSetName = 'Run')]
    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 90,

    [Parameter(ParameterSetName = 'Run')]
    [switch]$WaitForTelemetry,

    [Parameter(ParameterSetName = 'Run')]
    [ValidateRange(1, 300)]
    [int]$TelemetryTimeoutSeconds = 120,

    [Parameter(Mandatory, ParameterSetName = 'Static')]
    [switch]$StaticCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Write-Status {
    param([Parameter(Mandatory)][string]$Message)
    Write-Host $Message
}

function Read-DotEnvFile {
    param([Parameter(Mandatory)][string]$Path)

    $values = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($Path)) {
        $lineNumber++
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }

        $match = [regex]::Match($line, '^\s*(?<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?<value>.*)$')
        if (-not $match.Success) {
            throw "INFRASTRUCTURE_FAILURE=MALFORMED_SECRET_FILE ($([System.IO.Path]::GetFileName($Path)), line $lineNumber)"
        }

        $value = $match.Groups['value'].Value.Trim()
        if ($value.IndexOf([char]0) -ge 0) {
            throw "INFRASTRUCTURE_FAILURE=INVALID_SECRET_FILE_ENTRY ($([System.IO.Path]::GetFileName($Path)), line $lineNumber)"
        }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$match.Groups['key'].Value] = $value
    }
    return $values
}

function Test-ListeningPort {
    param([Parameter(Mandatory)][int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Get-ListeningProcess {
    param([Parameter(Mandatory)][int]$Port)

    $listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $listener) {
        return $null
    }
    return Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
}

function Get-ModuleDefinition {
    param([Parameter(Mandatory)][string]$Name)

    $definitions = @{
        Core = @{
            Directory = 'edol-core'; SecretFile = '.env_edol_core_secret'; PortFromEnvironment = 'SERVER_PORT'
            RequiredEnvironment = @('SERVER_PORT', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'WEB_ADMIN_NAME', 'WEB_ADMIN_PASSWORD')
            Web = $true; LocalDatabase = $true; ReadinessPath = '/api/printers/state'
        }
        Hub = @{
            Directory = 'edol-hub'; SecretFile = '.env_edol_hub_secret'; Port = 8090
            RequiredEnvironment = @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'QR_URL')
            Web = $true; LocalDatabase = $true; ReadinessPath = '/'; ReadOnlyPath = '/'
        }
        Ams = @{
            Directory = 'edol-ams'; SecretFile = '.env_edol_ams_secret'; Port = 8099
            RequiredEnvironment = @(); Web = $true; LocalDatabase = $false
        }
        Notify = @{
            Directory = 'edol-notify'; SecretFile = '.env_edol_notify_secret'
            RequiredEnvironment = @('TELEGRAM_BOT_TOKEN', 'TELEGRAM_ADMIN_ID'); Web = $false; LocalDatabase = $false
        }
    }
    return $definitions[$Name]
}

function Assert-StaticLayout {
    param([Parameter(Mandatory)][string]$RepositoryRoot)

    foreach ($name in @('Core', 'Hub', 'Ams', 'Notify')) {
        $definition = Get-ModuleDefinition -Name $name
        $devConfig = Join-Path $RepositoryRoot (Join-Path $definition.Directory 'src/main/resources/application-dev.yaml')
        if (-not (Test-Path -LiteralPath $devConfig -PathType Leaf)) {
            throw "INFRASTRUCTURE_FAILURE=LOCAL_DEV_CONFIG_MISSING ($name)"
        }
        if ($definition.LocalDatabase) {
            $content = [System.IO.File]::ReadAllText($devConfig)
            if ($content -notmatch 'jdbc:postgresql://localhost:5433/') {
                throw "INFRASTRUCTURE_FAILURE=LOCAL_DATABASE_CONFIGURATION_UNVERIFIED ($name)"
            }
        }
    }
}

function Stop-WorkflowProcessTree {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return
    }
    $processId = $Process.Id
    try {
        if (-not $Process.HasExited) {
            & $env:ComSpec /d /c "taskkill /PID $processId /T /F" *> $null
        }
    } catch {
        if ($null -ne (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
            Write-Status 'CLEANUP=PROCESS_TREE_STOP_FAILED'
        }
    }
}

function Stop-WorkflowServiceProcess {
    param([System.Diagnostics.Process]$Process)

    if ($null -eq $Process) {
        return
    }
    try {
        if (-not $Process.HasExited) {
            Stop-Process -InputObject $Process -Force
            $Process.WaitForExit()
        }
    } catch {
        Write-Status 'CLEANUP=SERVICE_PROCESS_STOP_FAILED'
    }
}

function Wait-ForPortRelease {
    param([int]$Port)

    if ($Port -le 0) {
        return $true
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (-not (Test-ListeningPort -Port $Port)) {
            return $true
        }
        Start-Sleep -Milliseconds 250
    }
    return $false
}

function Test-ReadOnlyHttpEndpoint {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Path
    )

    $client = [System.Net.Http.HttpClient]::new()
    try {
        $client.Timeout = [TimeSpan]::FromSeconds(10)
        $response = $client.GetAsync("http://127.0.0.1:$Port$Path").GetAwaiter().GetResult()
        try {
            return $response.IsSuccessStatusCode
        } finally {
            $response.Dispose()
        }
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Invoke-ReadOnlyHttpCheck {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][string]$Path
    )

    if (-not (Test-ReadOnlyHttpEndpoint -Port $Port -Path $Path)) {
        throw 'APPLICATION_STARTUP_FAILURE=READ_ONLY_HTTP_CHECK_UNAVAILABLE'
    }
}

function Test-CoreTelemetryReceived {
    param([Parameter(Mandatory)][int]$Port)

    $client = [System.Net.Http.HttpClient]::new()
    try {
        $client.Timeout = [TimeSpan]::FromSeconds(10)
        $response = $client.GetAsync("http://127.0.0.1:$Port/api/printers/state").GetAwaiter().GetResult()
        try {
            if (-not $response.IsSuccessStatusCode) {
                return $false
            }
            $states = @($response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json)
            foreach ($state in $states) {
                if ($state.online -ne $true) {
                    continue
                }
                foreach ($property in @('gcodeState', 'nozzleTemp', 'bedTemp', 'wifiSignal', 'currentFile', 'currentTask')) {
                    $value = $state.$property
                    if ($value -is [string] -and -not [string]::IsNullOrWhiteSpace($value)) {
                        return $true
                    }
                    if ($value -is [ValueType] -and $value -ne 0) {
                        return $true
                    }
                }
            }
            return $false
        } finally {
            $response.Dispose()
        }
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForCoreTelemetry {
    param(
        [Parameter(Mandatory)][int]$Port,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-CoreTelemetryReceived -Port $Port) {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

if ($StaticCheck) {
    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
    Assert-StaticLayout -RepositoryRoot $repositoryRoot
    Write-Status 'STATIC_LAYOUT=PASS'
    Write-Status 'ENV_PARSER=KEY_VALUE_ONLY'
    Write-Status 'CHILD_ENVIRONMENT=ISOLATED'
    Write-Status 'CLEANUP=WORKFLOW_PROCESS_TREE_ONLY'
    exit 0
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$definition = Get-ModuleDefinition -Name $Module
$process = $null
$temporaryDirectory = $null
$port = 0
$workflowStarted = $false
$serviceProcess = $null

try {
    Assert-StaticLayout -RepositoryRoot $repositoryRoot

    $commonSecretFile = Join-Path $repositoryRoot '.env_secret'
    $moduleSecretFile = Join-Path $repositoryRoot $definition.SecretFile
    foreach ($secretFile in @($commonSecretFile, $moduleSecretFile)) {
        if (-not (Test-Path -LiteralPath $secretFile -PathType Leaf)) {
            throw "INFRASTRUCTURE_FAILURE=REQUIRED_SECRET_FILE_MISSING ($([System.IO.Path]::GetFileName($secretFile)))"
        }
    }

    $environment = Read-DotEnvFile -Path $commonSecretFile
    foreach ($entry in (Read-DotEnvFile -Path $moduleSecretFile).GetEnumerator()) {
        $environment[$entry.Key] = $entry.Value
    }
    foreach ($requiredName in $definition.RequiredEnvironment) {
        $isSet = $environment.ContainsKey($requiredName) -and -not [string]::IsNullOrWhiteSpace($environment[$requiredName])
        Write-Status "$requiredName=$(if ($isSet) { 'SET' } else { 'MISSING' })"
        if (-not $isSet) {
            throw "INFRASTRUCTURE_FAILURE=REQUIRED_ENVIRONMENT_MISSING ($requiredName)"
        }
    }

    if ($definition.ContainsKey('PortFromEnvironment')) {
        if (-not [int]::TryParse($environment[$definition.PortFromEnvironment], [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
            throw "INFRASTRUCTURE_FAILURE=INVALID_EXPECTED_PORT ($($definition.PortFromEnvironment))"
        }
    } elseif ($definition.ContainsKey('Port')) {
        $port = [int]$definition.Port
    }

    if ($port -gt 0 -and (Test-ListeningPort -Port $port)) {
        throw "INFRASTRUCTURE_FAILURE=EXPECTED_PORT_ALREADY_IN_USE ($port)"
    }

    $maven = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($null -eq $maven) {
        $maven = Get-Command 'mvn' -ErrorAction SilentlyContinue
    }
    if ($null -eq $maven) {
        throw 'INFRASTRUCTURE_FAILURE=MAVEN_NOT_FOUND'
    }

    $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("edol-local-smoke-" + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory($temporaryDirectory) | Out-Null

    $applicationLog = Join-Path $temporaryDirectory 'application.log'
    $applicationArguments = @(
        '--spring.profiles.active=dev',
        '--logging.file.name=' + $applicationLog
    )
    $argumentsValue = [string]::Join(' ', $applicationArguments)
    $mavenInvocation = '"{0}" spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments={1}"' -f $maven.Source, $argumentsValue

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $env:ComSpec
    $startInfo.Arguments = '/d /s /c "' + $mavenInvocation + '"'
    $startInfo.WorkingDirectory = Join-Path $repositoryRoot $definition.Directory
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    foreach ($entry in $environment.GetEnumerator()) {
        $startInfo.EnvironmentVariables[$entry.Key] = $entry.Value
    }
    $startInfo.EnvironmentVariables['SPRING_PROFILES_ACTIVE'] = 'dev'
    $existingJavaToolOptions = $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS']
    $temporaryLogJvmOptions = '-DLOG_FILE="{0}" -Dlogging.file.name="{0}"' -f $applicationLog
    if ([string]::IsNullOrWhiteSpace($existingJavaToolOptions)) {
        $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = $temporaryLogJvmOptions
    } else {
        $startInfo.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = "$existingJavaToolOptions $temporaryLogJvmOptions"
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw 'APPLICATION_STARTUP_FAILURE=PROCESS_DID_NOT_START'
    }
    $workflowStarted = $true
    $startupMarker = $null
    if ($definition.Web) {
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
    } else {
        $startupMarker = [System.Threading.ManualResetEventSlim]::new($false)
        $outputHandler = [System.Diagnostics.DataReceivedEventHandler] ({
            param($sender, $eventArgs)
            if ($null -ne $eventArgs.Data -and $eventArgs.Data -match 'Started .+ in .+') {
                $startupMarker.Set()
            }
        }.GetNewClosure())
        $errorHandler = [System.Diagnostics.DataReceivedEventHandler] ({
            param($sender, $eventArgs)
            if ($null -ne $eventArgs.Data -and $eventArgs.Data -match 'Started .+ in .+') {
                $startupMarker.Set()
            }
        }.GetNewClosure())
        $process.add_OutputDataReceived($outputHandler)
        $process.add_ErrorDataReceived($errorHandler)
        $process.BeginOutputReadLine()
        $process.BeginErrorReadLine()
    }

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) {
            break
        }
        $listenerReady = -not $definition.Web -or (Test-ListeningPort -Port $port)
        $endpointReady = if ($definition.Web) {
            $definition.ContainsKey('ReadinessPath') -and
                (Test-ReadOnlyHttpEndpoint -Port $port -Path $definition.ReadinessPath)
        } else {
            $startupMarker.IsSet
        }
        if ($listenerReady -and $endpointReady) {
            if ($definition.Web) {
                $serviceProcess = Get-ListeningProcess -Port $port
                if ($null -eq $serviceProcess) {
                    throw 'APPLICATION_STARTUP_FAILURE=LISTENER_PROCESS_NOT_FOUND'
                }
            }
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }

    if (-not $ready) {
        if ($process.HasExited) {
            Write-Status "APPLICATION_STARTUP_FAILURE=PROCESS_EXITED_BEFORE_READINESS (exit=$($process.ExitCode))"
            throw 'APPLICATION_STARTUP_FAILURE=PROCESS_EXITED_BEFORE_READINESS'
        }
        throw 'APPLICATION_STARTUP_FAILURE=STARTUP_TIMEOUT'
    }
    Write-Status "READINESS=PASS ($Module)"
    if ($definition.ContainsKey('ReadOnlyPath')) {
        Invoke-ReadOnlyHttpCheck -Port $port -Path $definition.ReadOnlyPath
        Write-Status "READ_ONLY_HTTP_CHECK=PASS ($($definition.ReadOnlyPath))"
    }
    if ($WaitForTelemetry) {
        if ($Module -ne 'Core') {
            throw 'INFRASTRUCTURE_FAILURE=TELEMETRY_CHECK_REQUIRES_CORE'
        }
        if (-not (Wait-ForCoreTelemetry -Port $port -TimeoutSeconds $TelemetryTimeoutSeconds)) {
            throw 'APPLICATION_RUNTIME_FAILURE=TELEMETRY_TIMEOUT'
        }
        Write-Status 'TELEMETRY=RECEIVED'
    }
    Write-Status 'SMOKE_RESULT=PASS'
} finally {
    Stop-WorkflowServiceProcess -Process $serviceProcess
    Stop-WorkflowProcessTree -Process $process
    if ($workflowStarted -and $port -gt 0) {
        if (Wait-ForPortRelease -Port $port) {
            Write-Status "PORT_RELEASED=YES ($port)"
        } else {
            Write-Status "PORT_RELEASED=NO ($port)"
        }
    }
    if ($null -ne $temporaryDirectory -and (Test-Path -LiteralPath $temporaryDirectory)) {
        try {
            Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
        } catch {
            Write-Status 'CLEANUP=TEMPORARY_DIRECTORY_REMOVE_FAILED'
        }
    }
}
