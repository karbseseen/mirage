package p2p.phone.portaudio;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Runtime support for the PortAudio bindings (the moral equivalent of jextract's RuntimeHelper).
 *
 * <p>Two things make this "cross-platform" instead of "generated for one platform":
 * <ol>
 *   <li>The native library is located at run time (Windows / macOS / Linux naming and search paths).</li>
 *   <li>C {@code unsigned long} is 32-bit on Windows (LLP64) and 64-bit on Linux/macOS (LP64).
 *       The layout is taken from the running platform's linker, and the public Java API always
 *       uses {@code long}; the difference is bridged with method-handle adapters.</li>
 * </ol>
 */
final class RuntimeHelper {
    private RuntimeHelper() {}

    static final Linker LINKER = Linker.nativeLinker();

    /** C {@code long} / {@code unsigned long}: JAVA_INT on Windows, JAVA_LONG on Linux/macOS. */
    static final ValueLayout C_LONG = (ValueLayout) LINKER.canonicalLayouts().get("long");

    static final boolean LONG_IS_64_BIT = C_LONG.byteSize() == Long.BYTES;

    static final SymbolLookup SYMBOLS = loadPortAudio();

    // ---------------------------------------------------------------------------------------------
    // Linking helpers
    // ---------------------------------------------------------------------------------------------

    static MemorySegment address(String symbol) {
        return SYMBOLS.find(symbol)
                .orElseThrow(() -> new UnsatisfiedLinkError("PortAudio symbol not found: " + symbol));
    }

    /**
     * Creates a downcall handle for {@code symbol} whose Java-facing type is {@code javaType}.
     * Any platform-specific difference between {@code desc} and {@code javaType}
     * (i.e. C long as int vs. long) is bridged with a cast adapter.
     */
    static MethodHandle downcall(String symbol, FunctionDescriptor desc, MethodType javaType) {
        MethodHandle mh = LINKER.downcallHandle(address(symbol), desc);
        return MethodHandles.explicitCastArguments(mh, javaType);
    }

    // ---------------------------------------------------------------------------------------------
    // Struct-field helpers for C unsigned long (32-bit on Windows, 64-bit on Linux/macOS)
    // ---------------------------------------------------------------------------------------------

    static long getCLong(MemorySegment seg, long offset) {
        return LONG_IS_64_BIT
                ? seg.get(ValueLayout.JAVA_LONG, offset)
                : Integer.toUnsignedLong(seg.get(ValueLayout.JAVA_INT, offset));
    }

    static void setCLong(MemorySegment seg, long offset, long value) {
        if (LONG_IS_64_BIT) {
            seg.set(ValueLayout.JAVA_LONG, offset, value);
        } else {
            seg.set(ValueLayout.JAVA_INT, offset, (int) value);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Library discovery
    // ---------------------------------------------------------------------------------------------

    private static SymbolLookup loadPortAudio() {
        try {
            Path path = Paths.get("lib", System.mapLibraryName("portaudio"));
            return SymbolLookup.libraryLookup(path, Arena.global());
        } catch (IllegalArgumentException notFound) {
            throw new UnsatisfiedLinkError("Could not load the PortAudio native library. This should not happen.");
        }
    }
}
