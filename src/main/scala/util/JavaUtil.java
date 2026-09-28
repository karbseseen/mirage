package util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.Manifest;


public class JavaUtil {

    public static final String appName = "Mirror";
    public static final FileLock lock;

    static {
        try {
            File lockFile = new File("lock");
            lockFile.deleteOnExit();
            FileChannel lockChannel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock(0, 0, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File getJarFile() throws URISyntaxException {
        return new File(JavaUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }


    public static Manifest getManifest() throws IOException {
        Enumeration<URL> manifests = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements())
            try (InputStream input = manifests.nextElement().openStream()) {
                Manifest manifest = new Manifest(input);
                if (Objects.equals(manifest.getMainAttributes().getValue("Implementation-Title"), "mirage"))
                    return manifest;
            }
        throw new RuntimeException(appName + " manifest not found");
    }


    static File tempFile() {
        File file = new File( "lib/half-downloaded-lib.part");
        file.deleteOnExit();
        return file;
    }

    public static void downloadWithProgress(
        InputStream input,
        File outputFile,
        Consumer<Long> onProgress,
        long progressPeriod
    ) throws IOException {
        File partOutputFile = tempFile();

        try (FileOutputStream output = new FileOutputStream(partOutputFile)) {
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

        if (!partOutputFile.renameTo(outputFile))
            throw new RuntimeException("Couldn't move file to " + outputFile.getAbsolutePath());
    }

    public static void downloadWithProgress(
        InputStream input,
        File outputFile,
        Consumer<Long> onProgress
    ) throws IOException {
        downloadWithProgress(input, outputFile, onProgress, 40);
    }


    public static ArrayList<String> currentCmd() {
        if (SystemInfo.os == SystemInfo.OS.Windows) return windowsCmd();
        ProcessHandle.Info info  = ProcessHandle.current().info();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(info.command().orElseThrow());
        Collections.addAll(cmd, ProcessHandle.current().info().arguments().orElseThrow());
        return cmd;
    }

    private static ArrayList<String> windowsCmd() {
        interface Kernel32 extends Library {
            Kernel32 Instance = Native.load("kernel32", Kernel32.class);
            Pointer GetCommandLineW();
            Pointer LocalFree(Pointer hMem);
        }
        interface Shell32 extends StdCallLibrary {
            Shell32 Instance = Native.load("shell32", Shell32.class);
            Pointer CommandLineToArgvW(Pointer lpCmdLine, IntByReference pNumArgs);
        }

        Pointer cmd = Kernel32.Instance.GetCommandLineW();
        IntByReference argc = new IntByReference();
        Pointer argv = Shell32.Instance.CommandLineToArgvW(cmd, argc);

        Pointer[] ptrs = argv.getPointerArray(0, argc.getValue());
        ArrayList<String> args = new ArrayList<>(argc.getValue());
        for (Pointer ptr : ptrs) args.add(ptr.getWideString(0));

        Kernel32.Instance.LocalFree(argv);
        return args;
    }

    public static void startNewInstance(ArrayList<String> cmdList) throws IOException {
        String[] cmd = new String[cmdList.size()];
        for (int index = 0; index < cmdList.size(); index++)
            cmd[index] = cmdList.get(index);

        lock.release();
        Runtime.getRuntime().exec(cmd);
    }

    public static void startNewInstance() throws IOException { startNewInstance(currentCmd()); }

}
