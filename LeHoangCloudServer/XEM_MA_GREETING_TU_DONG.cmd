@echo off
setlocal
title LE HOANG ROBOTICS - Greetingbot admin code
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
echo Run on server 192.168.0.123, with the same Windows account used to start the server.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0bin\greeting-web-20261118-v1.4.0-auto\scripts\Update-GreetingServer.ps1" -ExpectedHostIp 192.168.0.123 -ShowAdminKey
pause
