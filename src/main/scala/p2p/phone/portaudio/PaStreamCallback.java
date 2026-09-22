package p2p.phone.portaudio;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * <pre>{@code
 * typedef int PaStreamCallback(const void *input, void *output,
 *                              unsigned long frameCount,
 *                              const PaStreamCallbackTimeInfo *timeInfo,
 *                              PaStreamCallbackFlags statusFlags,
 *                              void *userData);
 * }</pre>
 *
 * <p>{@code unsigned long} parameters are exposed as Java {@code long} on every platform.
 *
 * <p><b>Warning:</b> the callback runs on PortAudio's real-time audio thread. Don't block or
 * allocate, and never let an exception escape: an uncaught exception in an upcall terminates the JVM.
 * Do not call PortAudio functions from inside it.
 */
@FunctionalInterface
public interface PaStreamCallback {

    int apply(MemorySegment input, MemorySegment output, long frameCount,
              MemorySegment timeInfo, long statusFlags, MemorySegment userData);

    /** Native function descriptor for the current platform. */
    FunctionDescriptor $DESC = FunctionDescriptor.of(JAVA_INT,
            ADDRESS,                 // input
            ADDRESS,                 // output
            RuntimeHelper.C_LONG,    // frameCount  (unsigned long)
            ADDRESS,                 // timeInfo
            RuntimeHelper.C_LONG,    // statusFlags (unsigned long)
            ADDRESS);                // userData

    static FunctionDescriptor $descriptor() {
        return $DESC;
    }

    /**
     * Creates a native function pointer that calls {@code fi}. The pointer stays valid until
     * {@code arena} is closed, so keep the arena alive for as long as the stream is open.
     */
    static MemorySegment allocate(PaStreamCallback fi, Arena arena) {
        try {
            MethodHandle target = MethodHandles.lookup()
                    .findVirtual(PaStreamCallback.class, "apply", MethodType.methodType(
                            int.class,
                            MemorySegment.class, MemorySegment.class, long.class,
                            MemorySegment.class, long.class, MemorySegment.class))
                    .bindTo(fi);

            if (!RuntimeHelper.LONG_IS_64_BIT) {
                // Windows: native unsigned long is 32-bit. Zero-extend to the Java-facing long.
                MethodHandle u32ToLong = MethodHandles.lookup().findStatic(
                        Integer.class, "toUnsignedLong", MethodType.methodType(long.class, int.class));
                target = MethodHandles.filterArguments(target, 2, u32ToLong, null, u32ToLong);
            }
            return RuntimeHelper.LINKER.upcallStub(target, $DESC, arena);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("should not reach here", e);
        }
    }
}
