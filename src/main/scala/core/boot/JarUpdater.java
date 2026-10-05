package core.boot;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class JarUpdater {
    public static final String waitPidArgPrefix = "-waitPid:";
    public static final String deleteFileArgPrefix = "-delete:";

    public static String getMyPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }
    public static void waitForPid(String pid, long timeout) throws InterruptedException, ExecutionException, TimeoutException {
        ProcessHandle handle = ProcessHandle.of(Long.parseLong(pid)).orElse(null);
        if (handle != null) handle.onExit().get(timeout, TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) throws Exception {
        String prevPid = args[0];
        File oldJar = new File(args[1]);
        File newJar = new File(args[2]);

        String[] nextCmd = new String[args.length - 3 + 1];
        System.arraycopy(args, 3, nextCmd, 0, args.length - 3);
        nextCmd[nextCmd.length - 1] = waitPidArgPrefix + getMyPid();

        waitForPid(prevPid, 10_000);
        oldJar.delete();
        newJar.renameTo(oldJar);
        Runtime.getRuntime().exec(nextCmd);
    }
}
