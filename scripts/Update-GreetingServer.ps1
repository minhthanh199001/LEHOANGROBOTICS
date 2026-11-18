[CmdletBinding()]
param(
    [string]$DatabasePath,
    [ValidateRange(1024, 65535)][int]$Port = 5050,
    [switch]$ShowAdminKey,
    [string]$StateDirectory = (Join-Path $env:LOCALAPPDATA 'LeHoangRobotics\Greetingbot')
)
$ErrorActionPreference = 'Stop'
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
$dbFile = (Resolve-Path -LiteralPath $DatabasePath).Path
$repository = Split-Path -Parent $PSScriptRoot
$project = Join-Path $repository 'LeHoangCloudServer\LeHoangCloudServer.csproj'
if (!(Test-Path -LiteralPath $project)) { throw 'Can chay script tu checkout LEHOANGROBOTICS.' }
$releaseDirectory = Join-Path $stateDirectory ('server-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$compileDirectory = Join-Path $stateDirectory ('build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null

# Finish publishing before stopping the existing server.
& dotnet publish $project -c Release --no-self-contained "-p:OutputPath=$compileDirectory" -o $releaseDirectory --nologo
if ($LASTEXITCODE -ne 0) { throw 'Publish that bai. Server hien tai chua bi dung.' }
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
if (!(Test-Path -LiteralPath $keyFile)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $newKey = [Convert]::ToBase64String($bytes)
    $newKey | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $keyFile
    $newKey = $null
}
$env:GREETING_ADMIN_KEY = Read-AdminKey
$env:DATABASE_PATH = $dbFile
$env:ASPNETCORE_URLS = "http://0.0.0.0:$Port"
$env:ASPNETCORE_ENVIRONMENT = 'Production'
Write-Host "DB giu nguyen: $dbFile"
Write-Host "Ban sao luu: $backup"
Write-Host "Web: http://localhost:$Port -> tab GREETINGBOT"
Write-Host 'Xem ma quan tri trong cua so PowerShell khac: .\scripts\Update-GreetingServer.ps1 -ShowAdminKey'
Push-Location $releaseDirectory
try { & dotnet (Join-Path $releaseDirectory 'LeHoangCloudServer.dll') }
finally { Pop-Location; Remove-Item Env:GREETING_ADMIN_KEY -ErrorAction SilentlyContinue }
