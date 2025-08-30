package pathcreator.proxy.translate;

import pathcreator.proxy.translate.load.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.*;
import static pathcreator.proxy.translate.CommonStatic.*;

public final class Uid32 {

    private static final FunctionDescriptor DESC_GENERATE = FunctionDescriptor.of(ADDRESS);
    private static final FunctionDescriptor DESC_MACHINE_ID = FunctionDescriptor.ofVoid(JAVA_INT);

    private final MethodHandle generateHandle;
    private final MethodHandle setMachineIdHandle;

    public Uid32() {
        try {
            final Arena arena = Arena.ofAuto();
            final SymbolLookup lookup = SymbolLookup.libraryLookup(
                    NativeLibraryLoader.load(LIB_NAME_UID_32).toString(), arena
            );
            final Linker linker = Linker.nativeLinker();
            this.generateHandle = linker.downcallHandle(
                    lookup.find(UID_32_METHOD).orElseThrow(() ->
                            new IllegalStateException("generate_uid32 not found")),
                    DESC_GENERATE
            );
            this.setMachineIdHandle = linker.downcallHandle(
                    lookup.find(UID_32_SET_MACHINE_ID).orElseThrow(() ->
                            new IllegalStateException("set_machine_id not found")),
                    DESC_MACHINE_ID
            );
        } catch (final Throwable t) {
            throw new RuntimeException("Failed to initialize Uid32", t);
        }
    }

    public void setMachineId(final int id) {
        try {
            setMachineIdHandle.invokeExact(id);
        } catch (final Throwable t) {
            throw new RuntimeException("Failed to call set_machine_id", t);
        }
    }

    public byte[] generate() {
        try {
            final MemorySegment seg = (MemorySegment) generateHandle.invokeExact();
            return seg.reinterpret(32).toArray(JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}