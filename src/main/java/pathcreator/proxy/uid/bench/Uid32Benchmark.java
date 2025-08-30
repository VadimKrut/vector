package pathcreator.proxy.uid.bench;

import pathcreator.proxy.uid.Uid32;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Uid32Benchmark {

    private static final int ITERS = 100_000_000;

    public static void main(String[] args) {
        System.out.println("=== Warmup ===");
        Uid32.setMachineId(1);
        warmup();

        benchSegmentCached();
        benchArrayWrapper();
        benchNewArray();

        stressParallel();
    }

    private static void warmup() {
        byte[] tmp = new byte[32];
        MemorySegment seg = MemorySegment.ofArray(tmp);
        for (int i = 0; i < 1_000_000; i++) {
            Uid32.generateInto(seg, 0);
        }
    }

    private static void benchSegmentCached() {
        byte[] buf = new byte[32];
        MemorySegment seg = MemorySegment.ofArray(buf);
        long start = System.nanoTime();
        for (int i = 0; i < ITERS; i++) Uid32.generateInto(seg, 0);
        long end = System.nanoTime();
        print("generateInto(segment)", end - start, buf);
    }

    private static void benchArrayWrapper() {
        byte[] buf = new byte[32];
        long start = System.nanoTime();
        for (int i = 0; i < ITERS; i++) Uid32.generateInto(buf, 0);
        long end = System.nanoTime();
        print("generateInto(byte[])", end - start, buf);
    }

    private static void benchNewArray() {
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            byte[] arr = Uid32.generate();
            checksum += arr[0];
        }
        long end = System.nanoTime();
        System.out.printf("=== generate() new array ===%nTotal time (ns):   %d%nAverage per call:  %d ns%nChecksum:          %d%n%n",
                (end - start), (end - start) / ITERS, checksum);
    }

    private static void print(String label, long nanos, byte[] buf) {
        int checksum = 0;
        for (byte b : buf) checksum += b;
        System.out.printf("=== %s ===%nTotal time (ns):   %d%nAverage per call:  %d ns%nChecksum:          %d%n%n",
                label, nanos, nanos / ITERS, checksum);
    }

    private static void stressParallel() {
        final int threads = 64, perThread = 200_000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch latch = new CountDownLatch(threads);
        final ConcurrentHashMap<Long, Boolean> seen = new ConcurrentHashMap<>(1 << 20);
        final java.util.concurrent.atomic.AtomicLong collisions = new java.util.concurrent.atomic.AtomicLong();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                byte[] buf = new byte[32];
                for (int i = 0; i < perThread; i++) {
                    Uid32.generateInto(buf, 0);
                    long h = 1469598103934665603L;
                    for (int k = 0; k < 32; k++) {
                        h ^= (buf[k] & 0xFF);
                        h *= 1099511628211L;
                    }
                    if (seen.putIfAbsent(h, Boolean.TRUE) != null) collisions.incrementAndGet();
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        pool.shutdown();
        System.out.println("Parallel stress done. Collisions=" + collisions.get());
    }
}