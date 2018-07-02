@echo off
setlocal enableextensions enabledelayedexpansion

set INSTALLER_HOME=%~dp0
set RC=0
set PROCEED_ALL=false

REM Checks for Jenkins Master, if master set options

for /F "tokens=3 delims=: " %%H in ('sc query "Jenkins" ^| findstr "        STATE"') do (
  if /I "%%H" EQU "RUNNING" (
   Echo "jenkins is running"
   set PROCEED_ALL=true
  )
)

REM Checks for jenkins slave, if slave then set options
IF EXIST C:\jenkins\agent.jar set PROCEED_ALL=true

call :find-and-kill java.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill chromedriver.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill chromedriver-electron.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill geckodriver.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill geckodriver64.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill IEDriverServer.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill IEDriverServer64.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill MicrosoftWebDriver.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill notifu.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill notifu64.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill UISpy.exe
if not "%RC%"=="0" goto all-done

call :find-and-kill Winium.Desktop.Driver.exe
if not "%RC%"=="0" goto all-done

goto all-done


:find-and-kill
	set progname=%1

	echo [INFO] checking if %progname% is running...
	for /F "tokens=* USEBACKQ" %%F in (`tasklist /fi "IMAGENAME eq %progname%" /fo table`) do (
		set taskresult=%%F
	)

	echo %taskresult% | findstr /P /C:"No tasks are running" 1>nul
	if errorlevel 1 (
		REM found at least one instance running
		if not "%PROCEED_ALL%"=="true" (
			choice /C YNA /T 15 /D A /M "%progname% must be terminated to install Nexial. Proceed?"
			if ERRORLEVEL 3 set PROCEED_ALL=true
			if ERRORLEVEL 2 goto abort-killproc
		)

		if not "%progname%"=="java.exe" (
			REM forcefully kill
			taskkill /F /IM %progname%
		) else (
			wmic process where "name like 'java.exe' and commandline like '%%-Dnexial.home=%%'" get processid /format:list > %INSTALLER_HOME%\java_proc.txt
			for /f "delims== tokens=2" %%F in ('type %INSTALLER_HOME%\java_proc.txt ^| findstr /p /c:ProcessId=') do (
				taskkill /F /PID %%F
			)
		)

		echo.
		set RC=0
	)

	goto :EOF


:abort-killproc
	echo [INFO] Abort installation...
	SET rc=-513

:all-done
	exit /b %RC%
