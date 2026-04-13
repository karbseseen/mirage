package util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Manifest;


public class JavaUtil {

    public static final File jarFile;
    public static final FileLock lock;

    static {
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


    static final LazyVal<File> tempFile = new LazyVal<>(() -> {
        File file = new File(jarFile.getParentFile(), "lib/half-downloaded-lib.part");
        file.deleteOnExit();
        return file;
    });

    public static void downloadWithProgress(
        InputStream input,
        File outputFile,
        Consumer<Long> onProgress,
        long progressPeriod
    ) throws IOException {
        File partOutputFile = tempFile.get();

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
        ProcessHandle.Info info  = ProcessHandle.current().info();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(info.command().orElseThrow());
        Collections.addAll(cmd, ProcessHandle.current().info().arguments().orElseThrow());
        return cmd;
    }
    
    public static void startNewInstance(ArrayList<String> cmdList) throws IOException {
        String[] cmd = new String[cmdList.size()];
        for (int index = 0; index < cmdList.size(); index++)
            cmd[index] = cmdList.get(index);

        lock.release();
        Runtime.getRuntime().exec(cmd);
    }
    public static void startNewInstance() throws IOException { startNewInstance(currentCmd()); }


    public static class LazyVal<T> implements Supplier<T> {
        Supplier<T> supplier;
        T result;

        public LazyVal(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public T get() {
            if (supplier != null) {
                result = supplier.get();
                supplier = null;
            }
            return result;
        }
    }

}
