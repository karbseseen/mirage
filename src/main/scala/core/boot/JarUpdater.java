package core.boot;

import java.io.*;
import java.util.Arrays;


class JarUpdater {
    public static void main(String[] args) throws IOException {
        File oldJar = new File(args[0]);
        File newJar = new File(args[1]);
        String[] nextCmd = Arrays.copyOfRange(args, 2, args.length);

        oldJar.delete();
        newJar.renameTo(oldJar);
        Runtime.getRuntime().exec(nextCmd);
    }
}
