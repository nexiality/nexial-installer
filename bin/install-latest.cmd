@echo off
REM Nexial Installer

set INSTALLER_HOME=%~dp0..
%INSTALLER_HOME%\bin\installer.cmd -install latest
