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

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static java.io.File.separator;

final class Const {

    protected static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();
    protected static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");

    protected static final String INSTALLER_PROPS = "/" + NexialInstaller.class.getSimpleName() + ".properties";
    protected static final String PROJECT_BASE = "projects";
    protected static final String PROJECT_BASE_WIN = "C:" + separator + PROJECT_BASE + separator;
    protected static final String PROJECT_BASE_NIX = System.getProperty("user.home") + separator +
                                                     PROJECT_BASE + separator;
    protected static final String NEXIAL_HOME = "nexial-core";
    protected static final String NEXIAL_BAK = "nexial-core.BAK";
    protected static final String FINGERPRINT = "version.txt";
    // check for file existence using "start-with" strategy
    protected static final List<String> SPOT_CHECK_LIST = Arrays.asList("bin" + separator + "nexial.cmd",
                                                                        "bin" + separator + "nexial.sh",
                                                                        "lib" + separator + "nexial-seeknow",
                                                                        "lib" + separator + "nexial-core",
                                                                        "template" + separator + "nexial-data.xlsx",
                                                                        "template" + separator + "nexial-script.xlsx");

    protected static final Charset UTF8 = Charset.forName("UTF-8");
    protected static final String APP = "Nexial Installer";
    protected static final String VERSION = "v1.4";

    protected static final int LINE_WIDTH = 80;
    protected static final int BUFFER_SIZE = 8192;
    // show progress per mb downloaded
    protected static final int SHOW_PROGRESS_PER_BYTE = 1024 * 1024;
    protected static final String REGEX_A_HREF_TAG = ".+<a href=\".+\".+>.+</a>";

    protected static final String OPT_LIST = "list";
    protected static final String OPT_INSTALL = "install";
    protected static final String OPT_QUIT = "quit";
    protected static final String VER_LATEST = "latest";

    protected static final int ERR_MISSING_VERSION = -2;
    protected static final int ERR_UNKNOWN_EXCEPTION = -13;
    protected static final int ERR_DOWNLOAD_FAILED = -3;
    protected static final int ERR_DOWNLOAD_SAVE_FAILED = -4;

    protected enum PadOption {LEFT, RIGHT, CENTER}

    private Const() { }
}
