package org.nexial.installer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.SystemUtils.*;
import static org.nexial.installer.OutputUtils.error;
import static org.nexial.installer.OutputUtils.log;

public final class RuntimeUtils {

    public static boolean terminateInstance(long processId) {
        final List<String> commands;
        if (IS_OS_WINDOWS) {
             commands = Arrays.asList("C:\\Windows\\System32\\cmd.exe", "/C", "start", "\"\"", "taskkill", "/pid",
                                      processId + "", "/T", "/F");
        } else if (IS_OS_MAC_OSX || IS_OS_UNIX) {
            commands = Arrays.asList("kill", "-s", "QUIT", processId + "");
        } else {
            error("UNSUPPORTED OS: " + OS_NAME + ". No termination for " + processId);
            return false;
        }

        return terminateInstanceById(processId, commands);

    }

    protected static boolean terminateInstanceById(long processId, List<String> command){
        try {
            log("terminating process with process id " + processId);

            ProcessBuilder pb = new ProcessBuilder(command).inheritIO();
            pb.start();

            return true;
        } catch (IOException e) {
            error("Unable to terminate process with process id " + processId + ": " + e.getMessage());
            return false;
        }
    }

}
