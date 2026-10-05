package util;

import java.io.File;
import java.net.URISyntaxException;


public class JavaUtil {
    public static final String appName = "Mirror";
    public static File getJarFile() throws URISyntaxException {
        return new File(JavaUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
