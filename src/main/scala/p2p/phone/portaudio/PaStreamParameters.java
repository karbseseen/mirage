package p2p.phone.portaudio;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * <pre>{@code
 * typedef struct PaStreamParameters {
 *     PaDeviceIndex device;               // int
 *     int channelCount;
 *     PaSampleFormat sampleFormat;        // unsigned long
 *     PaTime suggestedLatency;            // double, seconds
 *     void *hostApiSpecificStreamInfo;
 * } PaStreamParameters;
 * }</pre>
 *
 * <p>The layout is built for the running platform: {@code unsigned long} is 4 bytes on Windows
 * (so 4 bytes of padding precede the double) and 8 bytes on Linux/macOS (no padding).
 * The accessors expose it as a Java {@code long} everywhere.
 */
public final class PaStreamParameters {
    private PaStreamParameters() {}

    public static final GroupLayout $LAYOUT = layout(RuntimeHelper.C_LONG);

    /** Package-private so the padding logic can be tested with either long width. */
    static GroupLayout layout(ValueLayout cLong) {
        long end = 2 * JAVA_INT.byteSize() + cLong.byteSize();
        long align = JAVA_DOUBLE.byteAlignment();
        long pad = (align - end % align) % align;

        List<MemoryLayout> members = new ArrayList<>();
        members.add(JAVA_INT.withName("device"));
        members.add(JAVA_INT.withName("channelCount"));
        members.add(cLong.withName("sampleFormat"));
        if (pad > 0) members.add(MemoryLayout.paddingLayout(pad));
        members.add(JAVA_DOUBLE.withName("suggestedLatency"));
        members.add(ADDRESS.withName("hostApiSpecificStreamInfo"));
        return MemoryLayout.structLayout(members.toArray(MemoryLayout[]::new)).withName("PaStreamParameters");
    }

    public static GroupLayout layout() { return $LAYOUT; }

    private static final long DEVICE_OFFSET  = $LAYOUT.byteOffset(PathElement.groupElement("device"));
    private static final long CHANNEL_OFFSET = $LAYOUT.byteOffset(PathElement.groupElement("channelCount"));
    private static final long FORMAT_OFFSET  = $LAYOUT.byteOffset(PathElement.groupElement("sampleFormat"));
    private static final long LATENCY_OFFSET = $LAYOUT.byteOffset(PathElement.groupElement("suggestedLatency"));
    private static final long HOST_OFFSET    = $LAYOUT.byteOffset(PathElement.groupElement("hostApiSpecificStreamInfo"));

    /** Allocates a zero-initialized struct. */
    public static MemorySegment allocate(SegmentAllocator allocator) { return allocator.allocate($LAYOUT); }

    public static int device(MemorySegment struct)                     { return struct.get(JAVA_INT, DEVICE_OFFSET); }
    public static void device(MemorySegment struct, int value)         { struct.set(JAVA_INT, DEVICE_OFFSET, value); }

    public static int channelCount(MemorySegment struct)               { return struct.get(JAVA_INT, CHANNEL_OFFSET); }
    public static void channelCount(MemorySegment struct, int value)   { struct.set(JAVA_INT, CHANNEL_OFFSET, value); }

    public static long sampleFormat(MemorySegment struct)              { return RuntimeHelper.getCLong(struct, FORMAT_OFFSET); }
    public static void sampleFormat(MemorySegment struct, long value)  { RuntimeHelper.setCLong(struct, FORMAT_OFFSET, value); }

    /** Suggested latency in seconds. */
    public static double suggestedLatency(MemorySegment struct)              { return struct.get(JAVA_DOUBLE, LATENCY_OFFSET); }
    public static void suggestedLatency(MemorySegment struct, double value)  { struct.set(JAVA_DOUBLE, LATENCY_OFFSET, value); }

    public static MemorySegment hostApiSpecificStreamInfo(MemorySegment struct)             { return struct.get(ADDRESS, HOST_OFFSET); }
    public static void hostApiSpecificStreamInfo(MemorySegment struct, MemorySegment value) { struct.set(ADDRESS, HOST_OFFSET, value); }
}
