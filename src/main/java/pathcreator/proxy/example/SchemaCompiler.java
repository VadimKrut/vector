package pathcreator.proxy.example;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.*;

public final class SchemaCompiler {

    private SchemaCompiler() {
    }

    private static final byte VERSION = 1;

    private static final ValueLayout.OfByte I8 = JAVA_BYTE;
    private static final ValueLayout.OfInt I32 = JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong I64 = JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfFloat F32 = JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfDouble F64 = JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong I64_LE = JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    // header
    private static final int OFF_VER = 0;  // u8
    private static final int OFF_PRES64 = 4;  // u64

    // TL-кэш для уже закодированных строк (по ссылочной идентичности)
    private static final ThreadLocal<IdentityHashMap<String, byte[]>> TL_STR_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private static final Map<Class<?>, Codec<?>> CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static <T> Codec<T> compile(final Class<T> type) {
        return (Codec<T>) CACHE.computeIfAbsent(type, SchemaCompiler::build);
    }

    private enum Kind {
        BOOL_P, BYTE_P, SHORT_P, CHAR_P, INT_P, LONG_P, FLOAT_P, DOUBLE_P,
        BOOL_B, BYTE_B, SHORT_B, CHAR_B, INT_B, LONG_B, FLOAT_B, DOUBLE_B,
        STRING, BYTES,
        LDT
    }

    private record FieldDef(
            String name,
            Kind kind,
            MethodHandle getter,
            MethodHandle setter,
            int presenceBit,
            int lenIndex,
            int fixedOffset,
            int fixedSize
    ) {
    }

