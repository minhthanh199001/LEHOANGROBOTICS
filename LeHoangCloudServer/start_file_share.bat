@echo off
title LE HOANG ROBOTICS - HTTP File Server
echo ============================================================
echo   LE HOANG ROBOTICS - HTTP FILE SERVER
echo   Thu muc: F:\EDU\2026\LEHOANGROBOTICS\LeHoangCloudServer
echo   Dia chi truy cap: http://192.168.0.123:8001
echo   Dia chi noi bo:   http://localhost:8001
echo ============================================================
echo Dang chay may chu chia se file... (Nhan Ctrl+C de dung)
python -m http.server 8001 --bind 0.0.0.0 --directory "F:\EDU\2026\LEHOANGROBOTICS\LeHoangCloudServer"
pause

