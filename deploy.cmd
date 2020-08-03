@echo off

if not "%1" == "" (
    copy src\main\resources\NexialInstaller.properties %TMP%\NexialInstaller.properties
    copy .%1\NexialInstaller.properties src\main\resources\NexialInstaller.properties
)

call gradle clean distZip installDist

if not "%1" == "" (
    copy %TMP%\NexialInstaller.properties src\main\resources\NexialInstaller.properties
)
