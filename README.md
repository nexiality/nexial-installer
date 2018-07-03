# nexial-installer

### What is this?
> _a platform-specific installer for Nexial; suitable for local and CI/CD environments._


### Introduction
The purpose of this project is to create a platform-aware installer for 
[Nexial Automation Platform](https://github.com/nexiality/nexial-core) (Nexial for short).

Nexial has a fairly straightforward installation model. Usually nothing more than just download the appropriate 
distribution and unzip it to desired location. However, to further simplify the installation and upgrade process and to
better support CI/CD deployment, this project was thus created.  

In a nutshell, the installer performs the following activities:
1. Prior to installing the latest version of Nexial, terminate all Nexial-related process such as `chromedriver`, 
   `geckodriver`, winium driver, `UISpy` and the Java process that executes Nexial automation.
2. If exists, remove backup directory, `project/nexial-core.BAK`, which stores previous version of Nexial. 
3. Back up (move) current Nexial directory from `projects/nexial-core` to `projects/nexial-core.BAK`.
4. Download the latest version of Nexial.
5. Unzip the latest version of Nexial to `project/nexial-core`.
6. Add a `version.txt` to `project/nexial-core`. This file contains the current build version of Nexial.
7. Clean up, including the Nexial distribution (zip file).


### Get Installer
The latest version is 1.2. Click on the appropriate link below to get the installer.

- [Linux installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.linux.zip)
- [Mac installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.mac.zip)
- [Windows installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.windows.zip)


### Usage
1. Download the installer (link above).
2. Unzip the installer to (Windows) `C:\projects\nexial-installer` or (MacOSX/Linux) `~/projects/nexial-installer`.
3. Run the installer script.
   - (Windows) From Windows Explorer, go to `C:\projects\nexial-installer`, double-click on `installer.cmd`.
   - (MacOSX) From Finder, go to `~/projects/nexial-installer`, double-click on `install.sh`.
   - (Linux) Open terminal, execute `cd ~/projects/nexial-installer; ./install.sh`.


### Limitation
1. Installer only install the latest version of Nexial.
2. Installer only back up one previous version of Nexial.
3. Installer does not self-update. In case a new version of installer is available, manual upgrade is required.


### Contribute
All contribution, suggestions, PR welcome!
