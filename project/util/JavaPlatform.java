package util;

import java.util.List;


public class JavaPlatform {
    public enum OS { Windows, MacOS, Linux }
    public enum Arch { X86_64, Arm64 }

    public static final String jlibtorrentVersion = "2.0.12.7";

    public static final OS os;
    public static final Arch arch;
    public static final String javafxPlatform;
    public static final String jlibtorrentPlatform;
    public static final String jlibtorrentExt;

    static {
        String sysOs = System.getProperty("os.name").toLowerCase();
        if (sysOs.contains("win")) os = OS.Windows;
        else if (sysOs.contains("mac")) os = OS.MacOS;
        else if (sysOs.contains("nix") || sysOs.contains("nux") || sysOs.contains("aix")) os = OS.Linux;
        else throw new RuntimeException("Unknown OS " + sysOs);

        String sysArch = System.getProperty("os.arch").toLowerCase();
        if (List.of("amd64", "x86_64", "x86").contains(sysArch)) arch = Arch.X86_64;
        else if (List.of("aarch64", "arm64", "arm").contains(sysArch)) arch = Arch.Arm64;
        else throw new RuntimeException("Unknown architecture " + sysArch);

        javafxPlatform = switch (os) {
            case Windows    -> "win";
            case MacOS      -> "mac";
            case Linux      -> "linux";
        };
        
        String libtorrentOs = switch (os) {
            case Windows    -> "windows";
            case MacOS      -> "macosx";
            case Linux      -> "linux";
        };
        String libtorrentArch = switch (arch) {
            case X86_64     -> "x86_64";
            case Arm64      -> "arm64";
        };
        jlibtorrentPlatform = libtorrentOs + "-" + libtorrentArch;

        jlibtorrentExt = switch (os) {
            case Windows    -> ".dll";
            case MacOS      -> ".dylib";
            case Linux      -> ".so";
        };
    }
}
