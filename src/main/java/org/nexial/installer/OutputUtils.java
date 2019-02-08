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

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.nexial.installer.Const.PadOption;

import static org.nexial.installer.Const.LINE_WIDTH;
import static org.nexial.installer.Const.LOG_DATE_FORMAT;
import static org.nexial.installer.Const.PadOption.*;

public class OutputUtils {

    protected static void showError(String message) {
        System.err.println();
        System.err.println("!-------------------------------- ERROR FOUND ---------------------------------!");
        System.err.println(padCenter("! ", message, " !", LINE_WIDTH));
        System.err.println("!" + repeatLine("-", LINE_WIDTH - 2) + "!");
    }

    protected static String pad(String leftBorder, String message, String rightBorder, int width, PadOption option) {
        if (leftBorder == null) { leftBorder = ""; }
        if (message == null) { message = ""; }
        if (rightBorder == null) { rightBorder = ""; }
        if (width < 1) { width = LINE_WIDTH; }

        int padLength = width - (leftBorder.length() + message.length() + rightBorder.length());

        StringBuilder buffer = new StringBuilder();
        int maxTextLengthPerLine = width - leftBorder.length() - rightBorder.length();
        String text = padLength > 0 ? message : message.substring(0, maxTextLengthPerLine);
        String reminder = padLength > 0 ? "" : message.substring(maxTextLengthPerLine);

        while (!text.isEmpty()) {
            String leftPadding = "";
            String rightPadding = "";
            if (text.length() < maxTextLengthPerLine) {
                if (option == CENTER) {
                    rightPadding = repeatLine(" ", (maxTextLengthPerLine - text.length()) / 2);
                    leftPadding = repeatLine(" ", maxTextLengthPerLine - text.length() - rightPadding.length());
                } else if (option == LEFT) {
                    leftPadding = repeatLine(" ", maxTextLengthPerLine - text.length());
                } else if (option == RIGHT) {
                    rightPadding = repeatLine(" ", maxTextLengthPerLine - text.length());
                }
            }

            buffer.append(leftBorder).append(leftPadding).append(text).append(rightPadding).append(rightBorder);

            if (reminder.equals("")) { break; }

            buffer.append("\n");
            if (reminder.length() > maxTextLengthPerLine) {
                text = reminder.substring(0, maxTextLengthPerLine);
                reminder = reminder.substring(maxTextLengthPerLine);
            } else {
                text = reminder;
                reminder = "";
            }
        }

        return buffer.toString();
    }

    protected static String padCenter(String leftBorder, String message, String rightBorder, int width) {
        return pad(leftBorder, message, rightBorder, width, CENTER);
    }

    protected static String padRight(String leftBorder, String message, String rightBorder, int width) {
        return pad(leftBorder, message, rightBorder, width, RIGHT);
    }

    protected static String repeatLine(String repeats, int width) {
        if (StringUtils.isBlank(repeats)) { repeats = " "; }
        if (width < 1) { width = LINE_WIDTH; }

        String line = "";
        while (line.length() < width) { line += repeats; }
        return line.substring(0, width);
    }

    protected static void log(String message) {
        if (StringUtils.isBlank(message)) { return; }
        System.out.println(LOG_DATE_FORMAT.format(new Date()) + "\t" + message);
    }

    protected static void error(String message) {
        if (StringUtils.isBlank(message)) { return; }
        System.err.println(LOG_DATE_FORMAT.format(new Date()) + "\t[ERROR] " + message);
    }
}
