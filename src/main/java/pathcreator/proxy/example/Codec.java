package pathcreator.proxy.example;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public interface Codec<T> {

    int byteSize(final T value);

    void write(final T value, final MemorySegment dst, final long off);

    T read(final MemorySegment src, final long off);

    default void write(final T value, final byte[] dst, final int off) {
        write(value, MemorySegment.ofArray(dst), off);
    }

    default T read(final byte[] src, final int off) {
        return read(MemorySegment.ofArray(src), off);
    }

    default byte[] encodeToBytes(final T value) {
        final int size = byteSize(value);
        final byte[] out = new byte[size];
        write(value, out, 0);
        return out;
    }

    default MemorySegment encodeToNative(final T value, final Arena arena) {
        final int size = byteSize(value);
        final MemorySegment seg = arena.allocate(size, 1);
        write(value, seg, 0);
        return seg;
    }
}