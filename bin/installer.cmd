@echo off
REM Nexial Installer. v1.4

setlocal

REM set up
set RC=0
set INSTALLER_HOME=%~dp0..

:kill-proc
	REM make sure processes related to Nexial are killed, before installation
	call %INSTALLER_HOME%\bin\killprocs.cmd
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" goto all-done
	echo.

:run-installer
    java -jar %INSTALLER_HOME%\lib\nexial-installer-1.4.jar %*

:all-done
    endlocal
	exit /b %RC%
