# nexial-installer

### What is this?
> _a cross-platform installer for Nexial; suitable for local and CI/CD environments._


### Introduction
The purpose of this project is to create a cross-platform installer for 
[Nexial Automation Platform](https://github.com/nexiality/nexial-core) (Nexial for short).

Nexial has a fairly straightforward installation model, which is usually nothing more than just download the appropriate 
distribution and unzip it to desired location. However, to further simplify the installation and upgrade process, and to
better support CI/CD deployment, this project was thus created.

In a nutshell, the installer performs the following activities:
1. Prior to installing the latest version of Nexial, terminate all Nexial-related process such as `chromedriver`, 
   `geckodriver`, winium driver, `UISpy` and the Java process that executes Nexial automation.
2. If exists, remove backup directory (i.e. `project/nexial-core.BAK`) which stores previous version of Nexial. 
3. Back up (move) current Nexial directory (i.e. `projects/nexial-core`) to backup directory 
   (i.e. `projects/nexial-core.BAK`).
4. Download the latest or a specific version of Nexial.
5. Unzip the latest version of Nexial to `project/nexial-core`.
6. Add a `version.txt` to `project/nexial-core`. This file contains the current build version of Nexial.
7. Clean up, including the Nexial distribution (zip file).


### Get Installer
The latest version is 1.4.1. Click on the link below to get the installer.
- [Nexial Installer v1.4.1](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.4.1/nexial-installer-1.4.1.zip)

Note that 1.4.* is drastically different than the previous versions. Release 1.3 (and prior) utilize batch files (Windows),
shell scripts (*NIX/Mac) and a set of command line tools to perform the installation process. In order to support more
features and to ease the ongoing maintenance, we have switch to using Java in Release 1.4. This switch gives us more
consistency and stability across platforms, albeit a bigger distro as well. 


### Usage
1. Download the installer (link above).
2. Unzip the installer to (Windows) `C:\projects\nexial-installer` or (MacOSX/Linux) `~/projects/nexial-installer`.
3. Run the "install-latest" script. **Use this to install the latest version of Nexial.**
   - (Windows) From Windows Explorer, go to `C:\projects\nexial-installer`, double-click on `install-latest.cmd`.
   - (MacOSX) From Finder, go to `~/projects/nexial-installer`, double-click on `install-latest.sh`.
   - (Linux) Open terminal, execute `cd ~/projects/nexial-installer; ./install-latest.sh`.
   ~ OR ~
4. Run the installer script. **Use this to install a specific version of Nexial.**
   - (Windows) From Windows Explorer, go to `C:\projects\nexial-installer`, double-click on `installer.cmd`.
   - (MacOSX) From Finder, go to `~/projects/nexial-installer`, execute `installer.sh`.
   - (Linux) Open terminal, execute `cd ~/projects/nexial-installer; ./installer.sh`.


### Limitation
1. Installer only back up one previous version of Nexial. But one can use the `configure` option via the `installer` 
   script to customize a different location for backup (and thus retain multiple backups).
2. Installer does not self-update. In case a new version of installer is available, manual upgrade is required.


_NOT RECOMMENDED_ If you like to continue using pre-1.4 installer (but why...), you can click on the appropriate link below:
- [Linux installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.linux.zip)
- [Mac installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.mac.zip)
- [Windows installer](https://github.com/nexiality/nexial-installer/releases/download/nexial-installer-v1.3/nexial-installer.windows.zip)


### Contribute
All contribution, suggestions, PR welcome!
