[CmdletBinding()]
param(
    [string]$DatabasePath,
    [ValidateRange(1024, 65535)][int]$Port = 5050,
    [switch]$ShowAdminKey,
    [string]$OpenAiKeyFile,
    [string]$SpeechTokenFile,
    [string]$PublishedDirectory,
    [string]$ExpectedHostIp,
    [string]$StateDirectory = (Join-Path $env:LOCALAPPDATA 'LeHoangRobotics\Greetingbot')
)
$ErrorActionPreference = 'Stop'
# Use this host's security module even when launched from a different PowerShell edition.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
if ($ExpectedHostIp) {
    if (-not (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object IPAddress -eq $ExpectedHostIp)) {
        throw "Run this updater on server $ExpectedHostIp, using its local disk path. No server was stopped."
    }
    if ($DatabasePath -and $DatabasePath.StartsWith('\\')) {
        throw 'Use the database local disk path on the server, not a network share path.'
    }
}
$stateDirectory = [IO.Path]::GetFullPath($StateDirectory)
$keyFile = Join-Path $stateDirectory 'admin-key.dpapi'

function Read-AdminKey {
    $secure = (Get-Content -LiteralPath $keyFile -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
if ($ShowAdminKey) {
    if (!(Test-Path -LiteralPath $keyFile)) { throw 'Chua co ma quan tri. Chay cap nhat server truoc.' }
    Write-Output (Read-AdminKey)
    return
}
if ([string]::IsNullOrWhiteSpace($DatabasePath) -or !(Test-Path -LiteralPath $DatabasePath -PathType Leaf)) {
    throw 'Truyen -DatabasePath duong dan day du toi cloud_fleet.db HIEN DANG DUNG. Script khong tao DB thay the.'
}
if ($OpenAiKeyFile -and !(Test-Path -LiteralPath $OpenAiKeyFile -PathType Leaf)) { throw 'Khong tim thay file khoa OpenAI.' }
if ($SpeechTokenFile -and !(Test-Path -LiteralPath $SpeechTokenFile -PathType Leaf)) { throw 'Khong tim thay file DPAPI ma ket noi giong noi.' }
$preparedSpeechToken = $null
if ($SpeechTokenFile) {
    # Decrypt and validate before publishing/stopping the current server.
    $speechSecure = (Get-Content -LiteralPath $SpeechTokenFile -Raw).Trim() | ConvertTo-SecureString
    $preparedSpeechToken = [Net.NetworkCredential]::new('', $speechSecure).Password
    if ($preparedSpeechToken -notmatch '^[A-Za-z0-9_-]{32,128}$') { throw 'Ma ket noi giong noi khong hop le.' }
}
$dbFile = (Resolve-Path -LiteralPath $DatabasePath).ProviderPath
$repository = Split-Path -Parent $PSScriptRoot
$project = Join-Path $repository 'LeHoangCloudServer\LeHoangCloudServer.csproj'
if ($PublishedDirectory) {
    $published = (Resolve-Path -LiteralPath $PublishedDirectory).ProviderPath
    foreach ($required in @('LeHoangCloudServer.dll', 'LeHoangCloudServer.runtimeconfig.json', 'wwwroot\index.html', 'wwwroot\js\greeting.js')) {
        if (-not (Test-Path -LiteralPath (Join-Path $published $required) -PathType Leaf)) { throw "Incomplete published package: $required" }
    }
} elseif (!(Test-Path -LiteralPath $project)) { throw 'Can chay script tu checkout LEHOANGROBOTICS.' }
$releaseDirectory = Join-Path $stateDirectory ('server-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$compileDirectory = Join-Path $stateDirectory ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null

# Prepare the complete version before stopping the existing server.
if ($PublishedDirectory) {
    $sourcePrefix = [IO.Path]::GetFullPath($published).TrimEnd('\') + '\'
    if ([string]::Equals([IO.Path]::GetFullPath($releaseDirectory), $sourcePrefix.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFullPath($releaseDirectory).StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The version directory must not be inside the package being copied.'
    }
    Get-ChildItem -LiteralPath $published -Force | Copy-Item -Destination $releaseDirectory -Recurse -Force
} else {
    & dotnet publish $project -c Release --no-self-contained "-p:OutputPath=$compileDirectory" -o $releaseDirectory --nologo
    if ($LASTEXITCODE -ne 0) { throw 'Publish that bai. Server hien tai chua bi dung.' }
}
# Validate runtime and protected credentials before taking the running server down.
$null = Get-Command dotnet -CommandType Application -ErrorAction Stop
if (!(Test-Path -LiteralPath $keyFile)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $newKey = [Convert]::ToBase64String($bytes)
    $newKey | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $keyFile
    $newKey = $null
}
$preparedAdminKey = Read-AdminKey
if ($preparedAdminKey.Length -lt 24) { throw 'Protected admin key is invalid; existing server was not stopped.' }
$listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
foreach ($serverProcessId in @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$serverProcessId"
    if (!$process -or $process.CommandLine -notmatch 'LeHoangCloudServer') {
        throw "Cong $Port dang duoc tien trinh khac su dung. Khong dung tien trinh khong xac dinh."
    }
    $service = Get-CimInstance Win32_Service -Filter "ProcessId=$serverProcessId" | Select-Object -First 1
    if ($service) { throw "Server dang chay bang Windows Service '$($service.Name)'. Dung dich vu nay va chay lai script." }
    Stop-Process -Id $serverProcessId -ErrorAction Stop
    Wait-Process -Id $serverProcessId -Timeout 15 -ErrorAction SilentlyContinue
}

$backup = Join-Path $stateDirectory ('backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null
foreach ($file in @($dbFile, ($dbFile + '-wal'), ($dbFile + '-shm'))) {
    if (Test-Path -LiteralPath $file) { Copy-Item -LiteralPath $file -Destination $backup }
}
$env:GREETING_ADMIN_KEY = $preparedAdminKey
$env:DATABASE_PATH = $dbFile
$env:ASPNETCORE_URLS = "http://0.0.0.0:$Port"
$env:ASPNETCORE_ENVIRONMENT = 'Production'
$previousOpenAiFile = [Environment]::GetEnvironmentVariable('OPENAI_API_KEY_FILE', 'Process')
$previousSpeechToken = [Environment]::GetEnvironmentVariable('GREETING_SPEECH_TOKEN', 'Process')
if ($OpenAiKeyFile) { $env:OPENAI_API_KEY_FILE = (Resolve-Path -LiteralPath $OpenAiKeyFile).ProviderPath }
if ($SpeechTokenFile) {
    $env:GREETING_SPEECH_TOKEN = $preparedSpeechToken
}
Write-Host "DB giu nguyen: $dbFile"
Write-Host "Ban sao luu: $backup"
Write-Host "Web: http://localhost:$Port -> tab GREETINGBOT"
Write-Host 'Xem ma quan tri trong cua so PowerShell khac: .\scripts\Update-GreetingServer.ps1 -ShowAdminKey'
Push-Location $releaseDirectory
try { & dotnet (Join-Path $releaseDirectory 'LeHoangCloudServer.dll') }
finally {
    Pop-Location
    Remove-Item Env:GREETING_ADMIN_KEY -ErrorAction SilentlyContinue
    if ($OpenAiKeyFile) { [Environment]::SetEnvironmentVariable('OPENAI_API_KEY_FILE', $previousOpenAiFile, 'Process') }
    if ($SpeechTokenFile) { [Environment]::SetEnvironmentVariable('GREETING_SPEECH_TOKEN', $previousSpeechToken, 'Process') }
    $preparedSpeechToken = $null; $speechSecure = $null
    $preparedAdminKey = $null
}
