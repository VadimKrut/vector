package pathcreator.proxy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import pathcreator.proxy.example.ExampleDto;
import pathcreator.proxy.example.Ser;

import java.io.*;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public final class Main {

    private static final int WARMUP_ITERS = 1_000_000;
    private static final int WRITE_ITERS  = 5_000_000;
    private static final int READ_ITERS   = 1_000_000;

    private static final int JDK_W_ITERS  = Math.min(WRITE_ITERS / 10, 200_000);
    private static final int JDK_R_ITERS  = Math.min(READ_ITERS  / 10, 100_000);

    public static void main(String[] args) {
        final ExampleDto dto = new ExampleDto();
        dto.setString("Hello World");
        dto.setLongValue(123456789L);
        dto.setBytes("Hello World".getBytes(StandardCharsets.UTF_8));
        dto.setLocalDateTime(LocalDateTime.now());
        dto.setBoolValue(null);
        dto.setBooleanValue(true);
        dto.setString2("Hello World2");
        dto.setLongValue2(89L);

        // наш формат: размер и предвыделение
        final byte[] oursOnce = dto.toBytes();
        final int size = oursOnce.length;
        System.out.printf("Sizes: OURS=%d bytes%n", size);

        try (Arena arena = Arena.ofConfined()) {
            final byte[] oursByteBuf = new byte[size];
            final MemorySegment oursSegBuf = arena.allocate(size, 1);

            final MemorySegment srcSeg = arena.allocate(size, 1);
            MemorySegment.copy(MemorySegment.ofArray(oursOnce), 0, srcSeg, 0, size);

            // --- Kryo ---
            final Kryo kryo = newKryo();
            final Output kryoOut = new Output(size + 128); // prealloc
            final Input kryoIn = new Input();

            // стабильный образ Kryo для read-бенча
            kryoOut.setPosition(0);
            kryo.writeObject(kryoOut, dto);
            kryoOut.flush();
            final byte[] kryoStable = copyOf(kryoOut.getBuffer(), kryoOut.position());
            System.out.printf("Sizes: KRYO=%d bytes%n", kryoStable.length);

            // --- JDK ---
            final byte[] jdkStable = jdkSerialize(dto);
            System.out.printf("Sizes: JDK=%d bytes%n%n", jdkStable.length);

            // --- прогрев ---
            warmup(dto, oursByteBuf, oursSegBuf, kryo, kryoOut, kryoIn, kryoStable, jdkStable);
            System.out.println("=== Warmup done ===");

            // ===== OURS: serialize =====
            long t0 = System.nanoTime();
            int cs1 = 0;
            for (int i = 0; i < WRITE_ITERS; i++) {
                dto.writeTo(oursByteBuf, 0);
                cs1 += oursByteBuf[0];
            }
            long t1 = System.nanoTime();
            print("OURS  serialize -> byte[] (prealloc)", t1 - t0, WRITE_ITERS, cs1);

            t0 = System.nanoTime();
            int cs2 = 0;
            for (int i = 0; i < WRITE_ITERS; i++) {
                dto.writeTo(oursSegBuf, 0);
                cs2 += (oursSegBuf.get(JAVA_BYTE, 0) & 0xFF);
            }
            t1 = System.nanoTime();
            print("OURS  serialize -> segment (prealloc)", t1 - t0, WRITE_ITERS, cs2);

            t0 = System.nanoTime();
            long cs3 = 0;
            for (int i = 0; i < WRITE_ITERS; i++) {
                byte[] arr = dto.toBytes(); // alloc per op
                cs3 += arr[0];
            }
            t1 = System.nanoTime();
            print("OURS  serialize -> new byte[]", t1 - t0, WRITE_ITERS, cs3);

            // ===== OURS: deserialize =====
            t0 = System.nanoTime();
            long cs4 = 0;
            for (int i = 0; i < READ_ITERS; i++) {
                ExampleDto x = Ser.fromBytes(oursOnce, 0, ExampleDto.class);
                cs4 += (x.isBooleanValue() ? 1 : 0);
                if (x.getLongValue() != null) cs4 += (x.getLongValue() & 0xF);
                if (x.getString() != null)    cs4 += x.getString().length();
            }
            t1 = System.nanoTime();
            print("OURS  deserialize <- byte[]", t1 - t0, READ_ITERS, cs4);

            t0 = System.nanoTime();
            long cs5 = 0;
            for (int i = 0; i < READ_ITERS; i++) {
                ExampleDto x = Ser.fromSegment(srcSeg, 0, ExampleDto.class);
                cs5 += (x.isBooleanValue() ? 1 : 0);
                if (x.getLongValue2() != null) cs5 += (x.getLongValue2() & 0xF);
                if (x.getString2() != null)    cs5 += x.getString2().length();
            }
            t1 = System.nanoTime();
            print("OURS  deserialize <- segment", t1 - t0, READ_ITERS, cs5);

            // ===== KRYO: serialize =====
            t0 = System.nanoTime();
            int cs6 = 0;
            for (int i = 0; i < WRITE_ITERS; i++) {
                kryoOut.setPosition(0);                  // вместо clear()
                kryo.writeObject(kryoOut, dto);
                cs6 += kryoOut.getBuffer()[0] & 0xFF;
            }
            t1 = System.nanoTime();
            print("KRYO  serialize -> Output(buffer)", t1 - t0, WRITE_ITERS, cs6);

            // ===== KRYO: deserialize =====
            t0 = System.nanoTime();
            long cs7 = 0;
            for (int i = 0; i < READ_ITERS; i++) {
                kryoIn.setBuffer(kryoStable, 0, kryoStable.length);
                ExampleDto x = kryo.readObject(kryoIn, ExampleDto.class);
                cs7 += (x.isBooleanValue() ? 1 : 0);
                if (x.getLongValue() != null) cs7 += (x.getLongValue() & 0xF);
                if (x.getString() != null)    cs7 += x.getString().length();
            }
            t1 = System.nanoTime();
            print("KRYO  deserialize <- byte[]", t1 - t0, READ_ITERS, cs7);

            // ===== JDK: serialize =====
            t0 = System.nanoTime();
            long cs8 = 0;
            for (int i = 0; i < JDK_W_ITERS; i++) {
                byte[] arr = jdkSerialize(dto);
                cs8 += arr[0] & 0xFF;
            }
            t1 = System.nanoTime();
            print("JDK   serialize -> byte[] (OOS per op)", t1 - t0, JDK_W_ITERS, cs8);

            // ===== JDK: deserialize =====
            t0 = System.nanoTime();
            long cs9 = 0;
            for (int i = 0; i < JDK_R_ITERS; i++) {
                ExampleDto x = jdkDeserialize(jdkStable, ExampleDto.class);
                cs9 += (x.isBooleanValue() ? 1 : 0);
                if (x.getLongValue2() != null) cs9 += (x.getLongValue2() & 0xF);
                if (x.getString2() != null)    cs9 += x.getString2().length();
            }
            t1 = System.nanoTime();
            print("JDK   deserialize <- byte[] (OIS per op)", t1 - t0, JDK_R_ITERS, cs9);
        }
    }

    // ---- Kryo ----
    private static Kryo newKryo() {
        Kryo k = new Kryo();
        k.setReferences(false);
        k.setRegistrationRequired(true);

        k.register(ExampleDto.class, 10);
        k.register(String.class, 11);
        k.register(byte[].class, 12);
        k.register(Long.class, 13);
        k.register(Boolean.class, 14);

        // LocalDateTime: sec+nano (UTC)
        k.register(LocalDateTime.class, new Serializer<LocalDateTime>() {
            @Override public void write(Kryo kryo, Output output, LocalDateTime obj) {
                if (obj == null) { output.writeByte((byte)0); return; }
                output.writeByte((byte)1);
                output.writeLong(obj.toEpochSecond(ZoneOffset.UTC), false);
                output.writeInt(obj.getNano());
            }
            @Override public LocalDateTime read(Kryo kryo, Input input, Class<? extends LocalDateTime> type) {
                byte tag = input.readByte();
                if (tag == 0) return null;
                long sec = input.readLong(false);
                int  ns  = input.readInt();
                return LocalDateTime.ofEpochSecond(sec, ns, ZoneOffset.UTC);
            }
        }, 15);
        return k;
    }

    // ---- JDK ----
    private static byte[] jdkSerialize(Serializable obj) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(256);
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(obj);
            }
            return baos.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T jdkDeserialize(byte[] bytes, Class<T> cls) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (T) ois.readObject();
            }
        } catch (IOException | ClassNotFoundException e) { throw new RuntimeException(e); }
    }

    // ---- util ----
    private static byte[] copyOf(byte[] src, int len) {
        byte[] dst = new byte[len];
        System.arraycopy(src, 0, dst, 0, len);
        return dst;
    }

    private static void warmup(
            ExampleDto dto, byte[] oursByteBuf, MemorySegment oursSegBuf,
            Kryo kryo, Output kryoOut, Input kryoIn,
            byte[] kryoStable, byte[] jdkStable
    ) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment segTmp = arena.allocate(oursSegBuf.byteSize(), 1);
            for (int i = 0; i < WARMUP_ITERS; i++) {
                dto.writeTo(oursByteBuf, 0);
                dto.writeTo(oursSegBuf, 0);
                Ser.fromBytes(oursByteBuf, 0, ExampleDto.class);
                Ser.fromSegment(oursSegBuf, 0, ExampleDto.class);

                kryoOut.setPosition(0);
                kryo.writeObject(kryoOut, dto);
                kryoIn.setBuffer(kryoStable, 0, kryoStable.length);
                kryo.readObject(kryoIn, ExampleDto.class);

                jdkDeserialize(jdkStable, ExampleDto.class);

                MemorySegment.copy(oursSegBuf, 0, segTmp, 0, Math.min(oursSegBuf.byteSize(), segTmp.byteSize()));
            }
        }
    }

    private static void print(String label, long nanos, int iters, long checksum) {
        long avg = nanos / iters;
        System.out.printf("=== %s ===%nTotal time (ns):   %d%nAverage per op:   %d ns%nChecksum:         %d%n%n",
                label, nanos, avg, checksum);
    }
}