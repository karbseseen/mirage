package core.boot;

import util.JavaUtil;
import util.SystemInfo;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


class FileLock implements AutoCloseable {
    private final Path path;
    private final java.nio.channels.FileLock lock;

    FileLock() throws IOException, StringException {
        path = Path.of(SystemInfo.os == SystemInfo.OS.Windows ? "lock" : ".lock");
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        if (SystemInfo.os == SystemInfo.OS.Windows) Files.setAttribute(path, "dos:hidden", true);
        lock = channel.tryLock(0, 0, false);
        if (lock == null) throw new StringException(JavaUtil.appName + " is already running");
    }

    @Override public void close() throws Exception {
        lock.release();
        Files.deleteIfExists(path);
    }
}
