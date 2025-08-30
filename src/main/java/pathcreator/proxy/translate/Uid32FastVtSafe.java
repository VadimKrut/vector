package pathcreator.proxy.translate;

import pathcreator.proxy.translate.load.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.FunctionDescriptor.of;
import static java.lang.foreign.FunctionDescriptor.ofVoid;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static pathcreator.proxy.translate.CommonStatic.*;

public final class Uid32FastVtSafe implements AutoCloseable {

    private final Arena arena;
    private final MethodHandle mhGenPtr;
    private final MethodHandle mhSet;

    public Uid32FastVtSafe() {
        try {
            this.arena = Arena.ofConfined();
            final SymbolLookup lookup = SymbolLookup.libraryLookup(NativeLibraryLoader.load(LIB_NAME_UID_32).toString(), arena);
            final Linker linker = Linker.nativeLinker();
            this.mhGenPtr = linker.downcallHandle(
                    lookup.find(UID_32_METHOD).orElseThrow(),
                    of(ADDRESS)
            );
            this.mhSet = linker.downcallHandle(
                    lookup.find(UID_32_SET_MACHINE_ID).orElseThrow(),
                    ofVoid(JAVA_INT)
            );
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

    public void generateInto(final byte[] dst, final int offset) {
        try {
            final MemorySegment addr = (MemorySegment) mhGenPtr.invokeExact();
            MemorySegment.copy(addr.reinterpret(INT32), INT0, MemorySegment.ofArray(dst), offset, INT32);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public byte[] generate() {
        try {
            final MemorySegment addr = (MemorySegment) mhGenPtr.invokeExact();
            return addr.reinterpret(INT32).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void close() {
        arena.close();
    }
}