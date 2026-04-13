package core.main;

import util.JavaUtil;
import util.JavaPlatform;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;


public class Main {
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
        if (new Main().check(args)) MainApp.main(args);
    }


    Out out = System.console() == null ? new CustomOut() : new StdOut();
    File libtorrentFile = new File(JavaUtil.jarFile.getParentFile(),
        "lib/jlibtorrent-" + JavaPlatform.jlibtorrentVersion + JavaPlatform.jlibtorrentExt);
    private Main() {}

    private boolean check(String[] args) throws Exception {
        if (JavaUtil.lock == null) {
            out.print("Mirage is already running");
            return false;
        }

        System.setProperty("jlibtorrent.jni.path", libtorrentFile.getAbsolutePath());

        for (String arg : args)
            if (arg.equals(Arg.depsOk))
                return true;

        Manifest manifest = JavaUtil.getManifest();
        Attributes attrs = manifest.getMainAttributes();
        boolean needRestart = false;

        File jarDirectory = JavaUtil.jarFile.getParentFile();
        String[] classPaths = attrs.getValue("Class-Path").split(" ");
        String[] classUrls = attrs.getValue("Class-Urls").split(" ");
        if (classPaths.length > classUrls.length) throw new RuntimeException("Invalid manifest");

        for (int index = 0; index < classPaths.length; index++) {
            File file = new File(jarDirectory, classPaths[index]);
            if (!file.isFile()) {
                downloadFile(classUrls[index], file);
                needRestart = true;
            }
        }
        
        for (int index = classPaths.length; index < classUrls.length; index++) {
            String url = classUrls[index];
            if (url.contains("jlibtorrent")) {
                if (!libtorrentFile.isFile()) downloadLibtorrent(url, libtorrentFile);
            }
            else throw new RuntimeException("Invalid extra url: " + url);
        }

        out.close();
        if (needRestart) {
            ArrayList<String> cmd = JavaUtil.currentCmd();
            cmd.add(Arg.depsOk);
            JavaUtil.startNewInstance(cmd);
        }
        return !needRestart;
    }

    private void downloadLibtorrent(String rawUrl, File destination) throws IOException {
        String url = parseUrl(rawUrl);
        
        var input1 = new URL(url).openStream();
        var input2 = new BufferedInputStream(input1);
        var input3 = new JarInputStream(input2);
        try (var input = input3) {
            var entry = Stream.generate(() -> {
                try { return input.getNextJarEntry(); }
                catch (IOException e) { throw new RuntimeException(e); }
            })
                .takeWhile(Objects::nonNull)
                .filter(e -> e.getName().endsWith(JavaPlatform.jlibtorrentExt))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Couldn't find appropriate file in jlibtorrent platform jar"));
            downloadAndPrint(url, input, destination, entry.getSize());
        }
    }

    private void downloadFile(String rawUrl, File destination) throws IOException {
        String url = parseUrl(rawUrl);
        
        File directory = destination.getParentFile();
        if (directory != null) {
            directory.mkdirs();
            if (!directory.isDirectory())
                throw new RuntimeException("Couldn't create " + directory);
        }

        URLConnection connection = new URL(url).openConnection();
        try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
            downloadAndPrint(url, input, destination, connection.getContentLengthLong());
        }
    }
    
    private void downloadAndPrint(String url, InputStream input, File destination, long totalSize) throws IOException {
        Consumer<Long> print = totalRead -> out.print(url + " - " + (totalRead * 100 / totalSize) + "%", true);
        JavaUtil.downloadWithProgress(input, destination, print);
        out.print(url + " - Done", false);
    }
    
    private String parseUrl(String rawUrl) {
        if (rawUrl.contains("-platform")) {
            if (rawUrl.contains("javafx"))
                return rawUrl.replaceAll("-platform", "-" + JavaPlatform.javafxPlatform);
            if (rawUrl.contains("jlibtorrent"))
                return rawUrl.replaceAll("-platform", "-" + JavaPlatform.jlibtorrentPlatform);
        }
        return rawUrl;
    }
}
