/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nexial.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static java.io.File.separator;
import static org.nexial.installer.Const.*;
import static org.nexial.installer.OutputUtils.*;

/**
 * this cmdline tool has 3 options:<li>
 * <li>list available versions of Nexial</li>
 * <li>install specific version of Nexial</li>
 * <li>install latest version of Nexial</li>
 * </li>
 * <p>
 * another design consideration is to keep this project as small as possible in terms of footprint (meaning: no or
 * little 3rd party dependencies) so that it is easier to distribute and to maintain.
 */
public class NexialInstaller {
    private static final PlatformSpecificLocationHandler DOWNLOAD_LOCATION_HANDLER =
        new PlatformSpecificLocationHandler() {
            @Override
            public File resolveForWindows(String base) { return new File(DOWNLOAD_DIR + base + ".zip"); }

            @Override
            public File resolveForLinux(String base) { return new File(DOWNLOAD_DIR + base + ".zip"); }

            @Override
            public File resolveForMac(String base) { return resolveForLinux(base); }
        };
    private static final PlatformSpecificLocationHandler NEXIAL_LOCATION_HANDLER =
        new PlatformSpecificLocationHandler() {
            @Override
            public File resolveForWindows(String base) { return new File(PROJECT_BASE_WIN + base); }

            @Override
            public File resolveForLinux(String base) { return new File(PROJECT_BASE_NIX + base); }

            @Override
            public File resolveForMac(String base) { return resolveForLinux(base); }
        };

    private static final Properties props = initProps();
    private static File installTarget = resolveNexialHome();
    private static File backupTarget = resolveNexialHomeBackup();
    private static boolean keepDownloaded;

    private static int exitCode;
    private static Map<String, String> availableVersions = new TreeMap<>();

    protected interface PlatformSpecificLocationHandler {
        File resolveForWindows(String base);

        File resolveForLinux(String base);

        File resolveForMac(String base);
    }

    public static void main(String[] args) {
        try {
            if (args != null && args.length > 0) {
                if (args.length == 1 && StringUtils.equals(args[0], "-help")) {
                    showHelp();
                } else {
                    handleCommand(CommandLineOptions.newInstance(args));
                }
                exit(exitCode);
            } else {
                showMenu();
            }
        } catch (Throwable e) {
            error(e.getMessage());
            exit(ERR_UNKNOWN_EXCEPTION);
        }
    }

    protected static Properties initProps() {
        InputStream propResource = NexialInstaller.class.getResourceAsStream(INSTALLER_PROPS);
        try {
            Properties props = new Properties();
            props.load(propResource);
            return props;
        } catch (IOException e) {
            throw new RuntimeException("Unable to load " + INSTALLER_PROPS);
        }
    }

    protected static String readStdin() { return new Scanner(System.in).nextLine(); }

    protected static void showHelp() {
        showBanner();
        System.out.println("USAGE: [ -list | [ -install [version] -target [path] -backup [path] [-keepDownloaded] ] ]");
        System.out.println("\t-list    list available versions");
        System.out.println("\t-install [latest|version]");
        System.out.println("\t-target  [full path of where to install Nexial]");
        System.out.println("\t-backup  [full path of where to backup existing Nexial]\n" +
                           "\t         Omit means no backup");
        System.out.println("\t-keepDownloaded indicates that the downloaded distro (zip) should be\n" +
                           "\t         kept in Nexial home");
        System.out.println("NO PARAMETER: interactive installation menu.");
        System.out.println();
        System.out.println();
    }

    protected static void showMenu() {
        showBanner();
        showOptions();

        // Scanner in = new Scanner(System.in);
        // String input = in.nextLine();
        String input = readStdin();

        while (input != null && !(OPT_QUIT.equals(input) || OPT_QUIT_Q.equals(input))) {
            input = input.trim();
            if (input.length() > 0) {
                if (OPT_QUIT.equals(input) || OPT_QUIT_Q.equals(input)) { break; }

                int splitIndex = input.indexOf(" ");
                String command = splitIndex == -1 ? input : input.substring(0, splitIndex);
                String version = splitIndex == -1 ? null : input.substring(splitIndex + 1);

                try {
                    handleCommand(command, version);
                } catch (Exception e) {
                    showError(e.getMessage());
                }
            }

            System.out.println();
            showOptions();

            input = readStdin();
        }
    }

