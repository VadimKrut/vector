/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 */

package pathcreator.proxy.uid.read;

import static pathcreator.proxy.uid.layout.Uid32Layout.*;
import static pathcreator.proxy.uid.read.Uid32Checks.calcMix;

/**
 * Быстрые чтения и верификация UID из {@code byte[]} в предположении little-endian.
 * <p>Без проверок границ. Для переносимости/BE-платформ используйте {@link Uid32ReaderSegment}.
 *
 * <h2>Производительность</h2>
 * Ручные LE-геттеры без {@code ByteBuffer}/{@code VarHandle}, без лямбд, без аллокаций.
 *
 * @since 1.0
 */
public final class Uid32ReaderArray {

    private Uid32ReaderArray() {
    }

    // точечные геттеры
    public static long tsc(final byte[] a, final int off) {
        return getLongLE(a, off + OFF_TSC);
    }

    public static long stackPtr(final byte[] a, final int off) {
        return getLongLE(a, off + OFF_STACK);
    }

    public static int machineId(final byte[] a, final int off) {
        return getIntLE(a, off + OFF_MACHINE);
    }

    public static int coreId(final byte[] a, final int off) {
        return getIntLE(a, off + OFF_CORE);
    }

    public static int tid(final byte[] a, final int off) {
        return getIntLE(a, off + OFF_TID);
    }

    public static int mix(final byte[] a, final int off) {
        return getIntLE(a, off + OFF_MIX);
    }

    /**
     * Разбор всех полей без проверки.
     */
    public static void readInto(final byte[] a, final int off, final Uid32Fields out) {
        out.tsc = getLongLE(a, off + OFF_TSC);
        out.stackPtr = getLongLE(a, off + OFF_STACK);
        out.machineId = getIntLE(a, off + OFF_MACHINE);
        out.coreId = getIntLE(a, off + OFF_CORE);
        out.tid = getIntLE(a, off + OFF_TID);
        out.mix = getIntLE(a, off + OFF_MIX);
    }

    /**
     * Проверка {@code mix} без разборки.
     */
    public static boolean verify(final byte[] a, final int off) {
        final long tsc = getLongLE(a, off + OFF_TSC);
        final long stack = getLongLE(a, off + OFF_STACK);
        final int machine = getIntLE(a, off + OFF_MACHINE);
        final int core = getIntLE(a, off + OFF_CORE);
        final int tid = getIntLE(a, off + OFF_TID);
        final int mixStore = getIntLE(a, off + OFF_MIX);
        return mixStore == calcMix(tsc, stack, machine, core, tid);
    }

    /**
     * Разбор + проверка за один проход.
     */
    public static boolean readIntoChecked(final byte[] a, final int off, final Uid32Fields out) {
        final long tsc = getLongLE(a, off + OFF_TSC);
        final long stack = getLongLE(a, off + OFF_STACK);
        final int machine = getIntLE(a, off + OFF_MACHINE);
        final int core = getIntLE(a, off + OFF_CORE);
        final int tid = getIntLE(a, off + OFF_TID);
        final int mixStore = getIntLE(a, off + OFF_MIX);
        out.tsc = tsc;
        out.stackPtr = stack;
        out.machineId = machine;
        out.coreId = core;
        out.tid = tid;
        out.mix = mixStore;
        return mixStore == calcMix(tsc, stack, machine, core, tid);
    }

    /**
     * Разбор + проверка, кидает {@link IllegalArgumentException} при несоответствии.
     */
    public static void readIntoOrThrow(final byte[] a, final int off, final Uid32Fields out) {
        if (!readIntoChecked(a, off, out)) throw new IllegalArgumentException("UID32 mix mismatch (byte[])");
    }

    // --- быстрые LE-геттеры (без проверок границ) ---
    private static int getIntLE(final byte[] a, final int p) {
        return (a[p] & 0xFF)
                | ((a[p + 1] & 0xFF) << 8)
                | ((a[p + 2] & 0xFF) << 16)
                | ((a[p + 3] & 0xFF) << 24);
    }

    private static long getLongLE(final byte[] a, final int p) {
        return ((long) (a[p] & 0xFF))
                | ((long) (a[p + 1] & 0xFF) << 8)
                | ((long) (a[p + 2] & 0xFF) << 16)
                | ((long) (a[p + 3] & 0xFF) << 24)
                | ((long) (a[p + 4] & 0xFF) << 32)
                | ((long) (a[p + 5] & 0xFF) << 40)
                | ((long) (a[p + 6] & 0xFF) << 48)
                | ((long) (a[p + 7] & 0xFF) << 56);
    }
}