@echo off
REM Nexial Installer

setlocal
set INSTALLER_HOME=%~dp0..
%INSTALLER_HOME%\bin\installer.cmd -install latest
endlocal