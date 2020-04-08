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

import java.util.Arrays;
import java.util.Iterator;
import java.util.StringJoiner;

import static org.nexial.installer.Const.*;

public class CommandLineOptions {
    private boolean listOnly;
    private String version;
    private String installTarget;
    private String backupTarget;
    private boolean keepDownloaded;

    private CommandLineOptions() {}

    public static CommandLineOptions newInstance(String[] args) {
        if (args == null || args.length < 1) { throw new NullPointerException("No arguments found"); }

        // e.g. -install [version] -target [dir] -backup [dir] -keepDownloaded
        CommandLineOptions options = new CommandLineOptions();

        Iterator<String> argIterator = Arrays.stream(args).iterator();
        argIterator.forEachRemaining(option -> {
            switch ("-" + option) {
                case OPT_LIST:
                case OPT_LIST_L:{
                    options.setListOnly(true);
                    break;
                }
                case OPT_INSTALL:
                case OPT_INSTALL_I: {
                    if (!argIterator.hasNext()) { throw new IllegalArgumentException("No version specified"); }
                    options.setVersion(argIterator.next());
                    break;
                }
                case OPT_TARGET:
                case OPT_TARGET_T: {
                    if (!argIterator.hasNext()) { throw new IllegalArgumentException("No target directory specified"); }
                    options.setInstallTarget(argIterator.next());
                    break;
                }
                case OPT_BACKUP:
                case OPT_BACKUP_B: {
                    if (!argIterator.hasNext()) { throw new IllegalArgumentException("No backup directory specified"); }
                    options.setBackupTarget(argIterator.next());
                    break;
                }
                case OPT_KEEP_DOWNLOADED:
                case OPT_KEEP_DOWNLOADED_KD: {
                    options.setKeepDownloaded(true);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown commandline argument: " + option);
                }
            }
        });

        return options;
    }

    public String getVersion() { return version;}

    public void setVersion(String version) { this.version = version;}

    public String getInstallTarget() { return installTarget;}

    public void setInstallTarget(String installTarget) { this.installTarget = installTarget;}

    public String getBackupTarget() { return backupTarget;}

    public void setBackupTarget(String backupTarget) { this.backupTarget = backupTarget;}

    public boolean isKeepDownloaded() { return keepDownloaded;}

    public void setKeepDownloaded(boolean keepDownloaded) { this.keepDownloaded = keepDownloaded;}

    public boolean isListOnly() { return listOnly;}

    public void setListOnly(boolean listOnly) { this.listOnly = listOnly;}

    @Override
    public String toString() {
        return new StringJoiner(", ", "[", "]")
                   .add("version='" + version + "'")
                   .add("installTarget='" + installTarget + "'")
                   .add("backupTarget='" + backupTarget + "'")
                   .add("keepDownloaded=" + keepDownloaded)
                   .toString();
    }
}
