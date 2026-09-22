package p2p.phone.portaudio;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * <pre>{@code
 * typedef struct PaStreamInfo {
 *     int structVersion;
 *     PaTime inputLatency;     // seconds; 0 for output-only streams
 *     PaTime outputLatency;    // seconds; 0 for input-only streams
 *     double sampleRate;       // Hz; the rate PortAudio believes the hardware really runs at
 * } PaStreamInfo;
 * }</pre>
 * Read-only: instances are returned by {@link portaudio_h#Pa_GetStreamInfo}.
 */
public final class PaStreamInfo {
    private PaStreamInfo() {}

    public static final GroupLayout $LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("structVersion"),
            MemoryLayout.paddingLayout(4),          // the doubles are 8-byte aligned on all supported platforms
            JAVA_DOUBLE.withName("inputLatency"),
            JAVA_DOUBLE.withName("outputLatency"),
            JAVA_DOUBLE.withName("sampleRate")
    ).withName("PaStreamInfo");

    public static GroupLayout layout() { return $LAYOUT; }

    private static final long VERSION_OFFSET = $LAYOUT.byteOffset(PathElement.groupElement("structVersion"));
    private static final long INPUT_OFFSET   = $LAYOUT.byteOffset(PathElement.groupElement("inputLatency"));
    private static final long OUTPUT_OFFSET  = $LAYOUT.byteOffset(PathElement.groupElement("outputLatency"));
    private static final long RATE_OFFSET    = $LAYOUT.byteOffset(PathElement.groupElement("sampleRate"));

    public static int structVersion(MemorySegment struct)    { return struct.get(JAVA_INT, VERSION_OFFSET); }
    public static double inputLatency(MemorySegment struct)  { return struct.get(JAVA_DOUBLE, INPUT_OFFSET); }
    public static double outputLatency(MemorySegment struct) { return struct.get(JAVA_DOUBLE, OUTPUT_OFFSET); }
    public static double sampleRate(MemorySegment struct)    { return struct.get(JAVA_DOUBLE, RATE_OFFSET); }
}
