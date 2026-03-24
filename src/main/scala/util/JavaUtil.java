package util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;


public class JavaUtil {
    public static File getJarFile() throws URISyntaxException {
        URI jarFilePath = JavaUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        return new File(jarFilePath);
    }

    public static ArrayList<String> currentCmd() {
        ProcessHandle.Info info  = ProcessHandle.current().info();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(info.command().orElseThrow());
        Collections.addAll(cmd, ProcessHandle.current().info().arguments().orElseThrow());
        return cmd;
    }
    
    public static <T> T restart(ArrayList<String> cmdList) throws IOException {
        String[] cmd = new String[cmdList.size()];
        for (int index = 0; index < cmdList.size(); index++)
            cmd[index] = cmdList.get(index);
        Runtime.getRuntime().exec(cmd);
        System.exit(0);
        throw new AssertionError("Unreachable");
    }
    public static <T> T restart() throws IOException { return restart(currentCmd()); }
}
