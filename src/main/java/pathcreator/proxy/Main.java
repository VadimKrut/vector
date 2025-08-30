package pathcreator.proxy;

import pathcreator.proxy.translate.Uid32;
import pathcreator.proxy.translate.Uid32Fast;
import pathcreator.proxy.translate.Uid32FastVtSafe;

public class Main {

    // простой "blackhole", чтобы JIT не выкинул работу
    private static volatile int sink;

    public static void main(String[] args) {
        final int warmup = 200_000;
        final int iters = 100_000_000;

        // 1) Обычная версия (alloc per call)
        var gen1 = new Uid32();
        gen1.setMachineId(42);
        warmUpAlloc(gen1, warmup);

        long t1s = System.nanoTime();
        int c1 = runAlloc(gen1, iters);
        long t1e = System.nanoTime();

        long total1 = t1e - t1s;
        long avg1 = total1 / iters;

        System.out.println("=== Uid32 (byte[] per call) ===");
        System.out.println("Total time (ns):   " + total1);
        System.out.println("Average per call:  " + avg1 + " ns");
        System.out.println("Checksum:          " + c1);

        // 2) Быстрая версия с кешом адреса TLS (для платформенных потоков)
        var gen2 = new Uid32Fast();
        gen2.setMachineId(42);
        byte[] buf2 = new byte[32];
        warmUpInto(gen2, buf2, warmup);

        long t2s = System.nanoTime();
        int c2 = runInto(gen2, buf2, iters);
        long t2e = System.nanoTime();

        long total2 = t2e - t2s;
        long avg2 = total2 / iters;

        System.out.println("=== Uid32Fast (no alloc, cached TLS addr) ===");
        System.out.println("Total time (ns):   " + total2);
        System.out.println("Average per call:  " + avg2 + " ns");
        System.out.println("Checksum:          " + c2);

        // 3) ВТ-безопасная версия без кеша адреса (каждый раз свежий ptr)
        var gen3 = new Uid32FastVtSafe();
        gen3.setMachineId(42);
        byte[] buf3 = new byte[32];
        warmUpInto(gen3, buf3, warmup);

        long t3s = System.nanoTime();
        int c3 = runInto(gen3, buf3, iters);
        long t3e = System.nanoTime();

        long total3 = t3e - t3s;
        long avg3 = total3 / iters;

        System.out.println("=== Uid32FastVtSafe (no alloc, VT safe) ===");
        System.out.println("Total time (ns):   " + total3);
        System.out.println("Average per call:  " + avg3 + " ns");
        System.out.println("Checksum:          " + c3);

        // чтобы JIT точно не выкинул работу
        sink = c1 ^ c2 ^ c3;
    }

    // ------- helpers -------

    private static void warmUpAlloc(Uid32 g, int n) {
        for (int i = 0; i < n; i++) {
            byte[] b = g.generate();
            sink ^= (b[0] & 0xFF);
        }
    }

    private static int runAlloc(Uid32 g, int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            byte[] b = g.generate();
            sum += (b[0] & 0xFF);
        }
        return sum;
    }

    private static void warmUpInto(Uid32FastVtSafe g, byte[] buf, int n) {
        for (int i = 0; i < n; i++) {
            g.generateInto(buf, 0);
            sink ^= (buf[0] & 0xFF);
        }
    }

    private static int runInto(Uid32FastVtSafe g, byte[] buf, int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            g.generateInto(buf, 0);
            sum += (buf[0] & 0xFF);
        }
        return sum;
    }

    // перегрузки для Uid32Fast (тот же интерфейс generateInto)
    private static void warmUpInto(Uid32Fast g, byte[] buf, int n) {
        for (int i = 0; i < n; i++) {
            g.generateInto(buf, 0);
            sink ^= (buf[0] & 0xFF);
        }
    }

    private static int runInto(Uid32Fast g, byte[] buf, int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            g.generateInto(buf, 0);
            sum += (buf[0] & 0xFF);
        }
        return sum;
    }
}
