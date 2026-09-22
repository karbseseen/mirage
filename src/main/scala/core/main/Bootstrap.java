package core.main;

import util.JavaUtil;
import util.LibInfo;
import util.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;


public class Bootstrap {
    private static class Arg {
        static final String depsOk = "no-dep-check";
    }

    private interface Out {
        void print(String line, boolean isTemp);
        default void print(String line) { print(line, false); }
        default void close() {}
    }
    private static class StdOut implements Out {
        @Override public void print(String line, boolean isTemp) {
            System.out.print(line + (isTemp ? "\r" : System.lineSeparator()));
        }
    }
    private static class CustomOut implements Out {
        private boolean overrideLastLine = false;
        DefaultListModel<String> model = null;
        JFrame frame = null;
        @Override public void print(String line, boolean isTemp) {
            if (model == null) {
                model = new DefaultListModel<>();

                JList<String> list = new JList<>(model);
                list.setBackground(new Color(32, 0, 32));
                list.setForeground(new Color(205, 205, 205));
                list.setFont(new Font(Font.DIALOG, Font.BOLD, 15));

                frame = new JFrame();
                frame.add(new JScrollPane(list));
                frame.setSize(1200, 400);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setVisible(true);
            }
            if (overrideLastLine) model.setElementAt(line, model.size() - 1);
            else model.addElement(line);
            overrideLastLine = isTemp;
        }
        public void close() {
            if (frame != null) frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        }
    }


    public static void main(String[] args) throws Exception {
        if (new Bootstrap(args).check()) MainApp.main(args);
    }


    final Out out = System.console() == null ? new CustomOut() : new StdOut();
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
            out.print("Mirage is already running");
            return false;
        }

        Manifest manifest = JavaUtil.getManifest();
        Attributes attrs = manifest.getMainAttributes();

        String[] classPaths = attrs.getValue("Class-Path").split(" ");
        String[] classUrls = attrs.getValue("Class-Urls").split(" ");
        if (classPaths.length > classUrls.length) throw new RuntimeException("Invalid manifest");

        if (depsOk) {
            for (int index = classPaths.length; index < classUrls.length; index++)
                new ParsedUrl(classUrls[index]).setProperty();
        } else {
            for (int index = 0; index < classPaths.length; index++)
                ensureJar(classUrls[index], classPaths[index]);
            for (int index = classPaths.length; index < classUrls.length; index++)
                ensureNativeLibrary(classUrls[index]);
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

    private void ensureJar(String rawUrl, String path) throws IOException, URISyntaxException {
        File destination = new File(jarDirectory, path);
        if (destination.isFile()) return;
        needRestart = true;

        File directory = destination.getParentFile();
        if (directory != null) {
            directory.mkdirs();
            if (!directory.isDirectory())
                throw new RuntimeException("Couldn't create " + directory);
        }

        String url = new ParsedUrl(rawUrl).value;
        URLConnection connection = new URI(url).toURL().openConnection();
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            downloadAndPrint(url, input, destination, connection.getContentLengthLong());
        }
    }

    private void ensureNativeLibrary(String rawUrl) throws IOException, URISyntaxException {
        ParsedUrl parsed = new ParsedUrl(rawUrl);
        if (parsed.lib == null) throw new RuntimeException("Invalid extra url: " + rawUrl);
        parsed.setProperty();
        String url = parsed.value;
        if (parsed.nativeLibFile.isFile()) return;

        var input1 = new URI(url).toURL().openStream();
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
                .orElseThrow(() -> new RuntimeException("Couldn't find appropriate file in " + parsed.lib.urlKeyword + " platform jar"));
            downloadAndPrint(url, input, parsed.nativeLibFile, entry.getSize());
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
        Consumer<Long> print = totalRead -> out.print(url + " - " + (totalRead * 100 / totalSize) + "%", true);
        JavaUtil.downloadWithProgress(input, destination, print);
        out.print(url + " - Done", false);
    }

    class ParsedUrl {
        final String value;
        final LibInfo lib;
        final File nativeLibFile;

        ParsedUrl(String rawUrl) {
            if (rawUrl.contains("-platform"))
                for (LibInfo lib : libs)
                    if (rawUrl.contains(lib.urlKeyword)) {
                        value = rawUrl.replaceAll("-platform", "-" + lib.platform);
                        this.lib = lib;
                        nativeLibFile = getNativeLibFile();
                        return;
                    }
            value = rawUrl;
            lib = null;
            nativeLibFile = getNativeLibFile();
        }

        private File getNativeLibFile() {
            if (lib == null) return null;
            String[] urlParts = value.split("/");
            String version = urlParts[urlParts.length - 2];
            return new File(jarDirectory, "lib/" + lib.urlKeyword + "-" + version + SystemInfo.sharedLibExt);
        }

        void setProperty() {
            if (lib.property != null) System.setProperty(lib.property, nativeLibFile.getAbsolutePath());
        }
    }

}
