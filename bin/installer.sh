#!/usr/bin/env bash

# Nexial Installer

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


# make sure required tools are available
RC=0
require-tool java

INSTALLER_HOME=$(cd `dirname $0`/..; pwd -P)

# make sure processes related to Nexial are killed, before installation
${INSTALLER_HOME}/bin/killprocs.sh
RC=$?
if [[ ${RC} -ne 0 ]]; then exit ${RC} ; fi
echo ""

# run nexial-installer
java -jar ${INSTALLER_HOME}/lib/nexial-installer-*.jar $*
