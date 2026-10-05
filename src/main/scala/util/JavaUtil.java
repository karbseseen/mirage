package util;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;


public class JavaUtil {
    public static final String appName = "Mirror";

    public static URL getJarFileURL() {
        return JavaUtil.class.getProtectionDomain().getCodeSource().getLocation();
    }
    public static File getJarFile() throws URISyntaxException {
        return new File(getJarFileURL().toURI());
    }
}
