#!/usr/bin/env bash

if [[ "$1" != "" ]]; then
    cp src/main/resources/NexialInstaller.properties ~/tmp/NexialInstaller.properties
    cp .${1}/NexialInstaller.properties src/main/resources/NexialInstaller.properties
fi

gradle distZip installDist

if [[ "$1" != "" ]]; then
    cp ~/tmp/NexialInstaller.properties src/main/resources/NexialInstaller.properties
fi
