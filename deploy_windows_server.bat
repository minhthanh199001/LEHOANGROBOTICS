@echo off
chcp 65001 >nul
title LÊ HOÀNG ROBOTICS - CLOUD FLEET SERVER
color 0B

echo ================================================================
echo    LÊ HOÀNG ROBOTICS - KHỞI CHẠY CLOUD FLEET SERVER
echo ================================================================
echo.

cd /d "%~dp0"

echo [*] Kiểm tra môi trường .NET 8 SDK / Runtime...
dotnet --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] LỖI: Chưa cài đặt .NET 8!
    echo Vui lòng tải và cài đặt .NET 8 Hosting Bundle / SDK tại:
    echo https://dotnet.microsoft.com/download/dotnet/8.0
    pause
    exit /b 1
)

echo [+] Mở cổng tường lửa Windows Firewall cho Port 5050...
netsh advfirewall firewall add rule name="LeHoang_Cloud_5050" dir=in action=allow protocol=TCP localport=5050 >nul 2>&1

echo [+] Đang khởi động LeHoangCloudServer (Port 5050)...
echo.
echo ================================================================
echo   Trang Web Quản trị Cloud: http://localhost:5050
echo   SignalR Fleet Hub:       http://localhost:5050/fleetHub
echo ================================================================
echo.

dotnet run --project LeHoangCloudServer/LeHoangCloudServer.csproj -c Release

pause
