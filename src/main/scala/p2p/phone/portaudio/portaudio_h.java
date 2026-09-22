// Generated-style Panama (java.lang.foreign) bindings for a subset of portaudio.h.
// Requires Java 22+ (final FFM API).
package p2p.phone.portaudio;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Bindings for:
 * {@code Pa_GetErrorText, Pa_Initialize, Pa_Terminate, Pa_OpenDefaultStream,
 * Pa_CloseStream, Pa_StartStream, Pa_StopStream, Pa_AbortStream,
 * Pa_OpenStream, Pa_GetStreamInfo, Pa_GetDefaultInputDevice, Pa_GetDefaultOutputDevice}
 *
 * <p>Type mapping (identical on every platform):
 * <ul>
 *   <li>{@code int}, {@code PaError}, {@code PaErrorCode} -> {@code int}</li>
 *   <li>{@code double} -> {@code double}</li>
 *   <li>{@code unsigned long} ({@code PaSampleFormat}, frame counts, flags) -> {@code long}</li>
 *   <li>{@code PaDeviceIndex} -> {@code int}; {@code PaTime} -> {@code double}</li>
 *   <li>pointers ({@code PaStream*}, {@code PaStream**}, callbacks, userData, struct pointers) -> {@link MemorySegment}</li>
 * </ul>
 */
public final class portaudio_h {

    private portaudio_h() {}

    // ---------------------------------------------------------------------------------------------
    // Layouts
    // ---------------------------------------------------------------------------------------------
    public static final ValueLayout.OfInt C_INT = JAVA_INT;
    public static final ValueLayout.OfDouble C_DOUBLE = JAVA_DOUBLE;
    public static final AddressLayout C_POINTER = ADDRESS;
    /** {@code long}/{@code unsigned long}: 32-bit on Windows, 64-bit on Linux/macOS. */
    public static final ValueLayout C_LONG = RuntimeHelper.C_LONG;

    // ---------------------------------------------------------------------------------------------
    // enum PaErrorCode
    // ---------------------------------------------------------------------------------------------
    public static final int paNoError                                = 0;
    public static final int paNotInitialized                         = -10000;
    public static final int paUnanticipatedHostError                 = -9999;
    public static final int paInvalidChannelCount                    = -9998;
    public static final int paInvalidSampleRate                      = -9997;
    public static final int paInvalidDevice                          = -9996;
    public static final int paInvalidFlag                            = -9995;
    public static final int paSampleFormatNotSupported               = -9994;
    public static final int paBadIODeviceCombination                 = -9993;
    public static final int paInsufficientMemory                     = -9992;
    public static final int paBufferTooBig                           = -9991;
    public static final int paBufferTooSmall                         = -9990;
    public static final int paNullCallback                           = -9989;
    public static final int paBadStreamPtr                           = -9988;
    public static final int paTimedOut                               = -9987;
    public static final int paInternalError                          = -9986;
    public static final int paDeviceUnavailable                      = -9985;
    public static final int paIncompatibleHostApiSpecificStreamInfo  = -9984;
    public static final int paStreamIsStopped                        = -9983;
    public static final int paStreamIsNotStopped                     = -9982;
    public static final int paInputOverflowed                        = -9981;
    public static final int paOutputUnderflowed                      = -9980;
    public static final int paHostApiNotFound                        = -9979;
    public static final int paInvalidHostApi                         = -9978;
    public static final int paCanNotReadFromACallbackStream          = -9977;
    public static final int paCanNotWriteToACallbackStream           = -9976;
    public static final int paCanNotReadFromAnOutputOnlyStream       = -9975;
    public static final int paCanNotWriteToAnInputOnlyStream         = -9974;
    public static final int paIncompatibleStreamHostApi              = -9973;
    public static final int paBadBufferPtr                           = -9972;
    public static final int paCanNotInitializeRecursively            = -9971;

    // ---------------------------------------------------------------------------------------------
    // PaDeviceIndex special values
    // ---------------------------------------------------------------------------------------------
    public static final int paNoDevice = -1;
    public static final int paUseHostApiSpecificDeviceSpecification = -2;

    // ---------------------------------------------------------------------------------------------
    // PaSampleFormat (unsigned long)
    // ---------------------------------------------------------------------------------------------
    public static final long paFloat32        = 0x00000001L;
    public static final long paInt32          = 0x00000002L;
    public static final long paInt24          = 0x00000004L;
    public static final long paInt16          = 0x00000008L;
    public static final long paInt8           = 0x00000010L;
    public static final long paUInt8          = 0x00000020L;
    public static final long paCustomFormat   = 0x00010000L;
    public static final long paNonInterleaved = 0x80000000L;

