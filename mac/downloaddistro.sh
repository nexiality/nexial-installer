#!/usr/bin/env bash


RC=0
INSTALLER_HOME=`dirname $0`
DOWNLOAD_LOC=~/projects


# download latest distro
echo "[INFO] download latest distro..."
DISTRO_URL=`wget -q --content-disposition --no-check-certificate -qO- "https://api.github.com/repos/nexiality/nexial-core/releases?prerelease=true" ^| ${INSTALLER_HOME}/jq -r ".[0].assets[0].browser_download_url"`
RC=$?
if [ ${RC} -ne 0 ] ; then
	echo [ERROR] UNABLE TO DETERMINE LATEST DISTRO. CHECK YOUR INTERNET ACCESS.
	exit ${RC}
fi

wget -q --show-progress --content-disposition --no-check-certificate --directory-prefix=${DOWNLOAD_LOC} ${DISTRO_URL}
RC=$?
if [ ${RC} -ne 0 ] ; then
	echo [ERROR] UNABLE TO DOWNLOAD LATEST DISTRO. CHECK YOUR INTERNET ACCESS.
	exit ${RC}
fi

exit ${RC}
