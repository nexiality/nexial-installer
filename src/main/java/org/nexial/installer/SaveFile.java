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

public class SaveFile {
    private File file;
    private long bytes;
    private long elapsedTime;

    public File getFile() { return file;}

    public void setFile(File file) { this.file = file;}

    public long getBytes() { return bytes;}

    public void setBytes(long bytes) { this.bytes = bytes;}

    public long getElapsedTime() { return elapsedTime;}

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime;}

    @Override
    public String toString() { return "file: " + file + ", bytes: " + bytes + ", elapsedTime: " + elapsedTime; }
}