    // ---------------------------------------------------------------------------------------------
    // Stream opening constants / PaStreamFlags (unsigned long)
    // ---------------------------------------------------------------------------------------------
    public static final long paFramesPerBufferUnspecified              = 0L;
    public static final long paNoFlag                                  = 0L;
    public static final long paClipOff                                 = 0x00000001L;
    public static final long paDitherOff                               = 0x00000002L;
    public static final long paNeverDropInput                          = 0x00000004L;
    public static final long paPrimeOutputBuffersUsingStreamCallback   = 0x00000008L;
    public static final long paPlatformSpecificFlags                   = 0xFFFF0000L;

    // ---------------------------------------------------------------------------------------------
    // PaStreamCallbackFlags (unsigned long)
    // ---------------------------------------------------------------------------------------------
    public static final long paInputUnderflow  = 0x00000001L;
    public static final long paInputOverflow   = 0x00000002L;
    public static final long paOutputUnderflow = 0x00000004L;
    public static final long paOutputOverflow  = 0x00000008L;
    public static final long paPrimingOutput   = 0x00000010L;

    // ---------------------------------------------------------------------------------------------
    // enum PaStreamCallbackResult
    // ---------------------------------------------------------------------------------------------
    public static final int paContinue = 0;
    public static final int paComplete = 1;
    public static final int paAbort    = 2;

