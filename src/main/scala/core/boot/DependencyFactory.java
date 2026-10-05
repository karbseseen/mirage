package core.boot;


import util.JavaUtil;
import util.LibInfo;
import util.SystemInfo;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;


class DependencyFactory {

    private final LibInfo[] libs = LibInfo.values();
    final File libDir = new File(JavaUtil.getJarFile().getParentFile(), "lib");

    DependencyFactory() throws URISyntaxException {
        Arrays.sort(libs, (a, b) -> b.urlKeyword.length() - a.urlKeyword.length());
    }

    sealed abstract class Dependency {
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

    final class JarDependency extends Dependency {
        JarDependency(String rawUrl) { super(rawUrl);  }

        private static final String[] urlPrefixes = {
            "https://repo1.maven.org/maven2/",
            "https://dl.frostwire.com/maven/",
        };

        @Override protected File getFile() {
            for (String prefix : urlPrefixes)
                if (url.startsWith(prefix))
                    return new File(libDir, url.substring(prefix.length()));
            throw new RuntimeException("Invalid jar url: " + url);
        }
    }

    final class NativeDependency extends Dependency {
        NativeDependency(String rawUrl) {  super(rawUrl); }

        @Override protected File getFile() {
            if (lib == null) return null;
            String[] urlParts = url.split("/");
            String version = urlParts[urlParts.length - 2];
            return new File(libDir, lib.urlKeyword + "-" + version + SystemInfo.sharedLibExt);
        }

        void setProperty() {
            if (lib != null && lib.property != null && file != null)
                System.setProperty(lib.property, file.getAbsolutePath());
        }
    }

}
