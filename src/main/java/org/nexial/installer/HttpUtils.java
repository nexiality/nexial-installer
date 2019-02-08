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

import java.io.*;
import java.net.URL;
import java.net.UnknownHostException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static org.nexial.installer.Const.*;

public final class HttpUtils {
    private HttpUtils() {}

    protected static JsonElement getJson(String url) throws IOException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedInputStream in = new BufferedInputStream(is);
            String jsonText = readText(in);

            if (jsonText.startsWith("[") && jsonText.endsWith("]")) {
                return GSON.fromJson(jsonText, JsonArray.class);
            } else {
                return GSON.fromJson(jsonText, JsonObject.class);
            }
        } catch (UnknownHostException e) {
            throw toIOException(e);
        }
    }

    protected static String getText(String url) throws IOException {
        try (InputStream is = new URL(url).openStream()) {
            return readText(new BufferedInputStream(is));
        } catch (UnknownHostException e) {
            throw toIOException(e);
        }
    }

    protected static SaveFile saveTo(String url, File targetFile, String progress) throws IOException {
        try (InputStream is = new URL(url).openStream()) {
            return save(new BufferedInputStream(is), targetFile, progress);
        }
    }

    private static IOException toIOException(UnknownHostException e) {
        return new IOException("Host not found: " + e.getMessage() + ". Check your Internet connection and try again");
    }

    private static String readText(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) { sb.append((char) cp); }
        return sb.toString().trim();
    }

    private static String readText(InputStream in) throws IOException {
        if (in == null) { throw new IOException("input stream is null"); }

        // ByteArrayOutputStream out = null;

        // try {
        //     out = new ByteArrayOutputStream();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                buffer = new byte[BUFFER_SIZE];
            }

            return new String(out.toByteArray(), UTF8);
        }
        // } finally {
        //     if (out != null) {
        //         out.flush();
        //         out.close();
        //     }
        // }
    }

    private static SaveFile save(InputStream in, File saveTo, String progress) throws IOException {
        if (in == null) { throw new IOException("input stream is null"); }
        if (saveTo == null) { throw new IOException("invalid save-to location: null"); }

        // make sure directory exists
        saveTo.getParentFile().mkdirs();

        long now = System.currentTimeMillis();
        int totalBytesRead = 0;
        int showProgressAfter = SHOW_PROGRESS_PER_BYTE;
        int totalShowProgress = 0;

        try (FileOutputStream out = new FileOutputStream(saveTo)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);

                totalBytesRead += bytesRead;
                if (totalBytesRead > showProgressAfter) {
                    showProgressAfter += SHOW_PROGRESS_PER_BYTE;

                    if (progress != null && progress.length() > 0) {
                        System.out.print(progress);
                        totalShowProgress += 1;

                        if (totalShowProgress % LINE_WIDTH == 0) { System.out.println(); }
                    }
                }

                buffer = new byte[BUFFER_SIZE];
            }

            if (progress != null && progress.length() > 0) { System.out.println(); }

            SaveFile saveFile = new SaveFile();
            saveFile.setFile(saveTo);
            saveFile.setBytes(totalBytesRead);
            saveFile.setElapsedTime(System.currentTimeMillis() - now);
            return saveFile;
        }
    }
}
