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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.io.File.separator;
import static java.nio.channels.FileChannel.open;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.commons.lang3.SystemUtils.USER_HOME;
import static org.nexial.installer.Const.*;
import static org.nexial.installer.OutputUtils.*;
import static org.nexial.installer.RuntimeUtils.terminateInstance;

/**
 * this cmdline tool has 3 options:<li>
 * <li>list available versions of Nexial</li>
 * <li>install specific version of Nexial</li>
 * <li>install latest version of Nexial</li>
 * </li>
 * <p>
 * another design consideration is to keep this project as small as possible in terms of footprint (meaning: zero or
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

    static final String NEXIAL_DIR = resolveNexialDirPath();
    static final String NEXIAL_INSTALL_DIR = StringUtils.appendIfMissing(NEXIAL_DIR, separator) + "install" + separator;
    static final Path updateStatusFilePath = Paths.get(NEXIAL_INSTALL_DIR + "update.nx");

    private static int exitCode;
    private static Map<String, String> availableVersions = new TreeMap<>();

    protected interface PlatformSpecificLocationHandler {
        File resolveForWindows(String base);

        File resolveForLinux(String base);

        File resolveForMac(String base);
    }

    protected static void createInstallDirIfNotExist() {
        if (!Files.isDirectory(Paths.get(NEXIAL_INSTALL_DIR))) {
            try {
                Files.createDirectory(Paths.get(NEXIAL_INSTALL_DIR));
            } catch (IOException e) {
                error(String.format("Could not create %s directory", NEXIAL_INSTALL_DIR));
            }
        }
    }

    static {
        createInstallDirIfNotExist();
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

        while (input != null && !(OPT_QUIT.equalsIgnoreCase(input) || OPT_QUIT_Q.equalsIgnoreCase(input))) {
            input = input.trim();
            if (input.length() > 0) {
                if (OPT_QUIT.equalsIgnoreCase(input) || OPT_QUIT_Q.equalsIgnoreCase(input)) { break; }

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
        System.out.println("\t" + OPT_LIST + " (" + OPT_LIST_L + ")" +
                           "\t- list the Nexial versions currently available for download.");
        System.out.println(
            "\t" + OPT_INSTALL + " (" + OPT_INSTALL_I + ")" + "\t- install a specific version or latest.");
        System.out.println(
            "\t" + OPT_CONFIGURE + " (" + OPT_CONFIGURE_C + ")" + "\t- customize installation location.");
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

        if (cmdlineOptions.isSilentUpdate()) {
            checkForUpdates();
            return;
        }

        if (cmdlineOptions.isUpgrade()) {
            upgradeNexial();
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
        if (OPT_LIST.equalsIgnoreCase(command) || OPT_LIST_L.equalsIgnoreCase(command)) {
            showVersions();
            exitCode = 0;
            return;
        }

        if (OPT_CONFIGURE.equalsIgnoreCase(command) || OPT_CONFIGURE_C.equalsIgnoreCase(command)) {
            configure();
            exitCode = 0;
            return;
        }

        if (OPT_SILENT_UPDATE.equalsIgnoreCase(command) || OPT_SILENT_UPDATE_SU.equalsIgnoreCase(command)) {
            checkForUpdates();
            exitCode = 0;
            return;
        }

        if (OPT_UPGRADE_NEXIAL.equalsIgnoreCase(command) || OPT_UPGRADE_NEXIAL_UN.equalsIgnoreCase(command)) {
            upgradeNexial();
            exitCode = 0;
            return;
        }

        if (OPT_INSTALL.equalsIgnoreCase(command) || OPT_INSTALL_I.equalsIgnoreCase(command)) {
            if (StringUtils.isBlank(version)) {
                showError("Please specify either latest or a specific version to install");
                System.err.println("For example:");
                System.err.println("\t" + OPT_INSTALL + " latest or " + OPT_INSTALL_I + " latest");
                System.err.println(
                    "\t" + OPT_INSTALL + " nexial-core-v1.9_0400 or " + OPT_INSTALL_I + " nexial-core-v1.9_0400");
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

    protected static void checkForUpdates() {

        checkDuplicateProcess();

        String currentVersion = getCurrentVersionOfNexial();

        boolean isNetworkInstall = false;
        Path updateCheckDir = null;
        final Path installConfigPath = Paths.get(NEXIAL_INSTALL_DIR, "install.conf");
        if (Files.exists(installConfigPath)) {
            final Map<String, String> configs = getUpdateStatusProperties(installConfigPath);

            if (configs.containsKey("autoDownload")) {
                if (!Boolean.parseBoolean(configs.get("autoUpdate"))) {
                    log("Auto-download for nexial-core is disabled.");
                    return;
                }
            }

            if (configs.containsKey("updateFromDir")) {
                updateCheckDir = Paths.get(configs.get("updateFromDir"));
                isNetworkInstall = true;
            }

        }

        final Entry<String, String> latestVersionUrlMap = isNetworkInstall ? getLatestVersionMap(updateCheckDir)
                                                                           : getLatestVersionMap();

        final String latestVersion = latestVersionUrlMap.getKey();
        log("Latest version of nexial is: " + latestVersion);

        if (!isUpdateStatusOld(latestVersion) || isLatestVersionStaged(latestVersion)) {
            log("Aborting current check.");
            return;
        }

        final int currentBuildNumber = getBuildNumberFromVersion.apply(currentVersion);
        log("Current build number is: " + currentBuildNumber);

        final int latestBuildNumber = getBuildNumberFromVersion.apply(latestVersion);
        log("Latest build number is: " + latestBuildNumber);

        if (latestBuildNumber > currentBuildNumber) {
            log("New version of Nexial-Core is available for download & install.");

            String status = "lastCheckedAt=" + System.currentTimeMillis() + "\n" +
                            "currentVersionWas=" + currentVersion + "\n" +
                            "latestVersionFound=" + latestVersion + "\n" +
                            "currentBuildNumber=" + currentBuildNumber + "\n" +
                            "latestBuildNumber=" + latestBuildNumber + "\n" +
                            "isOnLatest=" + false + "\n" +
                            "downloadUrl=" + latestVersionUrlMap.getValue() + "\n";

            /* modify the installTarget to stage folder. */
            final Path stageDirLocation = Paths.get(NEXIAL_INSTALL_DIR + separator + latestVersion);

            try {
                deleteOldStageDirs(stageDirLocation);
            } catch (IOException e) {
                error("Could not re-create staged installation directory. Reason: " + e.getMessage());
                return;
            }

            installTarget = stageDirLocation.toFile();
            backupTarget = null;

            try {

                if (isNetworkInstall) {
                    FileUtils.copyDirectory(Paths.get(latestVersionUrlMap.getValue()).toFile(), installTarget);
                } else { install(latestVersion); }

                backupTarget = resolveNexialHomeBackup();
                log("resolved Nexial backup directory as " + backupTarget);

                log("clean up previous backup directory (if exists)...");
                if (backupTarget.exists()) { FileUtils.deleteQuietly(backupTarget); }

                Files.createDirectory(backupTarget.toPath());

                log("backing up current Nexial installation (safe copy)...");
                FileUtils.copyDirectory(resolveNexialHome(), backupTarget);
                status += "updateLocation=" + installTarget + "\n" +
                          "backupLocation=" + backupTarget + "\n" +
                          "downloadFinishedAt=" + System.currentTimeMillis();

                try {
                    Files.write(updateStatusFilePath, status.getBytes());
                } catch (IOException e) {
                    error("Could not update the update.nx file. Reason: " + e.getMessage());
                }
            } catch (IOException e) {
                error("Could not finish stage-installation of new nexial version. Reason: " + e.getMessage());
            }
        } else {
            log("Current installation of Nexial-Core is already on latest build.");
        }
    }

    protected static void upgradeNexial() {
        checkDuplicateProcess();

        Map<String, String> props = getUpdateStatusProperties(updateStatusFilePath);
        String updateLocation = props.get("updateLocation");

        if (Files.isDirectory(Paths.get(updateLocation))) {
            File currentNexial = resolveNexialHome();
            FileUtils.deleteQuietly(currentNexial);
            try {

                final Path targetPath = currentNexial.toPath(); // target
                final Path sourcePath = Paths.get(updateLocation); // source
                Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                        throws IOException {
                        Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                        throws IOException {
                        Files.copy(file, targetPath.resolve(sourcePath.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });

                FileUtils.deleteQuietly(sourcePath.toFile());
                // FileUtils.moveDirectory(Paths.get(updateLocation).toFile(), currentNexial);
                FileUtils.deleteQuietly(updateStatusFilePath.toFile());
            } catch (IOException e) {
                error("Failed to update the nexial-core. Stage update directory is not available.");
            }
            log("Nexial-Core Upgraded Successfully.");
        } else {
            error("Failed to update the nexial-core. Stage update directory is not available.");
        }
    }

    private static void deleteOldStageDirs(Path stageDirLocation) throws IOException {
        Files.list(Paths.get(NEXIAL_DIR, "install"))
             .map(Path::toFile)
             .filter(File::isDirectory)
             .filter(NexialInstaller::isStageDirectory)
             .forEach(FileUtils::deleteQuietly);
        Files.createDirectory(stageDirLocation);
    }

    private static boolean isStageDirectory(File file) {
        return file.getName().matches("nexial-core-v[0-9]+.[0-9]+_[0-9]+");
    }

    private static boolean isUpdateStatusOld(String latestVersion) {
        final long lastCheckTimeDiff = System.currentTimeMillis() - updateStatusFilePath.toFile().lastModified();

        if (lastCheckTimeDiff > Duration.ofDays(7).toMillis()) {
            log("Last update checked was more than 7 days ago. Deleting the older update file.");
            updateStatusFilePath.toFile().delete();
            return true;
        } else if (lastCheckTimeDiff < Duration.ofHours(6).toMillis()) {
            log("Last update was checked within 6 hours.");
        }
        return false;
    }

    private static boolean isLatestVersionStaged(String latestVersion) {
        final Map<String, String> updateProps = getUpdateStatusProperties(updateStatusFilePath);
        if (latestVersion.equalsIgnoreCase(updateProps.get("latestVersionFound"))) {
            if (Files.exists(Paths.get(updateProps.get("updateLocation")))) {
                log("Latest version is already staged and ready for installation.");
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> getUpdateStatusProperties(Path propertyFile) {
        try {
            return Files.readAllLines(propertyFile)
                        .stream().map(l -> l.split("="))
                        .collect(Collectors.toMap(s -> s[0], s -> s[1]));
        } catch (IOException e) {
            error("Could not extract property map from file. Reason: " + e.getMessage());
            return new HashMap<>();
        }
    }

    protected static String getCurrentVersionOfNexial() {
        try {
            String currentVersion =
                new String(Files.readAllBytes(Paths.get(installTarget.getAbsolutePath() + separator + FINGERPRINT)));
            currentVersion = currentVersion.trim().replaceAll("\n", "");
            log("Current version of nexial is: " + currentVersion);
            return currentVersion;
        } catch (IOException e) {
            error("Could not extract currentVersion of nexial-core installed. Reason : " + e.getMessage());
            exitCode = ERR_UNKNOWN_EXCEPTION;
            exit(exitCode);
            return null;
        }
    }

    protected static Entry<String, String> getLatestVersionMap() {
        try {
            availableVersions = listAvailableVersions();
            if (availableVersions.size() < 1) {
                error("Available version list is not available.");
                exitCode = ERR_DOWNLOAD_FAILED;
                exit(exitCode);
                return null;
            }
        } catch (IOException e) {
            error("Could not fetch available version list. Reason: " + e.getMessage());
            exitCode = ERR_DOWNLOAD_FAILED;
            exit(exitCode);
            return null;
        }

        return availableVersions.entrySet().stream().findFirst().get();
    }

    protected static Entry<String, String> getLatestVersionMap(Path dir) {
        try {
            int latestBuild = Files.list(dir)
                                   .map(Path::toFile)
                                   .filter(File::isDirectory)
                                   .filter(NexialInstaller::isStageDirectory)
                                   .map(File::getName)
                                   .mapToInt(getBuildNumberFromVersion::apply)
                                   .max().orElse(0);
            if (latestBuild > 0) {
                Optional<File> latestNexialDir =
                    Files.list(dir)
                         .map(Path::toFile)
                         .filter(NexialInstaller::isStageDirectory)
                         .filter(f -> getBuildNumberFromVersion.apply(f.getName()) == latestBuild)
                         .findFirst();
                if (latestNexialDir.isPresent()) {
                    return new AbstractMap.SimpleEntry(latestNexialDir.get().getName(),
                                                       latestNexialDir.get().getAbsolutePath());
                } else {
                    throw new IOException("No version present.");
                }
            } else {
                throw new IOException("No version present.");
            }
        } catch (IOException e) {
            error("Could not fetch available version list. Reason: " + e.getMessage());
            exitCode = ERR_DOWNLOAD_FAILED;
            exit(exitCode);
            return null;
        }
    }

    protected static final Function<String, Integer> getBuildNumberFromVersion =
        (version) -> Integer.parseInt(version.split("-v")[1].split("_")[1]);

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
                    public boolean accept(File dir, String name) { return true; }
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

    private static String resolveNexialDirPath() {
        return StringUtils.appendIfMissing(new File(USER_HOME).getAbsolutePath(), separator) + ".nexial";
        // return resolveLocation("", NEXIAL_LOCATION_HANDLER).getParent() + separator + ".nexial";
    }

    private static void checkDuplicateProcess() {
        final Path updateLock = Paths.get(NEXIAL_INSTALL_DIR + "update.lock");

        final String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        final long processId = Long.parseLong(processName.split("@")[0]);

        log("Current Process ID: " + processId);

        /* Check if any other instance running or not */
        if (Files.notExists(updateLock)) {
            try {
                Files.createFile(updateLock);
            } catch (IOException e) {
                error("Could not create lock file. Reason: " + e.getMessage());
            }
        }
        final File file = updateLock.toFile();
        try {
            FileLock lock = open(updateLock, CREATE, WRITE).tryLock(0, Long.MAX_VALUE, false);
            if (lock == null) {
                error("Unable to lock the file. Checking if last process became zombie or not...");
                final long lastModifiedTimeDiff = System.currentTimeMillis() - file.lastModified();

                if (lastModifiedTimeDiff > Duration.ofHours(8).toMillis()) {
                    log("Last running process had not completed withing 8 Hours since start. Trying to kill this zombie process..");
                    if (!terminateInstance(Long.parseLong(Files.readAllLines(updateLock).get(0)))) {
                        error("Unable to kill the zombie process. Aborting current process.");
                        exitCode = ERR_DUP_PROCESS;
                        exit(exitCode);
                        return;
                    }

                    /* Try to get lock on the lock file again. */
                    lock = open(updateLock, CREATE, WRITE).tryLock(0, Long.MAX_VALUE, false);
                    if (lock == null) {
                        throw new IOException("Lock cannot acquire.");
                    } else {
                        lock.channel().write(ByteBuffer.wrap((processId + "").getBytes()));
                        file.deleteOnExit();
                    }
                } else {
                    error("Another nexial update checker is already running. Aborting current process.");
                    exitCode = ERR_DUP_PROCESS;
                    exit(exitCode);
                }
            } else {
                lock.channel().write(ByteBuffer.wrap((processId + "").getBytes()));
                file.deleteOnExit();
            }
        } catch (IOException e) {
            error("Could not get hold of the lock file. Aborting the run. Reason: " + e.getMessage());
            exitCode = ERR_UNKNOWN_EXCEPTION;
            exit(exitCode);
        }
    }

    protected static void exit(int returnCode) { System.exit(returnCode); }
}