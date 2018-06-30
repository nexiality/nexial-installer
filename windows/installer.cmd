@echo off
REM Nexial Installer. v1.0

setlocal

REM set up
echo.
echo --------------------------------------------------------------------------------
echo NEXIAL INSTALLER v1.0
echo --------------------------------------------------------------------------------

set RC=0
set INSTALLER_HOME=%~dp0
set PLATFORM_HOME=C:\projects\nexial-core
set PLATFORM_BACKUP_HOME=%PLATFORM_HOME%.BAK

:kill-proc
	REM make sure processes related to Nexial are killed, before installation
	call %INSTALLER_HOME%\killproc.cmd
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" goto all-done


:wipe-backup
	REM if backup directory already exists, wipe it
	IF EXIST %PLATFORM_BACKUP_HOME%\nul (
		echo [INFO] remove existing backup in %PLATFORM_BACKUP_HOME%
		rmdir /s /q %PLATFORM_BACKUP_HOME% 1> nul
		set RC=%ERRORLEVEL%
		if not "%RC%"=="0" (
			echo [ERROR] UNABLE TO REMOVE BACKUP DIRECTORY AND HENCE UNABLE TO PROCEED. CHECK
			echo [ERROR] THAT %PLATFORM_BACKUP_HOME% IS NOT LOCKED.
			goto all-done
		)
	)


:backup-platform
	REM rename existing platform directory to backup
	echo [INFO] moving %PLATFORM_HOME% to %PLATFORM_BACKUP_HOME%
	move /Y %PLATFORM_HOME% %PLATFORM_BACKUP_HOME% 1> nul
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" (
		echo [ERROR] UNABLE TO BACK UP %PLATFORM_HOME% to %PLATFORM_BACKUP_HOME%.
		echo [ERROR] CHECK THAT BOTH DIRECTORIES ARE NOT LOCKED, AND NO COMMAND WINDOWS IS
		echo [ERROR] POINTING TO EITHER DIRECTORIES.
		goto all-done
	)

	REM now we are ready to upgrade; recreate platform directory
	%PLATFORM_HOME:~0,2%
	mkdir %PLATFORM_HOME%
	cd %PLATFORM_HOME%\..
	del /s /q /f %PLATFORM_HOME% 1> nul
	rmdir /s /q %PLATFORM_HOME% 1> nul


:download-distro
	call %INSTALLER_HOME%\downloaddistro.cmd
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" goto all-done


:install-distro
	REM install distro via unzip
	echo [INFO] unzip latest distro to %PLATFORM_HOME%...
	%INSTALLER_HOME%\unzip -q -o -d %PLATFORM_HOME% %PLATFORM_HOME%\..\*nexial*.zip
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" (
		echo [ERROR] UNABLE TO INSTALL LATEST DISTRO TO %PLATFORM_BACKUP_HOME%.
		echo [ERROR] CHECK THAT THE DIRECTORY IS NOT LOCKED, AND THE DISTRO IS NOT CORRUPTED.
		goto all-done
	)

	REM version.txt to platform home
	FOR /F "tokens=* USEBACKQ" %%F in (`dir /b %PLATFORM_HOME%\..\*nexial*.zip`) do (
		set ZIP_FILE=%%F
	)
	set DISTRO_FILE=%ZIP_FILE:~0,-4%
	echo %DISTRO_FILE% > %PLATFORM_HOME%\version.txt

	REM proofs
	echo [INFO] latest distro contains the following...
	dir /b %PLATFORM_HOME%\lib\nexial*.jar
	echo.


:clean-up
	REM clean up
	echo [INFO] remove distro zip
	del /s /q %PLATFORM_HOME%\..\*nexial*.zip 1> nul
	set RC=%ERRORLEVEL%
	if not "%RC%"=="0" (
		echo [ERROR] UNABLE TO REMOVE DISTRO. CHECK THAT THIS FILE IS NOT LOCKED.
		goto all-done
	)

	echo [INFO] latest distro is installed to %PLATFORM_HOME%.


:all-done
	exit /b %RC%