    protected static void showBanner() {
        String edition = props.getProperty(PROP_EDITION);
        if (StringUtils.isBlank(edition)) {
            edition = "";
        } else {
            edition = " (" + edition + " edition)";
        }
        System.out.println(repeatLine("-", LINE_WIDTH));
        System.out.println(padCenter("[", APP + " " + VERSION + edition, "]", LINE_WIDTH));
        System.out.println(repeatLine("-", LINE_WIDTH));
    }

    protected static void showOptions() {
        System.out.println("OPTIONS:");
        System.out.println("\t" + OPT_LIST + " (" + OPT_LIST_L + ")" + "\t- list the Nexial versions currently available for download.");
        System.out.println("\t" + OPT_INSTALL + " (" + OPT_INSTALL_I + ")" + "\t- install a specific version or latest.");
        System.out.println("\t" + OPT_CONFIGURE + " (" + OPT_CONFIGURE_C + ")" + "\t- customize installation location.");
        System.out.println("\t" + OPT_QUIT + " (" + OPT_QUIT_Q + ")" + "\t- exit.");
        System.out.print("COMMAND: ");
    }

    protected static void handleCommand(CommandLineOptions cmdlineOptions) throws IOException {
        if (cmdlineOptions == null) {
            error("Missing or wrong command line arguments. Please double check and try again.");
            exitCode = ERR_ARGS_MISSING;
            return;
        }

        if (cmdlineOptions.isListOnly()) {
            listAvailableVersions().keySet().forEach(System.out::println);
            return;
        }

        if (StringUtils.isBlank(cmdlineOptions.getVersion())) {
            error("No version specified.");
            exitCode = ERR_MISSING_VERSION;
            return;
        }

        if (cmdlineOptions.getInstallTarget() != null) {
            File target = new File(cmdlineOptions.getInstallTarget());
            if ((!target.exists() || !target.isDirectory()) && !target.mkdirs()) {
                error("Unable to create directory " + cmdlineOptions.getInstallTarget() + ". " +
                      "Please make sure you have permission to create this directory and try again.");
                exitCode = ERR_FAIL_CREATE_DIR;
                return;
            } else {
                installTarget = target;
            }
        }

        if (cmdlineOptions.getBackupTarget() != null) {
            File backup = new File(cmdlineOptions.getBackupTarget());
            if (!backup.mkdirs()) {
                error("Unable to create directory " + cmdlineOptions.getBackupTarget() + ". " +
                      "Please make sure you have permission to create this directory and try again.");
                exitCode = ERR_FAIL_CREATE_DIR;
                return;
            } else {
                backupTarget = backup;
            }
        } else {
            backupTarget = null;
        }

        keepDownloaded = cmdlineOptions.isKeepDownloaded();
        install(cmdlineOptions.getVersion());
    }

    protected static void handleCommand(String command, String version) throws IOException {
        if (OPT_LIST.equals(command) || OPT_LIST_L.equals(command)) {
            showVersions();
            exitCode = 0;
            return;
        }

        if (OPT_CONFIGURE.equals(command) || OPT_CONFIGURE_C.equals(command)) {
            configure();
            exitCode = 0;
            return;
        }

        if (OPT_INSTALL.equals(command) || OPT_INSTALL_I.equals(command)) {
            if (StringUtils.isBlank(version)) {
                showError("Please specify either latest or a specific version to install");
                System.err.println("For example:");
                System.err.println("\t" + OPT_INSTALL + " latest or " + OPT_INSTALL_I + " latest");
                System.err.println("\t" + OPT_INSTALL + " nexial-core-v1.9_0400 or " + OPT_INSTALL_I + " nexial-core-v1.9_0400");
                exitCode = ERR_MISSING_VERSION;
                return;
            }

            if (VER_LATEST.equals(version)) {
                installLatest();
            } else {
                install(version);
            }

            exitCode = 0;
        }
    }

    protected static void configure() {
        System.out.println();
        System.out.println("press ENTER to accept current configurations.");

        installTarget = configureDirectory("installation", installTarget);
        backupTarget = configureDirectory("backup", backupTarget);
        keepDownloaded = configureKeepDownloaded(keepDownloaded);
    }

