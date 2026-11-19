#Requires -Version 7.0
param(
    [Parameter(Mandatory=$true)][string]$PublishedDirectory,
    [Parameter(Mandatory=$true)][string]$ShareRoot,
    [Parameter(Mandatory=$true)][string]$TargetIp,
    [string]$DatabaseRelativePath = 'bin\Debug\net8.0\cloud_fleet.db',
    [switch]$EnableLanAutoPairing,
    [ValidatePattern('^(_[A-Z0-9]+)*$')][string]$LauncherSuffix = '',
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')][string]$VersionName = ('greeting-web-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
$parsedIp = $null
if (-not [Net.IPAddress]::TryParse($TargetIp, [ref]$parsedIp) -or $parsedIp.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { throw 'TargetIp must be an IPv4 address.' }
$root = (Resolve-Path -LiteralPath $ShareRoot).ProviderPath.TrimEnd('\')
$prefix = $root + '\'
$database = [IO.Path]::GetFullPath((Join-Path $root $DatabaseRelativePath))
if (-not $database.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath $database -PathType Leaf)) { throw 'Database must already exist inside the shared project.' }
if ($DatabaseRelativePath -match '[%&|<>^"\r\n]') { throw 'Unsupported database path for a command launcher.' }
if (-not (Test-Path -LiteralPath (Join-Path $root 'LeHoangCloudServer.csproj'))) { throw 'The shared folder is not the expected server project.' }
$published = (Resolve-Path -LiteralPath $PublishedDirectory).ProviderPath
foreach ($required in @('LeHoangCloudServer.dll','LeHoangCloudServer.runtimeconfig.json','wwwroot\index.html','wwwroot\js\greeting.js')) {
    if (-not (Test-Path -LiteralPath (Join-Path $published $required) -PathType Leaf)) { throw "Incomplete package: $required" }
}
if (Get-ChildItem -LiteralPath $published -Recurse -File | Where-Object { $_.Name -like '*.db*' -or $_.Extension -eq '.dpapi' }) { throw 'Published package must not contain databases or protected credentials.' }
$versionRelative = 'bin\' + $VersionName
$version = [IO.Path]::GetFullPath((Join-Path $root $versionRelative))
if (-not $version.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $version)) { throw 'Version directory must be new and inside the shared project.' }
$applyName = 'CAP_NHAT_GREETING_WEB' + $LauncherSuffix + '.cmd'
$showName = 'XEM_MA_GREETING' + $LauncherSuffix + '.cmd'
$guideName = 'HUONG_DAN_GREETING_WEB' + $LauncherSuffix + '.txt'
foreach ($name in @($applyName,$showName,$guideName)) {
    if (Test-Path -LiteralPath (Join-Path $root $name)) { throw "Launcher already exists; refusing to overwrite it: $name" }
}
$runtime = Join-Path $version 'runtime'
$scripts = Join-Path $version 'scripts'
New-Item -ItemType Directory -Path $runtime,$scripts -Force | Out-Null
Get-ChildItem -LiteralPath $published -Force | Copy-Item -Destination $runtime -Recurse -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Update-GreetingServer.ps1') -Destination $scripts
$manifest = @()
foreach ($file in Get-ChildItem -LiteralPath $published -Recurse -File) {
    $relative = [IO.Path]::GetRelativePath($published, $file.FullName)
    $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    $targetHash = (Get-FileHash -LiteralPath (Join-Path $runtime $relative) -Algorithm SHA256).Hash
    if ($sourceHash -ne $targetHash) { throw "Copied package verification failed: $relative" }
    $manifest += @{ path=$relative; sha256=$sourceHash }
}
@{ targetIp=$TargetIp; databaseRelativePath=$DatabaseRelativePath; lanAutoPairing=[bool]$EnableLanAutoPairing; files=$manifest } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $version 'package-manifest.json') -Encoding utf8
$apply = @'
@echo off
setlocal
title LE HOANG ROBOTICS - Greetingbot Web update
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
echo Run this file ON SERVER {IP}, from its LOCAL DISK folder.
echo The existing database will be kept and backed up.
echo Keep the server console open after it starts.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0{VERSION}\scripts\Update-GreetingServer.ps1" -PublishedDirectory "%~dp0{VERSION}\runtime" -DatabasePath "%~dp0{DB}" -ExpectedHostIp {IP}{MODE}
pause
'@
$show = @'
@echo off
setlocal
title LE HOANG ROBOTICS - Greetingbot admin code
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
echo Run on server {IP}, with the same Windows account used to start the server.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0{VERSION}\scripts\Update-GreetingServer.ps1" -ExpectedHostIp {IP} -ShowAdminKey
pause
'@
$mode = if ($EnableLanAutoPairing) { ' -EnableLanAutoPairing' } else { '' }
$apply.Replace('{IP}',$TargetIp).Replace('{VERSION}',$versionRelative).Replace('{DB}',$DatabaseRelativePath).Replace('{MODE}',$mode) | Set-Content -LiteralPath (Join-Path $root $applyName) -Encoding ascii
$show.Replace('{IP}',$TargetIp).Replace('{VERSION}',$versionRelative) | Set-Content -LiteralPath (Join-Path $root $showName) -Encoding ascii
$pairingGuide = if ($EnableLanAutoPairing) {
    '5. Mo tab GREETINGBOT qua IP server. Robot tu tao ID/ma noi bo va xuat hien tren Web khi cung LAN; khong can nhap ma quan tri/ghep noi.'
} else { '5. Trong Web, chon GREETINGBOT, nhap ma quan tri, tao ma ghep noi va nhap vao cau hinh ket noi Web tren robot.' }
$guide = @"
CAP NHAT WEB GREETINGBOT

1. Tren may server $TargetIp, mo thu muc LOCAL cua LeHoangCloudServer.
2. Chay $applyName. Khong chay tu may khac qua duong dan mang.
3. Script chuan bi ban chay moi, kiem tra ma quan tri ma hoa, dung dung tien trinh LeHoangCloudServer,
   sao luu database hien tai, roi mo Web moi tai http://${TargetIp}:5050.
4. Giu cua so server dang mo. Dung $showName (cung tai khoan Windows) neu can xem ma quan tri thu cong.
$pairingGuide
6. Chinh Bu theo chieu quay; mac dinh 25 do. Gia tri duoc luu trong database va gui xuong robot khi dong bo.

DATABASE GIU NGUYEN: $DatabaseRelativePath
Ban sao luu va ban chay moi: %LOCALAPPDATA%\LeHoangRobotics\Greetingbot
Goi cap nhat: $versionRelative
Ma nguon va file Debug/Release cu khong bi ghi de. Neu can quay lai: dung cua so server moi,
mo PowerShell tai thu muc LeHoangCloudServer va chay .\bin\Debug\net8.0\LeHoangCloudServer.exe.
Khong thay URL giong noi OpenAI tren robot: cau noi PC/ADB hien tai van duoc giu nguyen.
"@
$guide | Set-Content -LiteralPath (Join-Path $root $guideName) -Encoding utf8
Write-Output ("Prepared and verified $($manifest.Count) runtime files: $version")
Write-Output ("Run $applyName locally on server $TargetIp. The running server and database have not been changed.")
