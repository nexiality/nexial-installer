package org.nexial.installer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
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
 *
 * another design consideration is to keep this project as small as possible in terms of footprint (meaning: no or
 * little 3rd party dependencies) so that it is easier to distribute and to maintain.
 */
public class NexialInstaller {

    protected static final PlatformSpecificLocationHandler NEXIAL_LOCATION_HANDLER =
        new PlatformSpecificLocationHandler() {
            @Override
            public File resolveForWindows(String base) { return new File(PROJECT_BASE_WIN + base); }

            @Override
            public File resolveForLinux(String base) { return new File(PROJECT_BASE_NIX + base); }

            @Override
            public File resolveForMac(String base) { return resolveForLinux(base); }
        };
    protected static final PlatformSpecificLocationHandler DOWNLOAD_LOCATION_HANDLER =
        new PlatformSpecificLocationHandler() {
            @Override
            public File resolveForWindows(String base) { return new File(PROJECT_BASE_WIN + base + ".zip"); }

            @Override
            public File resolveForLinux(String base) { return new File(PROJECT_BASE_NIX + base + ".zip"); }

            @Override
            public File resolveForMac(String base) { return resolveForLinux(base); }
        };

    private static int exitCode;
    private static Properties props;
    private static Map<String, String> availableVersions;

    protected interface PlatformSpecificLocationHandler {
        File resolveForWindows(String base);

        File resolveForLinux(String base);

        File resolveForMac(String base);
    }

    public static void main(String[] args) {
        try {
            props = loadProps();

            if (args != null && args.length > 0) {
                handleCommand(args[0], args.length > 1 ? args[1] : null);
                exit(exitCode);
            } else {
                showMenu();
            }
        } catch (Throwable e) {
            error(e.getMessage());
            exit(ERR_UNKNOWN_EXCEPTION);
        }
    }

    protected static Properties loadProps() throws IOException {
        InputStream propResource = NexialInstaller.class.getResourceAsStream(INSTALLER_PROPS);
        Properties props = new Properties();
        props.load(propResource);
        return props;
    }

    protected static void showMenu() throws IOException {
        showBanner();
        showOptions();

        Scanner in = new Scanner(System.in);
        String input = in.nextLine();

        while (input != null && !input.equals(OPT_QUIT)) {
            input = input.trim();
            if (input.length() > 0) {
                if (OPT_QUIT.equals(input)) { break; }

                int splitIndex = input.indexOf(" ");
                String command = splitIndex == -1 ? input : input.substring(0, splitIndex);
                String version = splitIndex == -1 ? null : input.substring(splitIndex + 1);
                handleCommand(command, version);
            }

            System.out.println();
            showOptions();
            input = in.nextLine();
        }
    }

    protected static void showBanner() {
        System.out.println(repeatLine("-", LINE_WIDTH));
        System.out.println(padCenter("[", APP + " " + VERSION, "]", LINE_WIDTH));
        System.out.println(repeatLine("-", LINE_WIDTH));
    }

    protected static void showOptions() {
        System.out.println("OPTIONS:");
        System.out.println("\t" + OPT_LIST + "\t- list the Nexial versions currently available for download.");
        System.out.println("\t" + OPT_INSTALL + "\t- install the specified version or latest.");
        System.out.println("\t" + OPT_QUIT + "\t- exit.");
        System.out.print("COMMAND: ");
    }

