#!/usr/bin/env bash

function find-and-kill() {
	local progname=$1

	echo "[INFO] checking if ${progname} is running..."
	pgrep -f "${progname}" > /dev/null 2>&1

	if [ $? -eq 0 ] ; then
	    # found at least one instance running
	    if [ "${PROCEED_ALL}" = "true" ] ; then
	        pkill -f "${progname}"
	    else
            read -t 15 -n 1 -p "$progname must be terminated to install Nexial. Proceed? (Y/N/A) " RESPONSE
            echo
            if [ $? -gt 128 ] ; then
                # timed out waiting for user response
                RESPONSE=A
            fi

            case ${RESPONSE} in
                [aA])
                    PROCEED_ALL=true
                    pkill -f "${progname}"
                    ;;
                [yY])
                    pkill -f "${progname}"
                    ;;
                [nN])
                    echo "[INFO] Abort installation..."
                    RC=-513
                    ;;
                *)
                    echo "[INFO] Abort installation..."
                    RC=-513
                    ;;
            esac
        fi
	fi
}


RC=0
PROCEED_ALL=false

find-and-kill "java.+\-Dnexial\.home\="
if [ ${RC} -ne 0 ] ; then exit ${RC}; fi

find-and-kill chromedriver
if [ ${RC} -ne 0 ] ; then exit ${RC}; fi

find-and-kill chromedriver-electron
if [ ${RC} -ne 0 ] ; then exit ${RC}; fi

find-and-kill geckodriver
if [ ${RC} -ne 0 ] ; then exit ${RC}; fi

exit ${RC}
