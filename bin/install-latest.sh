#!/usr/bin/env bash

# Nexial Installer

INSTALLER_HOME=$(cd `dirname $0`/..; pwd -P)
${INSTALLER_HOME}/bin/installer.sh -install latest