    protected static boolean configureKeepDownloaded(boolean keepDownloaded) {
        System.out.print("keep downloaded Nexial distro? (" + BooleanUtils.toStringYesNo(keepDownloaded) + "): ");

        String input = readStdin();
        while (StringUtils.isNotEmpty(input)) {
            Boolean response = BooleanUtils.toBooleanObject(input);
            if (response != null) {
                keepDownloaded = response;
                break;
            }

            System.out.print("keep downloaded Nexial distro? (" + BooleanUtils.toStringYesNo(keepDownloaded) + "): ");
            input = readStdin();
        }

        System.out.println("keep downloaded Nexial distro? " + BooleanUtils.toStringYesNo(keepDownloaded));
        if (keepDownloaded) { System.out.println("downloaded Nexial distro will be kept in " + installTarget); }
        return keepDownloaded;
    }

    protected static File configureDirectory(String type, File target) {
        System.out.print(type + " directory (" + target + "): ");

        String input = readStdin();
        while (StringUtils.isNotEmpty(input)) {
            File newDir = verifyDirectory(input);
            if (newDir == null) {
                System.out.print(type + " directory (" + target + "): ");
                input = readStdin();
            } else {
                target = newDir;
                break;
            }
        }

        System.out.println(type + " directory: " + target);
        System.out.println();
        return target;
    }

    protected static File verifyDirectory(String directory) {
        if (StringUtils.isBlank(directory)) { return null; }

        File dir = new File(directory);

        // could it be a directory?
        if (!dir.exists()) {
            System.out.print("Directory " + directory + " does not exist. Create it? (yes/no) ");
            String input = readStdin();
            if (StringUtils.isBlank(input)) { return null; }
            Boolean create = BooleanUtils.toBooleanObject(input);
            if (create == null || !create) { return null; }

            if (dir.mkdirs()) { return dir; }

            System.err.println("Unable to create directory " + directory + ". " +
                               "Please make sure you have permission to create this directory and try again.");
            exitCode = ERR_FAIL_CREATE_DIR;
            return null;
        }

        if (!dir.canRead()) {
            System.err.println("Unable to read from " + directory + ". Please make sure you have permission to read " +
                               "from this location and try again.");
            exitCode = ERR_UNKNOWN_EXCEPTION;
            return null;
        }

        // is a directory?
        if (dir.isDirectory()) { return dir; }

        // is a file?
        System.err.println(directory + " is a file, not a directory. Please specify a valid directory instead.");
        exitCode = ERR_UNKNOWN_EXCEPTION;
        return null;
    }

    protected static void showVersions() throws IOException {
        if (availableVersions == null || availableVersions.size() < 1) { availableVersions = listAvailableVersions(); }
        availableVersions.keySet().forEach(System.out::println);
    }

    protected static Map<String, String> listAvailableVersions() throws IOException {
        String versionUrl = props.getProperty(PROP_VERSIONS_URL);
        if (StringUtils.isBlank(versionUrl)) { throw new IOException("${" + PROP_VERSIONS_URL + "} not configured!"); }

        Map<String, String> versions = new TreeMap<>(Comparator.reverseOrder());

        // github json
        if (StringUtils.contains(versionUrl, "github.com")) { return handleGithubUrl(versionUrl, versions); }

        // support for JSON Lines (http://jsonlines.org/)
        // useful for MinIO implementation
        if (StringUtils.endsWith(versionUrl, ".jsonl")) { return handleJsonLines(versionUrl, versions); }

        // default treatment
        // html treatment
        return handleHtmlUrl(versionUrl, versions);
    }

    private static Map<String, String> handleGithubUrl(String versionUrl, Map<String, String> versions)
        throws IOException {
        JsonElement response = HttpUtils.getJson(versionUrl);
        if (response == null || !response.isJsonArray()) {
            throw new RuntimeException("Expected JSON content not found via ${" + PROP_VERSIONS_URL + "}");
        }

        JsonArray assets = (JsonArray) response;
        if (assets.size() < 1) {
            throw new RuntimeException("Expected JSON structure not found via ${" + PROP_VERSIONS_URL + "}");
        }

        for (int i = 0; i < assets.size(); i++) {
            JsonObject release = assets.get(i).getAsJsonObject();
            if (!release.has("tag_name") || !release.has("assets")) { continue; }

            String version = release.get("tag_name").getAsString();

            JsonArray thisAssets = release.get("assets").getAsJsonArray();
            if (thisAssets == null || thisAssets.size() < 1) { continue; }

            JsonObject firstAsset = thisAssets.get(0).getAsJsonObject();
            if (!firstAsset.has("browser_download_url")) { continue; }

            versions.put(version, firstAsset.get("browser_download_url").getAsString());
        }

        return versions;
    }

