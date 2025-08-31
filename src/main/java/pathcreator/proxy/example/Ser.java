package pathcreator.proxy.example;

import java.io.Serial;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public abstract class Ser<T> implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private transient Codec<T> codec;

    @SuppressWarnings("unchecked")
    private Codec<T> codec() {
        Codec<T> c = codec;
        if (c == null) {
            c = (Codec<T>) SchemaCompiler.compile(getClass());
            codec = c;
        }
        return c;
    }

    public final byte[] toBytes() {
        return codec().encodeToBytes(self());
    }

    public final void writeTo(byte[] dst, int off) {
        codec().write(self(), dst, off);
    }

    public final void writeTo(MemorySegment dst, long off) {
        codec().write(self(), dst, off);
    }

    public final MemorySegment toSegment(Arena arena) {
        return codec().encodeToNative(self(), arena);
    }

    public static <X> X fromBytes(byte[] src, int off, Class<X> type) {
        return SchemaCompiler.<X>compile(type).read(src, off);
    }

    public static <X> X fromSegment(MemorySegment src, long off, Class<X> type) {
        return SchemaCompiler.<X>compile(type).read(src, off);
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
}