    // ---------------------------------------------------------------------------------------------
    // const char *Pa_GetErrorText(PaError errorCode)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_GetErrorText {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(
                ADDRESS.withTargetLayout(MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE)),
                JAVA_INT);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_GetErrorText", DESC,
                MethodType.methodType(MemorySegment.class, int.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_GetErrorText");
    }
    public static FunctionDescriptor Pa_GetErrorText$descriptor() { return Pa_GetErrorText.DESC; }
    public static MethodHandle Pa_GetErrorText$handle() { return Pa_GetErrorText.HANDLE; }
    public static MemorySegment Pa_GetErrorText$address() { return Pa_GetErrorText.ADDR; }
    /** Returns a NUL-terminated UTF-8 string; read it with {@code segment.getString(0)}. */
    public static MemorySegment Pa_GetErrorText(int errorCode) {
        try {
            return (MemorySegment) Pa_GetErrorText.HANDLE.invokeExact(errorCode);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_Initialize(void)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_Initialize {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_Initialize", DESC,
                MethodType.methodType(int.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_Initialize");
    }
    public static FunctionDescriptor Pa_Initialize$descriptor() { return Pa_Initialize.DESC; }
    public static MethodHandle Pa_Initialize$handle() { return Pa_Initialize.HANDLE; }
    public static MemorySegment Pa_Initialize$address() { return Pa_Initialize.ADDR; }
    public static int Pa_Initialize() {
        try {
            return (int) Pa_Initialize.HANDLE.invokeExact();
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_Terminate(void)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_Terminate {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_Terminate", DESC,
                MethodType.methodType(int.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_Terminate");
    }
    public static FunctionDescriptor Pa_Terminate$descriptor() { return Pa_Terminate.DESC; }
    public static MethodHandle Pa_Terminate$handle() { return Pa_Terminate.HANDLE; }
    public static MemorySegment Pa_Terminate$address() { return Pa_Terminate.ADDR; }
    public static int Pa_Terminate() {
        try {
            return (int) Pa_Terminate.HANDLE.invokeExact();
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_OpenDefaultStream(PaStream **stream, int numInputChannels, int numOutputChannels,
    //                              PaSampleFormat sampleFormat, double sampleRate,
    //                              unsigned long framesPerBuffer,
    //                              PaStreamCallback *streamCallback, void *userData)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_OpenDefaultStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT,
                ADDRESS,            // PaStream **stream
                JAVA_INT,           // numInputChannels
                JAVA_INT,           // numOutputChannels
                RuntimeHelper.C_LONG, // sampleFormat     (unsigned long)
                JAVA_DOUBLE,        // sampleRate
                RuntimeHelper.C_LONG, // framesPerBuffer  (unsigned long)
                ADDRESS,            // streamCallback
                ADDRESS);           // userData
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_OpenDefaultStream", DESC,
                MethodType.methodType(int.class,
                        MemorySegment.class, int.class, int.class, long.class,
                        double.class, long.class, MemorySegment.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_OpenDefaultStream");
    }
    public static FunctionDescriptor Pa_OpenDefaultStream$descriptor() { return Pa_OpenDefaultStream.DESC; }
    public static MethodHandle Pa_OpenDefaultStream$handle() { return Pa_OpenDefaultStream.HANDLE; }
    public static MemorySegment Pa_OpenDefaultStream$address() { return Pa_OpenDefaultStream.ADDR; }
    /**
     * @param stream         address of a pointer-sized slot that receives the PaStream* (e.g. {@code arena.allocate(ADDRESS)})
     * @param sampleFormat   e.g. {@link #paFloat32}
     * @param framesPerBuffer e.g. {@link #paFramesPerBufferUnspecified}
     * @param streamCallback a stub from {@link PaStreamCallback#allocate}, or {@link MemorySegment#NULL} for blocking mode
     * @param userData       {@link MemorySegment#NULL} if unused
     */
    public static int Pa_OpenDefaultStream(MemorySegment stream, int numInputChannels, int numOutputChannels,
                                           long sampleFormat, double sampleRate, long framesPerBuffer,
                                           MemorySegment streamCallback, MemorySegment userData) {
        try {
            return (int) Pa_OpenDefaultStream.HANDLE.invokeExact(stream, numInputChannels, numOutputChannels,
                    sampleFormat, sampleRate, framesPerBuffer, streamCallback, userData);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_CloseStream(PaStream *stream)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_CloseStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, ADDRESS);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_CloseStream", DESC,
                MethodType.methodType(int.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_CloseStream");
    }
    public static FunctionDescriptor Pa_CloseStream$descriptor() { return Pa_CloseStream.DESC; }
    public static MethodHandle Pa_CloseStream$handle() { return Pa_CloseStream.HANDLE; }
    public static MemorySegment Pa_CloseStream$address() { return Pa_CloseStream.ADDR; }
    public static int Pa_CloseStream(MemorySegment stream) {
        try {
            return (int) Pa_CloseStream.HANDLE.invokeExact(stream);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_StartStream(PaStream *stream)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_StartStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, ADDRESS);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_StartStream", DESC,
                MethodType.methodType(int.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_StartStream");
    }
    public static FunctionDescriptor Pa_StartStream$descriptor() { return Pa_StartStream.DESC; }
    public static MethodHandle Pa_StartStream$handle() { return Pa_StartStream.HANDLE; }
    public static MemorySegment Pa_StartStream$address() { return Pa_StartStream.ADDR; }
    public static int Pa_StartStream(MemorySegment stream) {
        try {
            return (int) Pa_StartStream.HANDLE.invokeExact(stream);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_StopStream(PaStream *stream)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_StopStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, ADDRESS);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_StopStream", DESC,
                MethodType.methodType(int.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_StopStream");
    }
    public static FunctionDescriptor Pa_StopStream$descriptor() { return Pa_StopStream.DESC; }
    public static MethodHandle Pa_StopStream$handle() { return Pa_StopStream.HANDLE; }
    public static MemorySegment Pa_StopStream$address() { return Pa_StopStream.ADDR; }
    public static int Pa_StopStream(MemorySegment stream) {
        try {
            return (int) Pa_StopStream.HANDLE.invokeExact(stream);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_AbortStream(PaStream *stream)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_AbortStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT, ADDRESS);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_AbortStream", DESC,
                MethodType.methodType(int.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_AbortStream");
    }
    public static FunctionDescriptor Pa_AbortStream$descriptor() { return Pa_AbortStream.DESC; }
    public static MethodHandle Pa_AbortStream$handle() { return Pa_AbortStream.HANDLE; }
    public static MemorySegment Pa_AbortStream$address() { return Pa_AbortStream.ADDR; }
    public static int Pa_AbortStream(MemorySegment stream) {
        try {
            return (int) Pa_AbortStream.HANDLE.invokeExact(stream);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaDeviceIndex Pa_GetDefaultInputDevice(void)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_GetDefaultInputDevice {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_GetDefaultInputDevice", DESC,
                MethodType.methodType(int.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_GetDefaultInputDevice");
    }
    public static FunctionDescriptor Pa_GetDefaultInputDevice$descriptor() { return Pa_GetDefaultInputDevice.DESC; }
    public static MethodHandle Pa_GetDefaultInputDevice$handle() { return Pa_GetDefaultInputDevice.HANDLE; }
    public static MemorySegment Pa_GetDefaultInputDevice$address() { return Pa_GetDefaultInputDevice.ADDR; }
    /** @return a device index, or {@link #paNoDevice} if there is no default input device */
    public static int Pa_GetDefaultInputDevice() {
        try {
            return (int) Pa_GetDefaultInputDevice.HANDLE.invokeExact();
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaDeviceIndex Pa_GetDefaultOutputDevice(void)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_GetDefaultOutputDevice {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_GetDefaultOutputDevice", DESC,
                MethodType.methodType(int.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_GetDefaultOutputDevice");
    }
    public static FunctionDescriptor Pa_GetDefaultOutputDevice$descriptor() { return Pa_GetDefaultOutputDevice.DESC; }
    public static MethodHandle Pa_GetDefaultOutputDevice$handle() { return Pa_GetDefaultOutputDevice.HANDLE; }
    public static MemorySegment Pa_GetDefaultOutputDevice$address() { return Pa_GetDefaultOutputDevice.ADDR; }
    /** @return a device index, or {@link #paNoDevice} if there is no default output device */
    public static int Pa_GetDefaultOutputDevice() {
        try {
            return (int) Pa_GetDefaultOutputDevice.HANDLE.invokeExact();
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PaError Pa_OpenStream(PaStream **stream,
    //                       const PaStreamParameters *inputParameters,
    //                       const PaStreamParameters *outputParameters,
    //                       double sampleRate, unsigned long framesPerBuffer,
    //                       PaStreamFlags streamFlags,
    //                       PaStreamCallback *streamCallback, void *userData)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_OpenStream {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(JAVA_INT,
                ADDRESS,              // PaStream **stream
                ADDRESS,              // inputParameters  (NULL for output-only)
                ADDRESS,              // outputParameters (NULL for input-only)
                JAVA_DOUBLE,          // sampleRate
                RuntimeHelper.C_LONG, // framesPerBuffer (unsigned long)
                RuntimeHelper.C_LONG, // streamFlags     (unsigned long)
                ADDRESS,              // streamCallback
                ADDRESS);             // userData
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_OpenStream", DESC,
                MethodType.methodType(int.class,
                        MemorySegment.class, MemorySegment.class, MemorySegment.class,
                        double.class, long.class, long.class,
                        MemorySegment.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_OpenStream");
    }
    public static FunctionDescriptor Pa_OpenStream$descriptor() { return Pa_OpenStream.DESC; }
    public static MethodHandle Pa_OpenStream$handle() { return Pa_OpenStream.HANDLE; }
    public static MemorySegment Pa_OpenStream$address() { return Pa_OpenStream.ADDR; }
    /**
     * @param stream           address of a pointer-sized slot that receives the PaStream*
     * @param inputParameters  a {@link PaStreamParameters} struct, or {@link MemorySegment#NULL}
     * @param outputParameters a {@link PaStreamParameters} struct, or {@link MemorySegment#NULL}
     * @param framesPerBuffer  e.g. {@link #paFramesPerBufferUnspecified}
     * @param streamFlags      e.g. {@link #paNoFlag}, {@link #paClipOff}
     */
    public static int Pa_OpenStream(MemorySegment stream, MemorySegment inputParameters,
                                    MemorySegment outputParameters, double sampleRate,
                                    long framesPerBuffer, long streamFlags,
                                    MemorySegment streamCallback, MemorySegment userData) {
        try {
            return (int) Pa_OpenStream.HANDLE.invokeExact(stream, inputParameters, outputParameters,
                    sampleRate, framesPerBuffer, streamFlags, streamCallback, userData);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // const PaStreamInfo *Pa_GetStreamInfo(PaStream *stream)
    // ---------------------------------------------------------------------------------------------
    private static class Pa_GetStreamInfo {
        static final FunctionDescriptor DESC = FunctionDescriptor.of(
                ADDRESS.withTargetLayout(PaStreamInfo.$LAYOUT), ADDRESS);
        static final MethodHandle HANDLE = RuntimeHelper.downcall("Pa_GetStreamInfo", DESC,
                MethodType.methodType(MemorySegment.class, MemorySegment.class));
        static final MemorySegment ADDR = RuntimeHelper.address("Pa_GetStreamInfo");
    }
    public static FunctionDescriptor Pa_GetStreamInfo$descriptor() { return Pa_GetStreamInfo.DESC; }
    public static MethodHandle Pa_GetStreamInfo$handle() { return Pa_GetStreamInfo.HANDLE; }
    public static MemorySegment Pa_GetStreamInfo$address() { return Pa_GetStreamInfo.ADDR; }
    /**
     * @return a segment sized to {@link PaStreamInfo#$LAYOUT}; read it with the {@link PaStreamInfo}
     *         accessors. On error, returns {@link MemorySegment#NULL} (check {@code address() != 0}
     *         before reading). The memory is owned by PortAudio and valid only until the stream is
     *         closed: copy values out, don't keep the segment.
     */
    public static MemorySegment Pa_GetStreamInfo(MemorySegment stream) {
        try {
            MemorySegment info = (MemorySegment) Pa_GetStreamInfo.HANDLE.invokeExact(stream);
            // With a target layout, a C NULL still comes back sized; normalize so a stray read fails safely.
            return info.address() == 0 ? MemorySegment.NULL : info;
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }
}
