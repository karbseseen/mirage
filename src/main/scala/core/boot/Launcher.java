package core.boot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;


public class Launcher {

    public static volatile boolean restart = false;

    private static volatile Loader loader;
    public static void loadJar(File jarFile) throws MalformedURLException {
        loader.addDependency(jarFile);
    }

    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    public static void runAfterClassLoaderClose(Runnable listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private Launcher() {}

    public static void main(String[] args) throws IOException, ReflectiveOperationException {
        File appFile;
        try (InputStream input = Launcher.class.getResourceAsStream("/mainJarFileName.txt")) {
            assert input != null;
            appFile = new File(new String(input.readAllBytes()));
        }

        do {
            try (Loader loader = new Loader(appFile)) {
                Launcher.loader = loader;
                Thread.currentThread().setContextClassLoader(loader);

                Class<?> cls = loader.loadClass("core.boot.Bootstrap");
                Runnable app = (Runnable) cls.getDeclaredConstructor().newInstance();
                app.run();
            } finally {
                synchronized (listeners) {
                    listeners.forEach(Runnable::run);
                    listeners.clear();
                }
            }
        } while (restart);
    }

    private static class Loader extends URLClassLoader {
        Loader(File mainJar) throws MalformedURLException {
            super(new URL[]{ mainJar.toURI().toURL() });
        }
        void addDependency(File dependency) throws MalformedURLException {
            addURL(dependency.toURI().toURL());
        }
    }
}
