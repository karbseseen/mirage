package p2p.phone.portaudio;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;

/**
 * <pre>{@code
 * typedef struct PaStreamCallbackTimeInfo {
 *     PaTime inputBufferAdcTime;    // double
 *     PaTime currentTime;           // double
 *     PaTime outputBufferDacTime;   // double
 * } PaStreamCallbackTimeInfo;
 * }</pre>
 */
public final class PaStreamCallbackTimeInfo {
    private PaStreamCallbackTimeInfo() {}

    public static final GroupLayout $LAYOUT = MemoryLayout.structLayout(
            JAVA_DOUBLE.withName("inputBufferAdcTime"),
            JAVA_DOUBLE.withName("currentTime"),
            JAVA_DOUBLE.withName("outputBufferDacTime")
    ).withName("PaStreamCallbackTimeInfo");

    public static GroupLayout layout() { return $LAYOUT; }

    private static final long INPUT_OFFSET   = $LAYOUT.byteOffset(PathElement.groupElement("inputBufferAdcTime"));
    private static final long CURRENT_OFFSET = $LAYOUT.byteOffset(PathElement.groupElement("currentTime"));
    private static final long OUTPUT_OFFSET  = $LAYOUT.byteOffset(PathElement.groupElement("outputBufferDacTime"));

    /**
     * Views a raw pointer (such as the {@code timeInfo} argument of a {@link PaStreamCallback})
     * as a struct. Restricted operation: run with --enable-native-access.
     */
    public static MemorySegment ofAddress(MemorySegment address) {
        return address.reinterpret($LAYOUT.byteSize());
    }

    public static double inputBufferAdcTime(MemorySegment struct)  { return struct.get(JAVA_DOUBLE, INPUT_OFFSET); }
    public static double currentTime(MemorySegment struct)         { return struct.get(JAVA_DOUBLE, CURRENT_OFFSET); }
    public static double outputBufferDacTime(MemorySegment struct) { return struct.get(JAVA_DOUBLE, OUTPUT_OFFSET); }
}