    private static Map<String, String> handleHtmlUrl(String versionUrl, Map<String, String> versions)
        throws IOException {
        String html = HttpUtils.getText(versionUrl);
        if (StringUtils.isBlank(html)) {
            throw new RuntimeException("Expected HTML content not found via ${" + PROP_VERSIONS_URL + "}");
        }

        String downloadUrlBase = StringUtils.substringBeforeLast(versionUrl, "/");
        String[] lines = StringUtils.split(html, '\n');
        Arrays.stream(lines).forEach(line -> {
            if (line.matches(REGEX_A_HREF_TAG)) {
                String version = StringUtils.substringBefore(StringUtils.substringAfter(line, ">"), "</a>");
                String url = downloadUrlBase + "/" + version;
                version = StringUtils.substringBefore(version, ".zip");
                versions.put(version, url);
            }
        });

        return versions;
    }

    private static Map<String, String> handleJsonLines(String versionUrl, Map<String, String> versions)
        throws IOException {
        String jsonlContent = HttpUtils.getText(versionUrl);
        if (StringUtils.isBlank(jsonlContent)) {
            throw new RuntimeException("Expected JSON Lines content not found via ${" + PROP_VERSIONS_URL + "}");
        }

        String[] jsonLines = StringUtils.split(jsonlContent, "\n");
        if (ArrayUtils.isEmpty(jsonLines)) {
            throw new RuntimeException("No JSON Lines found via ${" + PROP_VERSIONS_URL + "}");
        }

        Arrays.stream(jsonLines).forEach(json -> {
            JsonObject jsonObject = GSON.fromJson(json, JsonObject.class);

            String distroName = null;
            if (jsonObject.has("key")) {
                String key = jsonObject.get("key").getAsString();
                if (StringUtils.endsWith(key, ".zip")) { distroName = key; }
            }

            String distroBaseUrl = null;
            if (jsonObject.has("url")) {
                String url = jsonObject.get("url").getAsString();
                if (StringUtils.isNotBlank(url)) { distroBaseUrl = url; }
            }

            if (StringUtils.isNotBlank(distroBaseUrl) && StringUtils.isNotBlank(distroName)) {
                String distroUrl = distroBaseUrl + distroName;
                versions.put(distroName, distroUrl);
            }
        });

        return versions;
    }

    protected static String resolveDownloadUrl(String version) throws IOException {
        if (availableVersions == null || availableVersions.size() < 1) { availableVersions = listAvailableVersions(); }
        if (availableVersions.containsKey(version)) { return availableVersions.get(version); }
        throw new IOException("Unable to resolve download URL for version " + version);
    }

    protected static void installLatest() throws IOException { install(VER_LATEST); }

