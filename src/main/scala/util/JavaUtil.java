package util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.Manifest;


public class JavaUtil {

    public enum OS { Windows, MacOS, Linux }

    public static final OS os;
    public static final File jarFile;
    public static final FileLock lock;

    static {
        String sysOs = System.getProperty("os.name").toLowerCase();
        if (sysOs.contains("win")) os = OS.Windows;
        else if (sysOs.contains("mac")) os = OS.MacOS;
        else if (sysOs.contains("nix") || sysOs.contains("nux") || sysOs.contains("aix")) os = OS.Linux;
        else throw new RuntimeException("Unknown OS");

        try {
            URI jarFilePath = JavaUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            jarFile = new File(jarFilePath);
            
            lock = FileChannel
                .open(jarFile.toPath(), StandardOpenOption.WRITE)
                .tryLock(0, 0, false);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Manifest getManifest() throws IOException {
        Enumeration<URL> manifests = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements())
            try (InputStream input = manifests.nextElement().openStream()) {
                Manifest manifest = new Manifest(input);
                if (Objects.equals(manifest.getMainAttributes().getValue("Implementation-Title"), "mirage"))
                    return manifest;
            }
        throw new RuntimeException("Mirage manifest not found");
    }


    public static void downloadWithProgress(
        InputStream input,
        OutputStream output,
        Consumer<Long> onProgress,
        long progressPeriod
    ) throws IOException {
        long totalRead = 0, lastProgressTime = 0;
        byte[] buffer = new byte[1024 * 8];
        while (true) {
            long time = System.currentTimeMillis();
            if (time - lastProgressTime > progressPeriod) {
                onProgress.accept(totalRead);
                lastProgressTime = time;
            }

            int currentRead = input.read(buffer);
            if (currentRead == -1) break;
            output.write(buffer, 0, currentRead);
            totalRead += currentRead;
        }
    }

    public static void downloadWithProgress(
        InputStream input,
        OutputStream output,
        Consumer<Long> onProgress
    ) throws IOException {
        downloadWithProgress(input, output, onProgress, 40);
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

        lock.release();
        Runtime.getRuntime().exec(cmd);
        System.exit(0);
        throw new AssertionError("Unreachable");
    }
    public static <T> T restart() throws IOException { return restart(currentCmd()); }
}
