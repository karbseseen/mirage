package core.boot;

import core.main.MainApp;
import util.JavaUtil;
import util.SystemInfo;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;


public class Bootstrap extends DependencyFactory implements Runnable {

    private final Out out = Out.create();
    public Bootstrap() throws URISyntaxException {}

    public static void main(String[] args) throws URISyntaxException {
        Out out = Out.create();

        String[] sourcePaths = {
            "core/boot/Launcher.class",
            "core/boot/Launcher$Loader.class",
        };

        File thisJar = JavaUtil.getJarFile();
        String thisJarName = thisJar.getName();
        String launcherJarName = (
            thisJarName.endsWith(".jar") ?
                thisJarName.substring(0, thisJarName.length() - 4) :
                thisJarName
        ) + "-launcher.jar";
        File outFile = new File(thisJar.getParentFile(), launcherJarName);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Launcher.class.getName());

        try (var output = new JarOutputStream(new FileOutputStream(outFile), manifest)) {
            output.putNextEntry(new ZipEntry("mainJarFileName.txt"));
            output.write(thisJarName.getBytes());

            for (String sourcePath : sourcePaths)
                try (var input = Bootstrap.class.getResourceAsStream("/" + sourcePath)) {
                    if (input == null) throw new NullPointerException("Couldn't find source " + sourcePath);
                    output.putNextEntry(new ZipEntry(sourcePath));
                    input.transferTo(output);
                }

            out.errPrintln("You need to launch " + launcherJarName + ", not " + thisJarName);
        } catch (IOException | NullPointerException error) {
            printError(out, error);
        }
    }

    public void run() {
        try (FileLock _ = new FileLock()) {
            ensureDependencies();
            MainApp.main(new String[]{});
        } catch (Exception error) {
            printError(out, error);
            throw new RuntimeException(error);
        }
    }

    private static void printError(Out out, Exception error) {
        String text;
        if (error instanceof StringException str)
            text = str.getMessage();
        else {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream print = new PrintStream(bytes);
            error.printStackTrace(print);
            print.flush();
            text = bytes.toString();
        }

        for (String line : text.split(System.lineSeparator()))
            out.errPrintln(line);
    }

    private void ensureDependencies() throws Exception {
        for (String rawUrl : readResourceLines("/libUrl.txt"))
            ensureJar(rawUrl);
        for (String rawUrl : readResourceLines("/libUrlSpecial.txt"))
            ensureNativeLibrary(rawUrl);
        ensureBundledLibrary("portaudio");
        if (SystemInfo.os == SystemInfo.OS.Windows) ensureBundledLibrary("libwinpthread-1");
        out.close();
    }

    private List<String> readResourceLines(String resource) throws IOException {
        var input1 = getClass().getResourceAsStream(resource);
        if (input1 == null) throw new MissingResourceException(resource + " not found", getClass().getName(), resource);
        var input2 = new InputStreamReader(input1);
        var input3 = new BufferedReader(input2);
        try (input3) {
            return input3.readAllLines();
        }
    }

    private void ensureJar(String rawUrl) throws IOException, URISyntaxException {
        Dependency dependency = new JarDependency(rawUrl);
        if (!dependency.file.isFile()) {
            File directory = dependency.file.getParentFile();
            if (directory != null && !directory.isDirectory() && !directory.mkdirs())
                throw new RuntimeException("Couldn't create " + directory);

            URLConnection connection = new URI(dependency.url).toURL().openConnection();
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                downloadAndPrint(dependency.url, input, dependency.file, connection.getContentLengthLong());
            }
        }
        Launcher.loadJar(dependency.file);
    }

    private void ensureNativeLibrary(String rawUrl) throws IOException, URISyntaxException {
        NativeDependency dependency = new NativeDependency(rawUrl);
        if (dependency.lib == null) throw new RuntimeException("Invalid special url: " + rawUrl);
        dependency.setProperty();
        if (dependency.file.isFile()) return;

        var input1 = new URI(dependency.url).toURL().openStream();
        var input2 = new BufferedInputStream(input1);
        var input3 = new JarInputStream(input2);
        try (var input = input3) {
            var entry = Stream.generate(() -> {
                try { return input.getNextJarEntry(); }
                catch (IOException e) { throw new RuntimeException(e); }
            })
                .takeWhile(Objects::nonNull)
                .filter(e -> e.getName().endsWith(SystemInfo.sharedLibExt))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Couldn't find appropriate file in " + dependency.lib.urlKeyword + " platform jar"));
            downloadAndPrint(dependency.url, input, dependency.file, entry.getSize());
        }
    }

    private void ensureBundledLibrary(String name) throws IOException {
        File file = new File(libDir, System.mapLibraryName(name));
        if (file.exists()) return;

        try (
            InputStream input = getClass().getResourceAsStream("/lib/" + file.getName());
            FileOutputStream output = new FileOutputStream(file);
        ) {
            input.transferTo(output);
        }
    }

    private void downloadAndPrint(String url, InputStream input, File destination, long totalSize) throws IOException {
        Consumer<Long> print = totalRead -> out.printTemp(url + " - " + (totalRead * 100 / totalSize) + "%");
        downloadWithProgress(input, destination, print, 40);
        out.println(url + " - Done");
    }

    public static void downloadWithProgress(
        InputStream input,
        File outputFile,
        Consumer<Long> onProgress,
        long progressPeriod
    ) throws IOException {
        File partOutputFile = new File( "lib/half-downloaded-lib.part");
        partOutputFile.deleteOnExit();

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

}
