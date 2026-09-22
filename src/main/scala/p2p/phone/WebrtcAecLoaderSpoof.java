package cn.enaium.webrtc.aec3;

import util.JavaUtil;
import util.LibInfo;
import util.SystemInfo;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;


class NativeLoader {
    private NativeLoader() {}
    static NativeLoader INSTANCE = new NativeLoader();
    void load() throws URISyntaxException {
        if (SystemInfo.os == SystemInfo.OS.Windows) {
            Path path = Paths.get(JavaUtil.getJarFile().getParent(), "lib", "libwinpthread-1.dll");
            System.load(path.toString());
        }
        System.load(System.getProperty(LibInfo.webrtcAec.property));
    }
}