    private static <T> Codec<T> build(final Class<T> type) {
        try {
            final MethodHandles.Lookup L = MethodHandles.lookup();

            // собираем Bean-свойства
            final PropertyDescriptor[] pds = Introspector.getBeanInfo(type).getPropertyDescriptors();
            final ArrayList<PropertyDescriptor> props = new ArrayList<>(pds.length);
            for (PropertyDescriptor pd : pds) {
                if (pd.getReadMethod() != null && pd.getWriteMethod() != null && !"class".equals(pd.getName())) {
                    props.add(pd);
                }
            }
            props.sort(Comparator.comparing(PropertyDescriptor::getName));

            final ArrayList<FieldDef> fields = new ArrayList<>(props.size());
            int bit = 0, lenVar = 0, fixedOff = 0;

            for (PropertyDescriptor pd : props) {
                final Class<?> t = pd.getPropertyType();
                final Kind k = classify(t);
                if (k == null) {
                    throw new UnsupportedOperationException("Unsupported type: " + t.getName() + " for " + pd.getName());
                }
                final MethodHandle gRaw = MethodHandles.lookup().unreflect(pd.getReadMethod());
                final MethodHandle sRaw = MethodHandles.lookup().unreflect(pd.getWriteMethod());
                // мостим через Object (erasure); invokeExact требует явный cast в вызове
                final MethodHandle g = gRaw.asType(MethodType.methodType(t, Object.class));
                final MethodHandle s = sRaw.asType(MethodType.methodType(void.class, Object.class, t));

                final boolean optional = isOptional(k);
                final boolean varlen = isVarlen(k);
                final int presenceBit = optional ? bit++ : -1;
                final int lenIndex = varlen ? lenVar++ : -1;
                final int fsz = fixedSizeOf(k);
                final int foff = (fsz > 0) ? fixedOff : -1;
                if (fsz > 0) fixedOff += fsz;

                fields.add(new FieldDef(pd.getName(), k, g, s, presenceBit, lenIndex, foff, fsz));
            }

            final int LEN_COUNT = lenVar;
            final int HEAD_SIZE = 12 + 4 * LEN_COUNT;
            final int FIXED_SIZE = fixedOff;
            final int TAIL_BASE = HEAD_SIZE + FIXED_SIZE;

            final MethodHandle ctorObj =
                    L.findConstructor(type, MethodType.methodType(void.class))
                            .asType(MethodType.methodType(Object.class));

            // pool для var-payloads в write(...)
            final ThreadLocal<byte[][]> TL_VAR = (LEN_COUNT == 0)
                    ? null
                    : ThreadLocal.withInitial(() -> new byte[LEN_COUNT][]);

            // ---------- Скомпилированный кодек ----------
            return new Codec<>() {

                // -------- size --------
                @Override
                public int byteSize(final T v) {
                    int tail = 0;
                    if (LEN_COUNT != 0) {
                        final IdentityHashMap<String, byte[]> strCache = TL_STR_CACHE.get();
                        for (final FieldDef f : fields) {
                            try {
                                if (f.kind == Kind.STRING) {
                                    final String s = (String) f.getter.invokeExact((Object) v);
                                    if (s != null) {
                                        byte[] nb = strCache.get(s);
                                        if (nb == null) {
                                            nb = s.getBytes(StandardCharsets.UTF_8);
                                            strCache.put(s, nb);
                                        }
                                        tail += nb.length;
                                    }
                                } else if (f.kind == Kind.BYTES) {
                                    final byte[] b = (byte[]) f.getter.invokeExact((Object) v);
                                    if (b != null) tail += b.length;
                                }
                            } catch (Throwable e) {
                                throw rethrow(e);
                            }
                        }
                    }
                    return TAIL_BASE + tail;
                }

                // -------- write: MemorySegment --------
                @Override
                public void write(final T v, final MemorySegment dst, final long off) {
                    long presence = 0L;

                    dst.set(I8, off + OFF_VER, VERSION);

                    final byte[][] varPayloads = (TL_VAR != null) ? TL_VAR.get() : null;
                    if (LEN_COUNT != 0) {
                        Arrays.fill(varPayloads, null);
                        final IdentityHashMap<String, byte[]> strCache = TL_STR_CACHE.get();
                        for (final FieldDef f : fields) {
                            if (f.lenIndex >= 0) {
                                try {
                                    if (f.kind == Kind.STRING) {
                                        final String s = (String) f.getter.invokeExact((Object) v);
                                        byte[] nb = null;
                                        if (s != null) {
                                            nb = strCache.get(s);
                                            if (nb == null) {
                                                nb = s.getBytes(StandardCharsets.UTF_8);
                                                strCache.put(s, nb);
                                            }
                                            varPayloads[f.lenIndex] = nb;
                                            presence |= bitMask(f.presenceBit);
                                        }
                                        dst.set(I32, off + 12 + 4L * f.lenIndex, (nb == null) ? 0 : nb.length);
                                    } else if (f.kind == Kind.BYTES) {
                                        final byte[] b = (byte[]) f.getter.invokeExact((Object) v);
                                        if (b != null) {
                                            varPayloads[f.lenIndex] = b;
                                            presence |= bitMask(f.presenceBit);
                                            dst.set(I32, off + 12 + 4L * f.lenIndex, b.length);
                                        } else {
                                            dst.set(I32, off + 12 + 4L * f.lenIndex, 0);
                                        }
                                    }
                                } catch (Throwable e) {
                                    throw rethrow(e);
                                }
                            }
                        }
                    }

                    // fixed
                    for (final FieldDef f : fields) {
                        if (f.fixedSize == 0) continue;
                        final long addr = off + HEAD_SIZE + f.fixedOffset;
                        try {
                            switch (f.kind) {
                                case BOOL_P ->
                                        dst.set(I8, addr, (byte) (((boolean) f.getter.invokeExact((Object) v)) ? 1 : 0));
                                case BYTE_P -> dst.set(I8, addr, (byte) ((byte) f.getter.invokeExact((Object) v)));
                                case SHORT_P ->
                                        dst.set(I32, addr, (int) ((short) f.getter.invokeExact((Object) v))); // формат НЕ меняем
                                case CHAR_P -> dst.set(I32, addr, (int) ((char) f.getter.invokeExact((Object) v)));
                                case INT_P -> dst.set(I32, addr, (int) f.getter.invokeExact((Object) v));
                                case LONG_P -> dst.set(I64, addr, (long) f.getter.invokeExact((Object) v));
                                case FLOAT_P -> dst.set(F32, addr, (float) f.getter.invokeExact((Object) v));
                                case DOUBLE_P -> dst.set(F64, addr, (double) f.getter.invokeExact((Object) v));

                                case BOOL_B -> {
                                    final Boolean o = (Boolean) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I8, addr, (byte) (o ? 1 : 0));
                                    }
                                }
                                case BYTE_B -> {
                                    final Byte o = (Byte) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I8, addr, o);
                                    }
                                }
                                case SHORT_B -> {
                                    final Short o = (Short) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I32, addr, o.intValue());
                                    }
                                }
                                case CHAR_B -> {
                                    final Character o = (Character) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I32, addr, (int) o.charValue());
                                    }
                                }
                                case INT_B -> {
                                    final Integer o = (Integer) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I32, addr, o.intValue());
                                    }
                                }
                                case LONG_B -> {
                                    final Long o = (Long) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(I64, addr, o.longValue());
                                    }
                                }
                                case FLOAT_B -> {
                                    final Float o = (Float) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(F32, addr, o.floatValue());
                                    }
                                }
                                case DOUBLE_B -> {
                                    final Double o = (Double) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst.set(F64, addr, o.doubleValue());
                                    }
                                }
                                case LDT -> {
                                    final LocalDateTime o = (LocalDateTime) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        final long sec = o.toEpochSecond(ZoneOffset.UTC);
                                        final int ns = o.getNano();
                                        dst.set(I64, addr, sec);
                                        dst.set(I32, addr + 8, ns);
                                    }
                                }
                                default -> {
                                }
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }

                    dst.set(I64_LE, off + OFF_PRES64, presence);

                    // tail
                    long p = off + TAIL_BASE;
                    if (LEN_COUNT != 0) {
                        for (int i = 0; i < LEN_COUNT; i++) {
                            final byte[] pl = varPayloads[i];
                            if (pl != null) {
                                MemorySegment.copy(MemorySegment.ofArray(pl), 0, dst, p, pl.length);
                                p += pl.length;
                            }
                        }
                    }
                }

                // -------- write: byte[] (спец. быстрый путь) --------
                @Override
                public void write(final T v, final byte[] dst, final int off) {
                    long presence = 0L;

                    dst[off + OFF_VER] = VERSION;

                    final byte[][] varPayloads = (TL_VAR != null) ? TL_VAR.get() : null;
                    if (LEN_COUNT != 0) {
                        Arrays.fill(varPayloads, null);
                        final IdentityHashMap<String, byte[]> strCache = TL_STR_CACHE.get();
                        for (final FieldDef f : fields) {
                            if (f.lenIndex >= 0) {
                                try {
                                    if (f.kind == Kind.STRING) {
                                        final String s = (String) f.getter.invokeExact((Object) v);
                                        byte[] nb = null;
                                        if (s != null) {
                                            nb = strCache.get(s);
                                            if (nb == null) {
                                                nb = s.getBytes(StandardCharsets.UTF_8);
                                                strCache.put(s, nb);
                                            }
                                            varPayloads[f.lenIndex] = nb;
                                            presence |= bitMask(f.presenceBit);
                                        }
                                        putIntLE(dst, off + 12 + (f.lenIndex << 2), (nb == null) ? 0 : nb.length);
                                    } else if (f.kind == Kind.BYTES) {
                                        final byte[] b = (byte[]) f.getter.invokeExact((Object) v);
                                        if (b != null) {
                                            varPayloads[f.lenIndex] = b;
                                            presence |= bitMask(f.presenceBit);
                                            putIntLE(dst, off + 12 + (f.lenIndex << 2), b.length);
                                        } else {
                                            putIntLE(dst, off + 12 + (f.lenIndex << 2), 0);
                                        }
                                    }
                                } catch (Throwable e) {
                                    throw rethrow(e);
                                }
                            }
                        }
                    }

                    // fixed
                    final int fixedBase = off + HEAD_SIZE;
                    for (final FieldDef f : fields) {
                        if (f.fixedSize == 0) continue;
                        final int p = fixedBase + f.fixedOffset;
                        try {
                            switch (f.kind) {
                                case BOOL_P -> dst[p] = (byte) (((boolean) f.getter.invokeExact((Object) v)) ? 1 : 0);
                                case BYTE_P -> dst[p] = (byte) ((byte) f.getter.invokeExact((Object) v));
                                case SHORT_P -> putIntLE(dst, p, (int) ((short) f.getter.invokeExact((Object) v)));
                                case CHAR_P -> putIntLE(dst, p, (int) ((char) f.getter.invokeExact((Object) v)));
                                case INT_P -> putIntLE(dst, p, (int) f.getter.invokeExact((Object) v));
                                case LONG_P -> putLongLE(dst, p, (long) f.getter.invokeExact((Object) v));
                                case FLOAT_P ->
                                        putIntLE(dst, p, Float.floatToRawIntBits((float) f.getter.invokeExact((Object) v)));
                                case DOUBLE_P ->
                                        putLongLE(dst, p, Double.doubleToRawLongBits((double) f.getter.invokeExact((Object) v)));

                                case BOOL_B -> {
                                    final Boolean o = (Boolean) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst[p] = (byte) (o ? 1 : 0);
                                    }
                                }
                                case BYTE_B -> {
                                    final Byte o = (Byte) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        dst[p] = o;
                                    }
                                }
                                case SHORT_B -> {
                                    final Short o = (Short) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putIntLE(dst, p, o.intValue());
                                    }
                                }
                                case CHAR_B -> {
                                    final Character o = (Character) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putIntLE(dst, p, (int) o.charValue());
                                    }
                                }
                                case INT_B -> {
                                    final Integer o = (Integer) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putIntLE(dst, p, o.intValue());
                                    }
                                }
                                case LONG_B -> {
                                    final Long o = (Long) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putLongLE(dst, p, o.longValue());
                                    }
                                }
                                case FLOAT_B -> {
                                    final Float o = (Float) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putIntLE(dst, p, Float.floatToRawIntBits(o));
                                    }
                                }
                                case DOUBLE_B -> {
                                    final Double o = (Double) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        putLongLE(dst, p, Double.doubleToRawLongBits(o));
                                    }
                                }
                                case LDT -> {
                                    final LocalDateTime o = (LocalDateTime) f.getter.invokeExact((Object) v);
                                    if (o != null) {
                                        presence |= bitMask(f.presenceBit);
                                        final long sec = o.toEpochSecond(ZoneOffset.UTC);
                                        final int ns = o.getNano();
                                        putLongLE(dst, p, sec);
                                        putIntLE(dst, p + 8, ns);
                                    }
                                }
                                default -> {
                                }
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }

                    putLongLE(dst, off + OFF_PRES64, presence);

                    // tail
                    int p = off + TAIL_BASE;
                    if (LEN_COUNT != 0) {
                        for (int i = 0; i < LEN_COUNT; i++) {
                            final byte[] pl = varPayloads[i];
                            if (pl != null) {
                                System.arraycopy(pl, 0, dst, p, pl.length);
                                p += pl.length;
                            }
                        }
                    }
                }

                // -------- encodeToBytes (оставляем — воспользуется спец. write(byte[],...)) --------
                @Override
                public byte[] encodeToBytes(final T v) {
                    final int total = byteSize(v);
                    final byte[] out = new byte[total];
                    write(v, out, 0);
                    return out;
                }

                // -------- read: MemorySegment --------
                @Override
                public T read(final MemorySegment src, final long off) {
                    final byte ver = src.get(I8, off + OFF_VER);
                    if (ver != VERSION) throw new IllegalArgumentException("Version mismatch: " + ver);
                    final long presence = src.get(I64_LE, off + OFF_PRES64);

                    final int[] lens = (LEN_COUNT == 0) ? null : new int[LEN_COUNT];
                    for (final FieldDef f : fields)
                        if (f.lenIndex >= 0)
                            lens[f.lenIndex] = src.get(I32, off + 12 + 4L * f.lenIndex);

                    final T obj;
                    try {
                        obj = (T) ctorObj.invokeExact();
                    } catch (Throwable e) {
                        throw rethrow(e);
                    }

                    for (final FieldDef f : fields) {
                        if (f.fixedSize == 0) continue;
                        final long addr = off + HEAD_SIZE + f.fixedOffset;
                        final boolean present = f.presenceBit < 0 || ((presence & bitMask(f.presenceBit)) != 0L);
                        try {
                            switch (f.kind) {
                                case BOOL_P ->
                                        f.setter.invokeExact((Object) obj, (boolean) ((src.get(I8, addr) & 0xFF) != 0));
                                case BYTE_P -> f.setter.invokeExact((Object) obj, src.get(I8, addr));
                                case SHORT_P -> f.setter.invokeExact((Object) obj, (short) src.get(I32, addr));
                                case CHAR_P -> f.setter.invokeExact((Object) obj, (char) src.get(I32, addr));
                                case INT_P -> f.setter.invokeExact((Object) obj, src.get(I32, addr));
                                case LONG_P -> f.setter.invokeExact((Object) obj, src.get(I64, addr));
                                case FLOAT_P -> f.setter.invokeExact((Object) obj, src.get(F32, addr));
                                case DOUBLE_P -> f.setter.invokeExact((Object) obj, src.get(F64, addr));

                                case BOOL_B ->
                                        f.setter.invokeExact((Object) obj, present ? Boolean.valueOf((src.get(I8, addr) & 0xFF) != 0) : null);
                                case BYTE_B ->
                                        f.setter.invokeExact((Object) obj, present ? Byte.valueOf(src.get(I8, addr)) : null);
                                case SHORT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Short.valueOf((short) src.get(I32, addr)) : null);
                                case CHAR_B ->
                                        f.setter.invokeExact((Object) obj, present ? Character.valueOf((char) src.get(I32, addr)) : null);
                                case INT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Integer.valueOf(src.get(I32, addr)) : null);
                                case LONG_B ->
                                        f.setter.invokeExact((Object) obj, present ? Long.valueOf(src.get(I64, addr)) : null);
                                case FLOAT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Float.valueOf(src.get(F32, addr)) : null);
                                case DOUBLE_B ->
                                        f.setter.invokeExact((Object) obj, present ? Double.valueOf(src.get(F64, addr)) : null);

                                case LDT -> {
                                    if (present) {
                                        final long sec = src.get(I64, addr);
                                        final int ns = src.get(I32, addr + 8);
                                        f.setter.invokeExact((Object) obj, LocalDateTime.ofEpochSecond(sec, ns, ZoneOffset.UTC));
                                    } else f.setter.invokeExact((Object) obj, (LocalDateTime) null);
                                }
                                default -> {
                                }
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }

                    long p = off + TAIL_BASE;
                    for (final FieldDef f : fields) {
                        if (f.lenIndex < 0) continue;
                        final int len = lens[f.lenIndex];
                        final boolean present = ((presence & bitMask(f.presenceBit)) != 0L);
                        try {
                            if (!present) {
                                if (f.kind == Kind.STRING) f.setter.invokeExact((Object) obj, (String) null);
                                else f.setter.invokeExact((Object) obj, (byte[]) null);
                                continue;
                            }
                            if (f.kind == Kind.STRING) {
                                final byte[] nb = new byte[len];
                                if (len != 0) MemorySegment.copy(src, p, MemorySegment.ofArray(nb), 0, len);
                                f.setter.invokeExact((Object) obj, new String(nb, StandardCharsets.UTF_8));
                                p += len;
                            } else {
                                final byte[] bb = new byte[len];
                                if (len != 0) MemorySegment.copy(src, p, MemorySegment.ofArray(bb), 0, len);
                                f.setter.invokeExact((Object) obj, bb);
                                p += len;
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }
                    return obj;
                }

                // -------- read: byte[] (спец. быстрый путь) --------
                @Override
                public T read(final byte[] src, final int off) {
                    final byte ver = src[off + OFF_VER];
                    if (ver != VERSION) throw new IllegalArgumentException("Version mismatch: " + ver);
                    final long presence = getLongLE(src, off + OFF_PRES64);

                    final int[] lens = (LEN_COUNT == 0) ? null : new int[LEN_COUNT];
                    for (final FieldDef f : fields)
                        if (f.lenIndex >= 0)
                            lens[f.lenIndex] = getIntLE(src, off + 12 + (f.lenIndex << 2));

                    final T obj;
                    try {
                        obj = (T) ctorObj.invokeExact();
                    } catch (Throwable e) {
                        throw rethrow(e);
                    }

                    final int fixedBase = off + HEAD_SIZE;
                    for (final FieldDef f : fields) {
                        if (f.fixedSize == 0) continue;
                        final int p = fixedBase + f.fixedOffset;
                        final boolean present = f.presenceBit < 0 || ((presence & bitMask(f.presenceBit)) != 0L);
                        try {
                            switch (f.kind) {
                                case BOOL_P -> f.setter.invokeExact((Object) obj, (boolean) ((src[p] & 0xFF) != 0));
                                case BYTE_P -> f.setter.invokeExact((Object) obj, src[p]);
                                case SHORT_P -> f.setter.invokeExact((Object) obj, (short) getIntLE(src, p));
                                case CHAR_P -> f.setter.invokeExact((Object) obj, (char) getIntLE(src, p));
                                case INT_P -> f.setter.invokeExact((Object) obj, getIntLE(src, p));
                                case LONG_P -> f.setter.invokeExact((Object) obj, getLongLE(src, p));
                                case FLOAT_P ->
                                        f.setter.invokeExact((Object) obj, Float.intBitsToFloat(getIntLE(src, p)));
                                case DOUBLE_P ->
                                        f.setter.invokeExact((Object) obj, Double.longBitsToDouble(getLongLE(src, p)));

                                case BOOL_B ->
                                        f.setter.invokeExact((Object) obj, present ? Boolean.valueOf((src[p] & 0xFF) != 0) : null);
                                case BYTE_B ->
                                        f.setter.invokeExact((Object) obj, present ? Byte.valueOf(src[p]) : null);
                                case SHORT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Short.valueOf((short) getIntLE(src, p)) : null);
                                case CHAR_B ->
                                        f.setter.invokeExact((Object) obj, present ? Character.valueOf((char) getIntLE(src, p)) : null);
                                case INT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Integer.valueOf(getIntLE(src, p)) : null);
                                case LONG_B ->
                                        f.setter.invokeExact((Object) obj, present ? Long.valueOf(getLongLE(src, p)) : null);
                                case FLOAT_B ->
                                        f.setter.invokeExact((Object) obj, present ? Float.valueOf(Float.intBitsToFloat(getIntLE(src, p))) : null);
                                case DOUBLE_B ->
                                        f.setter.invokeExact((Object) obj, present ? Double.valueOf(Double.longBitsToDouble(getLongLE(src, p))) : null);

                                case LDT -> {
                                    if (present) {
                                        final long sec = getLongLE(src, p);
                                        final int ns = getIntLE(src, p + 8);
                                        f.setter.invokeExact((Object) obj, LocalDateTime.ofEpochSecond(sec, ns, ZoneOffset.UTC));
                                    } else f.setter.invokeExact((Object) obj, (LocalDateTime) null);
                                }
                                default -> {
                                }
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }

                    int p = off + TAIL_BASE;
                    for (final FieldDef f : fields) {
                        if (f.lenIndex < 0) continue;
                        final int len = lens[f.lenIndex];
                        final boolean present = ((presence & bitMask(f.presenceBit)) != 0L);
                        try {
                            if (!present) {
                                if (f.kind == Kind.STRING) f.setter.invokeExact((Object) obj, (String) null);
                                else f.setter.invokeExact((Object) obj, (byte[]) null);
                                continue;
                            }
                            if (f.kind == Kind.STRING) {
                                final String s = new String(src, p, len, StandardCharsets.UTF_8);
                                f.setter.invokeExact((Object) obj, s);
                                p += len;
                            } else {
                                final byte[] bb = new byte[len];
                                System.arraycopy(src, p, bb, 0, len);
                                f.setter.invokeExact((Object) obj, bb);
                                p += len;
                            }
                        } catch (Throwable e) {
                            throw rethrow(e);
                        }
                    }
                    return obj;
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException("Schema compilation failed for " + type.getName(), t);
        }
    }

    // ---- helpers ----

    private static long bitMask(final int bit) {
        return 1L << bit;
    }

    private static boolean isOptional(final Kind k) {
        return switch (k) {
            case BOOL_B, BYTE_B, SHORT_B, CHAR_B, INT_B, LONG_B, FLOAT_B, DOUBLE_B, STRING, BYTES, LDT -> true;
            default -> false;
        };
    }

    private static boolean isVarlen(final Kind k) {
        return k == Kind.STRING || k == Kind.BYTES;
    }

    private static int fixedSizeOf(final Kind k) {
        return switch (k) {
            case BOOL_P, BOOL_B, BYTE_P, BYTE_B -> 1;
            case SHORT_P, SHORT_B, CHAR_P, CHAR_B, INT_P, INT_B -> 4; // формат не меняем
            case LONG_P, LONG_B -> 8;
            case FLOAT_P, FLOAT_B -> 4;
            case DOUBLE_P, DOUBLE_B -> 8;
            case LDT -> 12; // sec(i64)+nano(i32)
            default -> 0;
        };
    }

    private static Kind classify(final Class<?> t) {
        if (t == boolean.class) return Kind.BOOL_P;
        if (t == byte.class) return Kind.BYTE_P;
        if (t == short.class) return Kind.SHORT_P;
        if (t == char.class) return Kind.CHAR_P;
        if (t == int.class) return Kind.INT_P;
        if (t == long.class) return Kind.LONG_P;
        if (t == float.class) return Kind.FLOAT_P;
        if (t == double.class) return Kind.DOUBLE_P;

        if (t == Boolean.class) return Kind.BOOL_B;
        if (t == Byte.class) return Kind.BYTE_B;
        if (t == Short.class) return Kind.SHORT_B;
        if (t == Character.class) return Kind.CHAR_B;
        if (t == Integer.class) return Kind.INT_B;
        if (t == Long.class) return Kind.LONG_B;
        if (t == Float.class) return Kind.FLOAT_B;
        if (t == Double.class) return Kind.DOUBLE_B;

        if (t == String.class) return Kind.STRING;
        if (t == byte[].class) return Kind.BYTES;
        if (t == LocalDateTime.class) return Kind.LDT;

        return null;
    }

    private static RuntimeException rethrow(final Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error e) throw e;
        return new RuntimeException(t);
    }

    // ---- Byte[] fast I/O (LE) ----
    private static void putIntLE(final byte[] a, final int p, final int v) {
        a[p] = (byte) (v);
        a[p + 1] = (byte) (v >>> 8);
        a[p + 2] = (byte) (v >>> 16);
        a[p + 3] = (byte) (v >>> 24);
    }

    private static int getIntLE(final byte[] a, final int p) {
        return (a[p] & 0xFF)
                | ((a[p + 1] & 0xFF) << 8)
                | ((a[p + 2] & 0xFF) << 16)
                | ((a[p + 3] & 0xFF) << 24);
    }

    private static void putLongLE(final byte[] a, final int p, final long v) {
        a[p] = (byte) (v);
        a[p + 1] = (byte) (v >>> 8);
        a[p + 2] = (byte) (v >>> 16);
        a[p + 3] = (byte) (v >>> 24);
        a[p + 4] = (byte) (v >>> 32);
        a[p + 5] = (byte) (v >>> 40);
        a[p + 6] = (byte) (v >>> 48);
        a[p + 7] = (byte) (v >>> 56);
    }

    private static long getLongLE(final byte[] a, final int p) {
        return ((long) a[p] & 0xFF)
                | (((long) a[p + 1] & 0xFF) << 8)
                | (((long) a[p + 2] & 0xFF) << 16)
                | (((long) a[p + 3] & 0xFF) << 24)
                | (((long) a[p + 4] & 0xFF) << 32)
                | (((long) a[p + 5] & 0xFF) << 40)
                | (((long) a[p + 6] & 0xFF) << 48)
                | (((long) a[p + 7] & 0xFF) << 56);
    }
}