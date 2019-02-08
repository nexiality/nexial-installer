#!/usr/bin/env bash

# Nexial Installer. v1.4

INSTALLER_HOME=$(cd `dirname $0`/..; pwd -P)
${INSTALLER_HOME}/bin/installer.sh -install latest
