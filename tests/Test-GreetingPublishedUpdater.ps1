param(
    [Parameter(Mandatory=$true)][string]$SnapshotDatabase,
    [Parameter(Mandatory=$true)][string]$PublishedDirectory,
    [switch]$EnableLanAutoPairing
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('greeting-updater-test-' + [Guid]::NewGuid().ToString('N'))
$state = Join-Path $testRoot 'state'
$database = Join-Path $testRoot 'cloud_fleet.db'
$scriptDirectory = Join-Path $testRoot 'scripts'
New-Item -ItemType Directory -Path $scriptDirectory -Force | Out-Null
Copy-Item -LiteralPath (Resolve-Path -LiteralPath $SnapshotDatabase).Path -Destination $database
$updater = Join-Path $scriptDirectory 'Update-GreetingServer.ps1'
Copy-Item -LiteralPath (Join-Path $repo 'scripts\Update-GreetingServer.ps1') -Destination $updater
$published = (Resolve-Path -LiteralPath $PublishedDirectory).Path
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start(); $port = $listener.LocalEndpoint.Port; $listener.Stop()
$url = "http://127.0.0.1:$port"
$worker = $null
$checks = 0
function Check([bool]$value, [string]$message) {
    if (-not $value) { throw $message }
    $script:checks++
}
function Start-TestServer {
    $args = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $updater + '"'),
        '-DatabasePath', ('"' + $database + '"'), '-PublishedDirectory', ('"' + $published + '"'),
        '-StateDirectory', ('"' + $state + '"'), '-ExpectedHostIp', '127.0.0.1', '-Port', $port)
    if ($EnableLanAutoPairing) { $args += '-EnableLanAutoPairing' }
    $oldModulePath = $env:PSModulePath
    try {
        $env:PSModulePath = (Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\Modules') + ';' + (Join-Path $env:ProgramFiles 'WindowsPowerShell\Modules')
        $script:worker = Start-Process powershell.exe -ArgumentList $args -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $testRoot 'updater.stdout.log') -RedirectStandardError (Join-Path $testRoot 'updater.stderr.log')
    } finally { $env:PSModulePath = $oldModulePath }
    for ($i=0; $i -lt 40; $i++) {
        if ($worker.HasExited) { throw 'Portable updater exited before starting the test server.' }
        try { $null = Invoke-RestMethod "$url/api/fleet/summary" -TimeoutSec 1; return } catch {}
        Start-Sleep -Milliseconds 250
    }
    throw 'Portable updater test server did not start.'
}
function Stop-TestServer {
    foreach ($serverPid in @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$serverPid"
        if (-not $process -or -not $process.CommandLine.Contains($state) -or -not $process.CommandLine.Contains('LeHoangCloudServer.dll')) {
            throw 'Refusing to stop an unrelated process on the test port.'
        }
        Stop-Process -Id $serverPid -ErrorAction Stop
        Wait-Process -Id $serverPid -Timeout 10 -ErrorAction SilentlyContinue
    }
    if ($worker) { $null = $worker.WaitForExit(3000) }
}
try {
    Start-TestServer
    $page = Invoke-WebRequest "$url/" -UseBasicParsing
    Check ($page.Content.Contains('voiceAngleOffset')) 'Published Web lacks the compensation field.'
    Check ($page.Content.Contains('greetingSettings')) 'Published Web lacks the Greetingbot tab.'
    $access = Invoke-RestMethod "$url/api/greeting/access"
    Check (($access.managementMode -eq 'lan') -eq [bool]$EnableLanAutoPairing) 'Updater LAN access mode was not applied.'
    Check (-not (Test-Path -LiteralPath (Join-Path $testRoot 'LeHoangCloudServer\LeHoangCloudServer.csproj'))) 'Test must not contain source/SDK build prerequisites.'
    $keyFile = Join-Path $state 'admin-key.dpapi'
    Check (Test-Path -LiteralPath $keyFile) 'Admin key was not protected with DPAPI.'
    $secure = (Get-Content -LiteralPath $keyFile -Raw).Trim() | ConvertTo-SecureString
    $credential = [Net.NetworkCredential]::new('', $secure)
    $headers = @{'X-Greeting-Admin-Key'=$credential.Password}
    $registered = Invoke-RestMethod "$url/api/greeting/robots" -Method Post -Headers $headers -ContentType 'application/json' `
        -Body '{"id":"GREETING-DEPLOY-TEST","name":"Deployment test"}'
    Check ($registered.robot.settings.voiceAngleOffset -eq 25) 'Default directional compensation is not 25 degrees.'
    $settings = $registered.robot.settings; $settings.voiceAngleOffset = 37
    $null = Invoke-RestMethod "$url/api/greeting/robots/GREETING-DEPLOY-TEST/settings" -Method Put -Headers $headers `
        -ContentType 'application/json' -Body ($settings | ConvertTo-Json)
    Stop-TestServer
    Start-TestServer
    $restored = Invoke-RestMethod "$url/api/greeting/robots/GREETING-DEPLOY-TEST" -Headers $headers
    Check ($restored.settings.voiceAngleOffset -eq 37) 'Web compensation did not survive restart.'
    Check (@(Get-ChildItem -LiteralPath $state -Directory -Filter 'backup-*').Count -ge 2) 'Database backups missing.'
    Stop-TestServer
    Check ((Get-FileHash -LiteralPath $SnapshotDatabase).Hash -ne (Get-FileHash -LiteralPath $database).Hash) 'Test must exercise a separate database copy.'
    Write-Output ("PASS: $checks portable updater checks; temporary test data at $testRoot")
} finally {
    Stop-TestServer
    $headers=$null; $credential=$null; $secure=$null; $registered=$null
}
