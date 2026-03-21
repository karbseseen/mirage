import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.Manifest;


public class Main {
    private static class Arg {
        static final String depsOk = "no-dep-check";
    }
    private static final String mavenPath = "lib/maven2/";
    private static final String platformSuffix = "-platform.jar";
    private static final long printPeriod = 100;


    private interface Out {
        void print(String line, boolean isTemp);
    }
    private static class StdOut implements Out {
        @Override public void print(String line, boolean isTemp) {
            System.out.print(line + (isTemp ? "\r" : System.lineSeparator()));
        }
    }
    private static class CustomOut implements Out {
        private boolean overrideLastLine = false;
        DefaultListModel<String> model = null;
        @Override public void print(String line, boolean isTemp) {
            if (model == null) {
                model = new DefaultListModel<>();

                JList<String> list = new JList<>(model);
                list.setBackground(new Color(32, 0, 32));
                list.setForeground(new Color(205, 205, 205));
                list.setFont(new Font(Font.DIALOG, Font.BOLD, 15));

                JFrame frame = new JFrame();
                frame.add(new JScrollPane(list));
                frame.setSize(1200, 400);
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
            if (overrideLastLine) model.setElementAt(line, model.size() - 1);
            else model.addElement(line);
            overrideLastLine = isTemp;
        }
    }


    public static void main(String[] args) throws Exception {
        new Main().check(args);
        MainApp.main(args);
    }


    File jarDirectory;
    String os;
    Out out;
    private Main() {}

    private void check(String[] args) throws Exception {
        for (String arg : args)
            if (arg.equals(Arg.depsOk))
                return;

        URI jarFilePath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        jarDirectory = new File(jarFilePath).getParentFile();
        
        String sysOs = System.getProperty("os.name").toLowerCase();
        if (sysOs.contains("win")) os = "win";
        else if (sysOs.contains("mac")) os = "mac";
        else if (sysOs.contains("nix") || sysOs.contains("nux") || sysOs.contains("aix")) os = "linux";
        else throw new Exception("Unknown OS");

        out = System.console() == null ? new CustomOut() : new StdOut();

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> manifests = loader.getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements())
            try (InputStream manifest = manifests.nextElement().openStream()) {
                if (check(new Manifest(manifest)))
                    break;
            }
    }

    private boolean check(Manifest manifest) throws Exception {
        Attributes attrs = manifest.getMainAttributes();
        if (!Objects.equals(attrs.getValue("Implementation-Title"), "mirage")) return false;
        boolean needRestart = false;

        String[] classPaths = attrs.getValue("Class-Path").split(" ");
        int platformPathNum = Integer.parseInt(attrs.getValue("Platform-Path-Num"));
        for (int index = 0; index < classPaths.length; index++) {
            String path = classPaths[index];
            File file = new File(jarDirectory, path);
            if (!file.isFile()) {
                if (!path.startsWith(mavenPath)) throw new Error("Invalid Class-Path entry: " + path);
                String url = "https://repo1.maven.org/maven2/" + path.substring(mavenPath.length());
                
                if (index < platformPathNum) {
                    if (!url.endsWith(platformSuffix)) throw new Error("Invalid platform Class-Path entry: " + path);
                    url = url.substring(0, url.length() - platformSuffix.length()) + "-" + os + ".jar";
                }
                
                downloadFile(url, file);
                needRestart = true;
            }
        }

        if (needRestart) {
            ArrayList<String> cmdList = currentCmd();
            cmdList.add(Arg.depsOk);
            
            String[] cmd = new String[cmdList.size()];
            for (int index = 0; index < cmdList.size(); index++)
                cmd[index] = cmdList.get(index);
            
            Runtime.getRuntime().exec(cmd);
            System.exit(0);
        }

        return true;
    }

    private ArrayList<String> currentCmd() {
        ProcessHandle.Info info  = ProcessHandle.current().info();
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(info.command().orElseThrow());
        Collections.addAll(cmd, ProcessHandle.current().info().arguments().orElseThrow());
        return cmd;
    }

    private void downloadFile(String url, File destination) throws Exception {
        File directory = destination.getParentFile();
        if (directory != null) {
            directory.mkdirs();
            if (!directory.isDirectory()) throw new Exception("Couldn't create " + directory);
        }
        File destinationPart = new File(destination.getAbsolutePath() + ".part");

        URLConnection connection = new URL(url).openConnection();
        BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
        FileOutputStream output = new FileOutputStream(destinationPart);
        byte[] buffer = new byte[1024 * 8];

        long totalRead = 0, totalSize = connection.getContentLengthLong();
        long lastPrintTime = 0;
        while (true) {
            long time = System.currentTimeMillis();
            if (time - lastPrintTime > printPeriod) {
                out.print(url + " - " + (totalRead * 100 / totalSize) + "%", true);
                lastPrintTime = time;
            }

            int currentRead = input.read(buffer);
            if (currentRead == -1) break;
            output.write(buffer, 0, currentRead);
            totalRead += currentRead;
        }
        out.print(url + " - Done", false);

        input.close();
        output.close();

        destinationPart.renameTo(destination);
    }
}
