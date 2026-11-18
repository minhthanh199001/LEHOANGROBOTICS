param(
    [Parameter(Mandatory = $true)][string]$ApiKeyFile,
    [int]$Port = 5053,
    [string]$ListenAddress = '127.0.0.1',
    [string]$RobotSerial = '',
    [string]$AdbPath = 'adb',
    [switch]$ShowPairingCode,
    [string]$DataRoot = (Join-Path $env:LOCALAPPDATA 'LeHoangRobotics\GreetingSpeech')
)
$ErrorActionPreference = 'Stop'
function New-SpeechToken {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [BitConverter]::ToString($bytes).Replace('-', '')
}
if (-not (Test-Path -LiteralPath $ApiKeyFile -PathType Leaf)) { throw 'Không tìm thấy file khóa OpenAI.' }
if ($Port -lt 1024 -or $Port -gt 65535) { throw 'Cổng không hợp lệ.' }
if ($RobotSerial) {
    if ($RobotSerial -notmatch '^[a-zA-Z0-9_.:-]+$') { throw 'ADB serial không hợp lệ.' }
    if ($ListenAddress -ne '127.0.0.1') { throw 'Cầu nối hướng âm chỉ chạy trên loopback qua ADB reverse.' }
    $AdbPath = (Get-Command $AdbPath -CommandType Application -ErrorAction Stop).Source
    & $AdbPath -s $RobotSerial reverse "tcp:$Port" "tcp:$Port"
    if ($LASTEXITCODE -ne 0) { throw 'Không thiết lập được ADB reverse tới robot.' }
}
$repo = Split-Path $PSScriptRoot -Parent
$DataRoot = [IO.Path]::GetFullPath($DataRoot)
New-Item -ItemType Directory -Force -Path $DataRoot | Out-Null
$tokenFile = Join-Path $DataRoot 'speech-token.dpapi'
if (-not (Test-Path -LiteralPath $tokenFile)) {
    $token = New-SpeechToken
    ConvertTo-SecureString $token -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $tokenFile
    $token = $null
}
$secureToken = (Get-Content -LiteralPath $tokenFile -Raw).Trim() | ConvertTo-SecureString
$credential = [Net.NetworkCredential]::new('', $secureToken)
$launchFile = Join-Path $DataRoot 'launch.json'
$previousProcess = $null
if (Test-Path -LiteralPath $launchFile) {
    $previous = Get-Content -LiteralPath $launchFile -Raw | ConvertFrom-Json
    $candidate = Get-CimInstance Win32_Process -Filter ("ProcessId=" + [int]$previous.pid) -ErrorAction SilentlyContinue
    if ($candidate -and $previous.dll -and ([IO.Path]::GetFullPath([string]$previous.dll)).StartsWith((Join-Path $DataRoot 'versions') + '\', [StringComparison]::OrdinalIgnoreCase) -and
        $candidate.CommandLine.Contains([string]$previous.dll)) {
        $previousProcess = $candidate
    }
}
$listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
if ($listeners | Where-Object { -not $previousProcess -or $_.OwningProcess -ne $previousProcess.ProcessId }) { throw "Cổng $Port đã có ứng dụng khác sử dụng." }
$version = Get-Date -Format 'yyyyMMdd-HHmmss'
$publish = Join-Path $DataRoot "versions\$version"
dotnet publish (Join-Path $repo 'LeHoangCloudServer\LeHoangCloudServer.csproj') -c Release -o $publish --nologo | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Publish server giọng nói thất bại.' }
if ($previousProcess) {
    Stop-Process -Id $previousProcess.ProcessId -Force
    Wait-Process -Id $previousProcess.ProcessId -Timeout 10 -ErrorAction SilentlyContinue
}
$dll = Join-Path $publish 'LeHoangCloudServer.dll'
$url = "http://${ListenAddress}:$Port"
$names = @('OPENAI_API_KEY_FILE', 'OPENAI_API_KEY', 'GREETING_SPEECH_TOKEN', 'DATABASE_PATH', 'ASPNETCORE_URLS', 'GREETING_ADMIN_KEY', 'GREETING_ROBOT_ADB_SERIAL', 'GREETING_ADB_PATH')
$old = @{}
foreach ($name in $names) { $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:OPENAI_API_KEY_FILE = (Resolve-Path -LiteralPath $ApiKeyFile).Path
    $env:OPENAI_API_KEY = ''
    $env:GREETING_SPEECH_TOKEN = $credential.Password
    $env:DATABASE_PATH = Join-Path $DataRoot 'speech-fleet.db'
    $env:ASPNETCORE_URLS = $url
    $env:GREETING_ADMIN_KEY = New-SpeechToken
    $env:GREETING_ROBOT_ADB_SERIAL = $RobotSerial
    $env:GREETING_ADB_PATH = $AdbPath
    $process = Start-Process -FilePath 'dotnet' -ArgumentList @('"' + $dll + '"') -WorkingDirectory $publish -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $DataRoot 'server.stdout.log') -RedirectStandardError (Join-Path $DataRoot 'server.stderr.log')
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $old[$name], 'Process') }
}
@{ pid = $process.Id; dll = $dll; url = $url; tokenFile = $tokenFile; robotSerial = $RobotSerial; adbPath = $AdbPath } | ConvertTo-Json | Set-Content -LiteralPath $launchFile
$healthy = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
        $status = Invoke-RestMethod "http://127.0.0.1:$Port/api/greeting/speech/status" -Headers @{ Authorization = 'Bearer ' + $credential.Password } -TimeoutSec 2
        if ($status.ready -and (-not $RobotSerial -or $status.direction.ready)) { $healthy = $true; break }
    } catch {}
    Start-Sleep -Milliseconds 300
}
if ($ShowPairingCode) { Write-Output ("Mã kết nối giọng nói: " + $credential.Password) }
$credential = $null; $secureToken = $null
if (-not $healthy) { throw ('Server chưa sẵn sàng; trạng thái MicroArray: ' + $status.direction.state + '. Kiểm tra OpenAI/ADB và server.stderr.log.') }
Write-Output ("Speech server ready: $url; model=gpt-transcribe; PID=" + $process.Id)
if ($RobotSerial) { Write-Output ("MicroArray bridge: " + $status.direction.state + "; robot=" + $RobotSerial) }
Write-Output ("Protected device token: $tokenFile")
