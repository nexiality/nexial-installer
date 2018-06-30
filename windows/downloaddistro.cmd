@echo off
setlocal

set RC=0
set INSTALLER_HOME=%~dp0
set DOWNLOAD_LOC=C:\projects

REM download latest distro
echo [INFO] download latest distro...
for /f %%f in ('%INSTALLER_HOME%\wget -q --content-disposition --no-check-certificate -qO- "https://api.github.com/repos/nexiality/nexial-core/releases?prerelease=true" ^| %INSTALLER_HOME%\jq -r ".[0].assets[0].browser_download_url"') do (
	set DISTRO_URL="%%f"
)
set RC=%ERRORLEVEL%
if not "%RC%"=="0" (
	echo [ERROR] UNABLE TO DETERMINE LATEST DISTRO. CHECK YOUR INTERNET ACCESS.
	goto all-done
)

%INSTALLER_HOME%\wget -q --show-progress --content-disposition --no-check-certificate --directory-prefix=%DOWNLOAD_LOC% %DISTRO_URL%
set RC=%ERRORLEVEL%
if not "%RC%"=="0" (
	echo [ERROR] UNABLE TO DOWNLOAD LATEST DISTRO. CHECK YOUR INTERNET ACCESS.
	goto all-done
)


:all-done
	exit /b %RC%

