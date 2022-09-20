#!/usr/bin/env bash

if [[ "$1" != "" ]]; then
    cp src/main/resources/NexialInstaller.properties ~/tmp/NexialInstaller.properties
    cp .${1}/NexialInstaller.properties src/main/resources/NexialInstaller.properties
fi

chmod -fR 755 bin/*.sh
gradle clean installDist
chmod -fR 755 build/install/nexial-installer/bin/*.sh
gradle distZip

if [[ "$1" != "" ]]; then
    cp ~/tmp/NexialInstaller.properties src/main/resources/NexialInstaller.properties
fi
