package core.main;

import util.JavaUtil;
import util.LibInfo;
import util.SystemInfo;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;


public class Bootstrap {
    private static class Arg {
        static final String depsOk = "no-dep-check";
    }

    public static void main(String[] args) throws Exception {
        if (new Bootstrap(args).check()) MainApp.main(args);
    }


    final Out out = Out.create();
    final File jarDirectory = JavaUtil.getJarFile().getParentFile();
    final LibInfo[] libs;
    final boolean depsOk;
    boolean needRestart = false;

    private Bootstrap(String[] args) throws URISyntaxException {
        libs = LibInfo.values();
        Arrays.sort(libs, (a, b) -> b.urlKeyword.length() - a.urlKeyword.length());
        depsOk = Arrays.asList(args).contains(Arg.depsOk);
    }

    private boolean check() throws Exception {
        if (JavaUtil.lock == null) {
            out.println(JavaUtil.appName + " is already running");
            return false;
        }

        if (depsOk) {
            for (String rawUrl : readResourceLines("/libUrlSpecial.txt"))
                new NativeDependency(rawUrl).setProperty();
        } else {
            for (String rawUrl : readResourceLines("/libUrl.txt"))
                ensureJar(rawUrl);
            for (String rawUrl : readResourceLines("/libUrlSpecial.txt"))
                ensureNativeLibrary(rawUrl);
            ensureBundledLibrary("portaudio");
            if (SystemInfo.os == SystemInfo.OS.Windows) ensureBundledLibrary("libwinpthread-1");
        }

        out.close();
        if (needRestart) {
            ArrayList<String> cmd = JavaUtil.currentCmd();
            cmd.add(Arg.depsOk);
            JavaUtil.startNewInstance(cmd);
        }
        return !needRestart;
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
        if (dependency.file.isFile()) return;
        needRestart = true;

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
        String path = "/lib/" + System.mapLibraryName(name);
        File file = new File(jarDirectory, path);
        if (file.exists()) return;

        try (
            InputStream input = getClass().getResourceAsStream(path);
            FileOutputStream output = new FileOutputStream(file);
        ) {
            input.transferTo(output);
        }
    }

    private void downloadAndPrint(String url, InputStream input, File destination, long totalSize) throws IOException {
        Consumer<Long> print = totalRead -> out.printTemp(url + " - " + (totalRead * 100 / totalSize) + "%");
        JavaUtil.downloadWithProgress(input, destination, print);
        out.println(url + " - Done");
    }

    private sealed abstract class Dependency {
        final LibInfo lib;
        final String url;
        final File file;

        Dependency(String rawUrl) {
            lib = findLib(rawUrl);
            url = (lib == null) ? rawUrl : rawUrl.replaceAll("-platform", "-" + lib.platform);
            file = getFile();
        }

        private LibInfo findLib(String rawUrl) {
            for (LibInfo lib : libs)
                if (rawUrl.contains(lib.urlKeyword))
                    return lib;
            return null;
        }

        protected abstract File getFile();
    }

    private final class JarDependency extends Dependency {
        JarDependency(String rawUrl) {
            super(rawUrl);
        }

        private static final String[] urlPrefixes = {
            "https://repo1.maven.org/maven2/",
            "https://dl.frostwire.com/maven/",
        };

        @Override protected File getFile() {
            for (String prefix : urlPrefixes)
                if (url.startsWith(prefix))
                    return new File(jarDirectory, "lib/" + url.substring(prefix.length()));
            throw new RuntimeException("Invalid jar url: " + url);
        }
    }

    private final class NativeDependency extends Dependency {
        NativeDependency(String rawUrl) {
            super(rawUrl);
        }

        @Override protected File getFile() {
            if (lib == null) return null;
            String[] urlParts = url.split("/");
            String version = urlParts[urlParts.length - 2];
            return new File(jarDirectory, "lib/" + lib.urlKeyword + "-" + version + SystemInfo.sharedLibExt);
        }

        void setProperty() {
            if (lib != null && lib.property != null && file != null)
                System.setProperty(lib.property, file.getAbsolutePath());
        }
    }
}
