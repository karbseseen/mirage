package util;

import java.util.List;


public class SystemInfo {
    public enum OS { Windows, MacOS, Linux }
    public enum Arch { X86_64, Arm64 }

    public static final OS os;
    public static final Arch arch;
    public static final String sharedLibExt;

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

        sharedLibExt = switch (os) {
            case Windows    -> ".dll";
            case MacOS      -> ".dylib";
            case Linux      -> ".so";
        };
    }
}
