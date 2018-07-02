#!/usr/bin/env bash

#
# Nexial Installer. v1.0
#

# --------------------------------------------------------------------------------
# functions
# --------------------------------------------------------------------------------
function require-tool() {
	local tool=$1

    which ${tool} > /dev/null 2>&1
    RC=$?
    if [ ${RC} -ne 0 ] ; then
        echo "[ERROR] '${tool}' IS REQUIRED BUT DOES NOT EXISTS OR CANNOT BE FOUND VIA PATH."
        echo "[ERROR] MAKE SURE IT IS INSTALLED AND AVAILABLE VIA \$PATH."
        echo
       exit ${RC}
    fi
}


echo
echo "--------------------------------------------------------------------------------"
echo "NEXIAL INSTALLER v1.0"
echo "--------------------------------------------------------------------------------"


# make sure required tools are available
RC=0
require-tool wget
require-tool unzip
require-tool basename
require-tool dirname
require-tool pgrep
require-tool pkill


INSTALLER_HOME=$(cd `dirname $0`; pwd -P)
DOWNLOAD_LOC=~/projects
PLATFORM_HOME=~/projects/nexial-core
PLATFORM_BACKUP_HOME=${PLATFORM_HOME}.BAK

echo "[INFO] INSTALL_HOME=${INSTALLER_HOME}"
echo "[INFO] PLATFORM_HOME=${PLATFORM_HOME}"


# make sure processes related to Nexial are killed, before installation
${INSTALLER_HOME}/killprocs.sh
RC=$?
if [ ${RC} -ne 0 ] ; then exit ${RC} ; fi


# if backup directory already exists, wipe it
if [ ! -d ${PLATFORM_BACKUP_HOME} ] ; then
    echo "[INFO] remove existing backup in ${PLATFORM_BACKUP_HOME}"
    rm -fR ${PLATFORM_BACKUP_HOME}
    RC=$?
    if [ ${RC} -ne 0 ] ; then
        echo "[ERROR] UNABLE TO REMOVE BACKUP DIRECTORY AND HENCE UNABLE TO PROCEED. CHECK"
        echo "[ERROR] THAT ${PLATFORM_BACKUP_HOME} IS NOT LOCKED."
        exit ${RC}
    fi
fi


# rename existing platform directory to backup
if [ -d ${PLATFORM_HOME} ] ; then
    echo "[INFO] moving ${PLATFORM_HOME} to ${PLATFORM_BACKUP_HOME}"
    mv -f ${PLATFORM_HOME} ${PLATFORM_BACKUP_HOME}
    RC=$?
    if [ ${RC} -ne 0 ] ; then
        echo "[ERROR] UNABLE TO BACK UP ${PLATFORM_HOME} to ${PLATFORM_BACKUP_HOME}."
        echo "[ERROR] CHECK THAT BOTH DIRECTORIES ARE NOT LOCKED, AND NO COMMAND WINDOWS IS"
        echo "[ERROR] POINTING TO EITHER DIRECTORIES."
        exit ${RC}
    fi
fi


# now we are ready to upgrade; recreate platform directory
mkdir -p ${PLATFORM_HOME}
rm -fR ${PLATFORM_HOME}
cd ${DOWNLOAD_LOC}
rm -f *nexial*.zip


# download latest distro
${INSTALLER_HOME}/downloaddistro.sh
RC=$?
if [ ${RC} -ne 0 ] ; then exit ${RC} ; fi


# install distro via unzip
echo "[INFO] unzip latest distro to ${PLATFORM_HOME}..."
unzip -q -o -d ${PLATFORM_HOME} ${DOWNLOAD_LOC}/*nexial*.zip
RC=$?
if [ ${RC} -ne 0 ] ; then
    echo "[ERROR] UNABLE TO INSTALL LATEST DISTRO TO ${PLATFORM_BACKUP_HOME}."
    echo "[ERROR] CHECK THAT THE DIRECTORY IS NOT LOCKED, AND THE DISTRO IS NOT CORRUPTED."
    exit ${RC}
fi


# version.txt to platform home
DISTRO_FILE=`ls ${PLATFORM_HOME}/../*nexial*.zip`
DISTRO_FILE=`basename ${DISTRO_FILE}`
echo `echo ${DISTRO_FILE} | cut -d'.' -f 1` >  ${PLATFORM_HOME}/version.txt


# proofs
echo "[INFO] latest distro contains the following..."
ls -1 ${PLATFORM_HOME}/lib/nexial*.jar
RC=$?
if [ ${RC} -ne 0 ] ; then
    echo "[ERROR] UNABLE TO FIND ANY NEXIAL RELATED JAR FILES; INSTALLATION FAILED!!!"
    echo "[ERROR] MAKE SURE THERE ISN'T ANY PERMISSION OR DISKSPACE ISSUES."
    exit ${RC}
fi
echo


# clean up
echo "[INFO] remove distro zip"
rm -f ${DOWNLOAD_LOC}/*nexial*.zip
RC=$?
if [ ${RC} -ne 0 ] ; then
    echo "[ERROR] UNABLE TO REMOVE DISTRO. CHECK THAT THIS FILE IS NOT LOCKED."
    exit ${RC}
fi

echo "[INFO] latest distro is installed to ${PLATFORM_HOME}."
echo

exit ${RC}