    protected static void handleCommand(String command, String version) throws IOException {
        if (OPT_LIST.equals(command)) {
            showVersions();
            exitCode = 0;
            return;
        }

        if (OPT_INSTALL.equals(command)) {
            if (StringUtils.isBlank(version)) {
                showError("Please specify either latest or a specific version to install");
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

    protected static void showVersions() throws IOException {
        if (MapUtils.isEmpty(availableVersions)) { availableVersions = listAvailableVersions(); }
        availableVersions.keySet().forEach(System.out::println);
    }

    // protected static List<String> listAvailableVersions() throws IOException {
    //     JsonArray assets = downloadAvailableVersions();
    //
    //     List<String> versions = new ArrayList<>();
    //     for (int i = 0; i < assets.size(); i++) {
    //         JsonObject asset = assets.get(i).getAsJsonObject();
    //         versions.add(asset.get("tag_name").getAsString());
    //     }
    //     return versions;
    // }

    // protected static JsonArray downloadAvailableVersions() throws IOException {
    //     JsonElement response = HttpUtils.getJson(props.getProperty("nexial.versions.url"));
    //     if (response == null || !response.isJsonArray()) {
    //         throw new RuntimeException("Expected JSON format not found via ${nexial.versions.url}");
    //     }
    //
    //     JsonArray assets = (JsonArray) response;
    //     if (assets.size() < 1) { throw new RuntimeException("Expected JSON found empty via ${nexial.versions.url}"); }
    //     return assets;
    // }

    protected static Map<String, String> listAvailableVersions() throws IOException {
        String versionUrl = props.getProperty("nexial.versions.url");
        if (StringUtils.isBlank(versionUrl)) { throw new IOException("${nexial.versions.url} not configured!"); }

        Map<String, String> versions = new TreeMap<>(Comparator.reverseOrder());

        if (StringUtils.contains(versionUrl, "amazonaws")) {
            // html treatment
            String html = HttpUtils.getText(versionUrl);
            if (StringUtils.isBlank(html)) {
                throw new RuntimeException("Expected HTML content not found via ${nexial.version.url}");
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
        } else {
            // github json
            JsonElement response = HttpUtils.getJson(versionUrl);
            if (response == null || !response.isJsonArray()) {
                throw new RuntimeException("Expected JSON content not found via ${nexial.versions.url}");
            }

            JsonArray assets = (JsonArray) response;
            if (assets.size() < 1) {
                throw new RuntimeException("Expected JSON structure not found via ${nexial.versions.url}");
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
        }

        return versions;
    }

    protected static String resolveDownloadUrl(String version) throws IOException {
        if (MapUtils.isEmpty(availableVersions)) { availableVersions = listAvailableVersions(); }
        if (availableVersions.containsKey(version)) { return availableVersions.get(version); }
        throw new IOException("Unable to resolve download URL for version " + version);
    }

    protected static void installLatest() throws IOException { install(VER_LATEST); }

    protected static void install(String version) throws IOException {
        if (StringUtils.isBlank(version)) {
            error("No version specified.");
            return;
        }

        if (MapUtils.isEmpty(availableVersions)) {
            availableVersions = listAvailableVersions();
            if (MapUtils.isEmpty(availableVersions)) {
                error("No versions available for Nexial");
                return;
            }
        }

        version = version.trim();
        if (VER_LATEST.equals(version)) {
            version = IterableUtils.get(availableVersions.keySet(), 0);
        } else {
            if (!availableVersions.containsKey(version)) {
                error("Specified version not found or not available");
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

        File installTarget = resolveNexialHome();
        if (installTarget == null) { throw new IOException("unable to resolve Nexial installation directory"); }
        log("resolved Nexial installation directory as " + installTarget);

        File backupTarget = resolveNexialHomeBackup();
        if (backupTarget == null) { throw new IOException("unable to resolve Nexial backup directory"); }
        log("resolved Nexial backup directory as " + backupTarget);

        // remove BACKUP directory
        log("clean up previous backup directory (if exists)...");
        FileUtils.deleteDirectory(backupTarget);

        if (!installTarget.exists()) {
            log("Nexial installation directory not found; creating...");
            if (!installTarget.mkdirs()) { throw new IOException("unable to create Nexial installation directory"); }
        } else {
            log("backing up current Nexial installation...");
            FileUtils.moveDirectory(installTarget, backupTarget);
            if (!installTarget.mkdirs()) { throw new IOException("unable to recreate Nexial installation directory"); }
        }

        // unzip distro
        log("unzipping Nexial distro to installation directory...");
        unzip(downloaded, installTarget);

        // add fingerprint
        log("adding fingerprint file...");
        createFingerprint(version, installTarget);

        // remove distro
        log("remove Nexial distro (zip)...");
        FileUtils.deleteQuietly(downloaded);

        // spot check
        log("spot check...");
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
            String lookfor = installTarget.getAbsolutePath() + separator + file;
            Collection<File> matches = FileUtils.listFiles(
                installTarget,
                new IOFileFilter() {
                    @Override
                    public boolean accept(File file) { return file.getAbsolutePath().startsWith(lookfor); }

                    @Override
                    public boolean accept(File dir, String name) { return true;}
                },
                new DirectoryFileFilter() {
                    @Override
                    public boolean accept(File file) { return true; }
                });
            if (CollectionUtils.isNotEmpty(matches)) { log("verified: " + IterableUtils.get(matches, 0)); }
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