package core.boot;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import util.JavaUtil;
import util.SystemInfo;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static util.JavaUtil.deleteFileArgPrefix;


class Bootstrap extends DependencyFactory {

    private final Out out = Out.create();
    private ArrayList<File> jars = new ArrayList<>();
    private ArrayList<File> natives = new ArrayList<>();
    private Thread afterLaunchTask = null;
    private Bootstrap() throws URISyntaxException { super(); }

    public static void main(String[] args) throws URISyntaxException {
        new Bootstrap().run(args);
    }

    private void run(String[] args) {
        try {
            try (FileLock _ = new FileLock()) { runInner(args); }

            String updateJarPath = System.getProperty("updateJarPath");
            if (updateJarPath != null) updateJar(updateJarPath);
        } catch (Exception error) {
            if (afterLaunchTask != null) afterLaunchTask.interrupt();

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
    }

    private void runInner(String[] args) throws IOException, URISyntaxException, ReflectiveOperationException {
        for (String rawUrl : readResourceLines("/libUrl.txt"))
            ensureJar(rawUrl);
        for (String rawUrl : readResourceLines("/libUrlSpecial.txt"))
            ensureNativeLibrary(rawUrl);
        ensureBundledLibrary("portaudio");
        if (SystemInfo.os == SystemInfo.OS.Windows) ensureBundledLibrary("libwinpthread-1");

        List<String> filesToDelete = new ArrayList<>();
        for (String arg : args)
            if (arg.startsWith(deleteFileArgPrefix))
                filesToDelete.add(arg.substring(deleteFileArgPrefix.length()));

        Set<File> dependencyFiles = new HashSet<>();
        dependencyFiles.addAll(jars);
        dependencyFiles.addAll(natives);
        try (var stream = Files.walk(libDir.toPath())) {
            stream.forEach(path -> {
                if (Files.isRegularFile(path) && !dependencyFiles.contains(path.toFile()))
                    filesToDelete.add(path.toString());
            });
        }

        afterLaunchTask = new Thread(() -> {
            try { Thread.sleep(4000); }
            catch (InterruptedException e) { return; }

            out.close();
            for (String file : filesToDelete) new File(file).delete();
        });
        afterLaunchTask.start();

        URL[] urls = new URL[jars.size() + 1];
        for (int index = 0; index < jars.size(); index++)
            urls[index] = jars.get(index).toURI().toURL();
        urls[urls.length - 1] = JavaUtil.getJarFileURL();

        jars = natives = null;
        ClassLoader loader = new URLClassLoader(urls, null);
        Thread.currentThread().setContextClassLoader(loader);
        Method mainApp = loader
            .loadClass("core.main.MainApp")
            .getMethod("main", String[].class);
        out.close();
        mainApp.invoke(null, (Object) new String[0]);
    }

    private void updateJar(String newJarPath) throws IOException, URISyntaxException {
        File thisJar = JavaUtil.getJarFile();
        String thisJarName = thisJar.getName();
        String updaterJarName = (
            thisJarName.endsWith(".jar") ?
            thisJarName.substring(0, thisJarName.length() - 4) :
            thisJarName
        ) + "-updater.jar";
        File updaterFile = new File(thisJar.getParentFile(), updaterJarName);

        String updateCls = JarUpdater.class.getName();
        String updaterPath = updateCls.replace('.', '/') + ".class";

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, updateCls);

        try (
            var input = getClass().getResourceAsStream("/" + updaterPath);
            var output = new JarOutputStream(new FileOutputStream(updaterFile), manifest);
        ) {
            if (input == null) throw new NullPointerException("Couldn't find source " + updaterPath);
            output.putNextEntry(new ZipEntry(updaterPath));
            input.transferTo(output);
        }

        ArrayList<String> currentCmd = this.currentCmd();
        ArrayList<String> updaterCmd = new ArrayList<>();
        updaterCmd.add(currentCmd.getFirst());
        updaterCmd.add("-jar");
        updaterCmd.add(updaterFile.getAbsolutePath());
        updaterCmd.add(thisJar.getAbsolutePath());
        updaterCmd.add(new File(newJarPath).getAbsolutePath());
        updaterCmd.addAll(currentCmd);
        updaterCmd.add(deleteFileArgPrefix + updaterFile.getAbsolutePath());

        Runtime.getRuntime().exec(updaterCmd.toArray(new String[0]));
    }

    private List<String> readResourceLines(String resourceName) throws IOException {
        var resource = getClass().getResourceAsStream(resourceName);
        if (resource == null) throw new MissingResourceException(resourceName + " not found", getClass().getName(), resourceName);
        try (var input = new BufferedReader(new InputStreamReader(resource))) {
            return input.readAllLines();
        }
    }

    private void ensureJar(String rawUrl) throws IOException, URISyntaxException {
        Dependency dependency = new JarDependency(rawUrl);
        jars.add(dependency.file);
        if (dependency.file.isFile()) return;

        File directory = dependency.file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs())
            throw new RuntimeException("Couldn't create " + directory);

        URLConnection connection = new URI(dependency.url).toURL().openConnection();
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            downloadAndPrint(dependency.url, input, dependency.file, connection.getContentLengthLong());
        }
    }

    private void ensureNativeLibrary(String rawUrl) throws IOException, URISyntaxException {
        NativeDependency dependency = new NativeDependency(rawUrl);
        if (dependency.lib == null) throw new RuntimeException("Invalid special url: " + rawUrl);
        dependency.setProperty();
        natives.add(dependency.file);
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
        natives.add(file);
        if (file.exists()) return;

        try (
            InputStream input = getClass().getResourceAsStream("/lib/" + file.getName());
            FileOutputStream output = new FileOutputStream(file);
        ) {
            input.transferTo(output);
        }
    }

    private void downloadAndPrint(String url, InputStream input, File destination, long totalSize) throws IOException {
        Consumer<Long> print = totalRead -> {
            if (out.isClosed()) throw new RuntimeException("Closed");
            else out.printTemp(url + " - " + (totalRead * 100 / totalSize) + "%");
        };
        downloadWithProgress(input, destination, print, 40);
        out.println(url + " - Done");
    }

    private void downloadWithProgress(
        InputStream input,
        File outputFile,
        Consumer<Long> onProgress,
        long progressPeriod
    ) throws IOException {
        File partOutputFile = new File("lib/half-downloaded-lib.part");
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

    private ArrayList<String> currentCmd() {
        if (SystemInfo.os == SystemInfo.OS.Windows) return windowsCmd();
        ProcessHandle.Info info = ProcessHandle.current().info();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(info.command().orElseThrow());
        Collections.addAll(cmd, ProcessHandle.current().info().arguments().orElseThrow());
        return cmd;
    }

    private ArrayList<String> windowsCmd() {
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

}