    protected static void install(String version) throws IOException {
        if (StringUtils.isBlank(version)) {
            error("No version specified.");
            return;
        }

        if (availableVersions == null || availableVersions.size() < 1) {
            availableVersions = listAvailableVersions();
            if (availableVersions == null || availableVersions.size() < 1) {
                error("No versions available for Nexial");
                exitCode = ERR_DOWNLOAD_FAILED;
                return;
            }
        }

        version = version.trim();
        if (VER_LATEST.equals(version)) {
            version = availableVersions.keySet().iterator().next();
        } else {
            if (!availableVersions.containsKey(version)) {
                error("Specified version not found or not available");
                exitCode = ERR_MISSING_VERSION;
                return;
            }
        }

        String downloadFrom = resolveDownloadUrl(version);
        File downloadTo = resolveDownloadLocation(version);

        log("downloading Nexial distro to " + downloadTo);

        SaveFile saveFile = HttpUtils.saveTo(downloadFrom, downloadTo, ".");
        if (saveFile == null) {
            error("unable to download Nexial distro from " + downloadFrom);
            exitCode = ERR_DOWNLOAD_FAILED;
            return;
        }

        log("downloaded Nexial distro in approximately " + (saveFile.getElapsedTime() / 1000) + " seconds");

        File downloaded = saveFile.getFile();
        if (!downloaded.canRead() || downloaded.length() != saveFile.getBytes()) {
            error("downloaded Nexial distro cannot be read or was not saved correctly");
            exitCode = ERR_DOWNLOAD_SAVE_FAILED;
            return;
        }

        if (installTarget == null) { throw new IOException("unable to resolve Nexial installation directory"); }
        log("resolved Nexial installation directory as " + installTarget);
        if (!installTarget.exists()) {
            log("Nexial installation directory not found; creating...");
            if (!installTarget.mkdirs()) { throw new IOException("unable to create Nexial installation directory"); }
        }

        // if (backupTarget == null) { throw new IOException("unable to resolve Nexial backup directory"); }
        if (backupTarget != null) {
            log("resolved Nexial backup directory as " + backupTarget);

            // remove BACKUP directory
            log("clean up previous backup directory (if exists)...");
            FileUtils.deleteDirectory(backupTarget);

            log("backing up current Nexial installation...");
            FileUtils.moveDirectory(installTarget, backupTarget);
            if (!installTarget.mkdirs()) { throw new IOException("unable to recreate Nexial installation directory"); }
        } else {
            // need to remove current install directory before we can unzip into it
            log("delete Nexial installation directory");
            FileUtils.deleteDirectory(installTarget);
            if (!installTarget.mkdirs()) { throw new IOException("unable to recreate Nexial installation directory"); }
        }

        // unzip distro
        log("unzipping Nexial distro to installation directory...");
        unzip(downloaded, installTarget);

        // fix exec permission
        log("setting permission bits on batch files...");
        FileUtils.listFiles(installTarget, new String[]{"sh", "bash", "bat", "cmd"}, true)
                 .forEach(file -> file.setExecutable(true, false));

        // add fingerprint
        log("adding fingerprint file...");
        createFingerprint(version, installTarget);

        // remove distro
        if (!keepDownloaded) {
            log("remove Nexial distro (zip)...");
            FileUtils.deleteQuietly(downloaded);
        } else {
            log("preserve Nexial distro (zip) to " + installTarget);
            FileUtils.moveFileToDirectory(downloaded, installTarget, false);
        }

        // spot check
        log("spot checks...");
        spotChecks(installTarget);

        log("installation for " + version + " completed");
    }

    protected static void unzip(File zip, File destination) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                File unzipTo = new File(destination, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    unzipTo.mkdirs();
                } else {
                    unzipTo.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(zipEntry);
                         FileOutputStream out = new FileOutputStream(unzipTo)) { IOUtils.copy(in, out); }
                }
            }
        }
    }

    protected static void createFingerprint(String manifest, File destination) throws IOException {
        FileUtils.writeStringToFile(new File(destination.getAbsolutePath() + separator + FINGERPRINT),
                                    manifest, UTF8);
    }

    protected static void spotChecks(File installTarget) {
        SPOT_CHECK_LIST.forEach(file -> {
            String startsWith = installTarget.getAbsolutePath() + separator + file;
            Collection<File> matches = FileUtils.listFiles(
                installTarget,
                new IOFileFilter() {
                    @Override
                    public boolean accept(File file) { return file.getAbsolutePath().startsWith(startsWith); }

                    @Override
                    public boolean accept(File dir, String name) { return true;}
                },
                new DirectoryFileFilter() {
                    @Override
                    public boolean accept(File file) { return true; }
                });
            if (matches.size() > 0) { log("verified: " + matches.iterator().next()); }
        });
    }

    protected static File resolveNexialHome() { return resolveLocation(NEXIAL_HOME, NEXIAL_LOCATION_HANDLER); }

    protected static File resolveNexialHomeBackup() { return resolveLocation(NEXIAL_BAK, NEXIAL_LOCATION_HANDLER); }

    protected static File resolveDownloadLocation(String version) {
        return resolveLocation(version, DOWNLOAD_LOCATION_HANDLER);
    }

    protected static File resolveLocation(String base, PlatformSpecificLocationHandler handler) {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows")) { return handler.resolveForWindows(base); }
        if (os.startsWith("Linux") || os.startsWith("LINUX")) { return handler.resolveForLinux(base); }
        if (os.startsWith("Mac")) { return handler.resolveForMac(base); }
        throw new RuntimeException("Unknown OS: " + os);
    }

    protected static void exit(int returnCode) { System.exit(returnCode); }
}