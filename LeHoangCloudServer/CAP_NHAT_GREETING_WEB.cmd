@echo off
setlocal
title LE HOANG ROBOTICS - Greetingbot Web update
set "PSModulePath=%SystemRoot%\System32\WindowsPowerShell\v1.0\Modules;%ProgramFiles%\WindowsPowerShell\Modules"
echo Run this file ON SERVER 192.168.0.123, from its LOCAL DISK folder.
echo The existing database will be kept and backed up.
echo Keep the server console open after it starts.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0bin\greeting-web-20261118-v1.3.6\scripts\Update-GreetingServer.ps1" -PublishedDirectory "%~dp0bin\greeting-web-20261118-v1.3.6\runtime" -DatabasePath "%~dp0bin\Debug\net8.0\cloud_fleet.db" -ExpectedHostIp 192.168.0.123
pause
