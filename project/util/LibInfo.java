package util;


public enum LibInfo {
    javaFX("openjfx", javaFXPlatform()),
    jlibtorrent("jlibtorrent", "jlibtorrent.jni.path", libtorrentPlatform()),
    lwjgl("lwjgl", "org.lwjgl.libname", lwjglPlatform()),
    lwjglOpus("lwjgl-opus", "org.lwjgl.opus.libname", lwjgl.platform),
    webrtcAec("webrtc-aec3-kmp-jni", "webrtc.aec3.libpath", webrtcAecPlatform());

    private static String javaFXPlatform() {
        return switch (SystemInfo.os) {
            case Windows    -> "win";
            case MacOS      -> "mac";
            case Linux      -> "linux";
        };
    }
    
    private static String libtorrentPlatform() {
        String os = switch (SystemInfo.os) {
            case Windows    -> "windows";
            case MacOS      -> "macosx";
            case Linux      -> "linux";
        };
        String arch = SystemInfo.os == SystemInfo.OS.Windows ? "" : switch (SystemInfo.arch) {
            case X86_64     -> "-x86_64";
            case Arm64      -> "-arm64";
        };
        return os + arch;
    }

    private static String lwjglPlatform() {
        return switch (SystemInfo.os) {
            case Windows    -> "windows";
            case MacOS      -> "macos";
            case Linux      -> "linux";
        } +
        switch (SystemInfo.arch) {
            case X86_64 -> "";
            case Arm64 -> "-arm64";
        };
    }

    private static String webrtcAecPlatform() {
        return switch (SystemInfo.os) {
            case Windows    -> "windows";
            case MacOS      -> "darwin";
            case Linux      -> "linux";
        } +
        switch (SystemInfo.arch) {
            case X86_64 -> "-x86_64";
            case Arm64 -> "-aarch64";
        };
    }

    public final String urlKeyword, property, platform;
    LibInfo(String urlKeyword, String property, String platform) {
        this.urlKeyword = urlKeyword;
        this.property = property;
        this.platform = platform;
    }
    LibInfo(String urlKeyword, String platform) {
        this(urlKeyword, null, platform);
    }
}
