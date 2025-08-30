package pathcreator.proxy.translate;

import pathcreator.proxy.translate.load.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static pathcreator.proxy.translate.CommonStatic.*;

public final class Uid32Fast implements AutoCloseable {

    private static final java.lang.foreign.FunctionDescriptor FD_GEN_PTR = java.lang.foreign.FunctionDescriptor.of(ADDRESS);
    private static final java.lang.foreign.FunctionDescriptor FD_SET_MACHINE = java.lang.foreign.FunctionDescriptor.ofVoid(JAVA_INT);

    private final Arena arena;
    private final MethodHandle mhGenPtr;
    private final MethodHandle mhSet;

    private final ThreadLocal<MemorySegment> tlsBuf = ThreadLocal.withInitial(() -> null);

    public Uid32Fast() {
        try {
            this.arena = Arena.ofConfined();
            final SymbolLookup lookup = SymbolLookup.libraryLookup(NativeLibraryLoader.load(LIB_NAME_UID_32).toString(), arena);
            final Linker linker = Linker.nativeLinker();
            this.mhGenPtr = linker.downcallHandle(lookup.find(UID_32_METHOD).orElseThrow(), FD_GEN_PTR);
            this.mhSet = linker.downcallHandle(lookup.find(UID_32_SET_MACHINE_ID).orElseThrow(), FD_SET_MACHINE);
        } catch (final Throwable t) {
            throw new RuntimeException("FFM init failed", t);
        }
    }

    public void setMachineId(final int id) {
        try {
            mhSet.invokeExact(id);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public MemorySegment generateSegment() {
        try {
            MemorySegment seg = tlsBuf.get();
            if (seg == null) {
                MemorySegment addr = (MemorySegment) mhGenPtr.invokeExact();
                seg = addr.reinterpret(32);
                tlsBuf.set(seg);
                return seg;
            } else {
                final MemorySegment ignore = (MemorySegment) mhGenPtr.invokeExact();
                return seg;
            }
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public void generateInto(final byte[] dst, final int offset) {
        MemorySegment.copy(generateSegment(), 0, MemorySegment.ofArray(dst), offset, 32);
    }

    public byte[] generate() {
        return generateSegment().toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
    }

    @Override
    public void close() {
        arena.close();
    }
}