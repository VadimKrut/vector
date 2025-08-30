/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 */

package pathcreator.proxy.uid.read;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static pathcreator.proxy.uid.layout.Uid32Layout.*;
import static pathcreator.proxy.uid.read.Uid32Checks.calcMix;

/**
 * Быстрые чтения и проверка UID из {@link MemorySegment} в {@code ByteOrder.nativeOrder()}.
 * <p>Без проверок границ. Сегмент/смещение должны указывать на не менее 32 байт.
 *
 * @since 1.0
 */
public final class Uid32ReaderSegment {

    private Uid32ReaderSegment() {
    }

    private static final java.lang.foreign.ValueLayout.OfLong L64 =
            JAVA_LONG_UNALIGNED.withOrder(ByteOrder.nativeOrder());
    private static final java.lang.foreign.ValueLayout.OfInt I32 =
            JAVA_INT_UNALIGNED.withOrder(ByteOrder.nativeOrder());

    // точечные геттеры
    public static long tsc(final MemorySegment s, final long o) {
        return s.get(L64, o + OFF_TSC);
    }

    public static long stackPtr(final MemorySegment s, final long o) {
        return s.get(L64, o + OFF_STACK);
    }

    public static int machineId(final MemorySegment s, final long o) {
        return s.get(I32, o + OFF_MACHINE);
    }

    public static int coreId(final MemorySegment s, final long o) {
        return s.get(I32, o + OFF_CORE);
    }

    public static int tid(final MemorySegment s, final long o) {
        return s.get(I32, o + OFF_TID);
    }

    public static int mix(final MemorySegment s, final long o) {
        return s.get(I32, o + OFF_MIX);
    }

    /**
     * Разбор всех полей без проверки.
     */
    public static void readInto(final MemorySegment s, final long o, final Uid32Fields out) {
        out.tsc = s.get(L64, o + OFF_TSC);
        out.stackPtr = s.get(L64, o + OFF_STACK);
        out.machineId = s.get(I32, o + OFF_MACHINE);
        out.coreId = s.get(I32, o + OFF_CORE);
        out.tid = s.get(I32, o + OFF_TID);
        out.mix = s.get(I32, o + OFF_MIX);
    }

    /**
     * Проверка {@code mix} без разборки.
     */
    public static boolean verify(final MemorySegment s, final long o) {
        final long tsc = s.get(L64, o + OFF_TSC);
        final long stack = s.get(L64, o + OFF_STACK);
        final int machine = s.get(I32, o + OFF_MACHINE);
        final int core = s.get(I32, o + OFF_CORE);
        final int tid = s.get(I32, o + OFF_TID);
        final int mixStore = s.get(I32, o + OFF_MIX);
        return mixStore == calcMix(tsc, stack, machine, core, tid);
    }

    /**
     * Разбор + проверка за один проход.
     */
    public static boolean readIntoChecked(final MemorySegment s, final long o, final Uid32Fields out) {
        final long tsc = s.get(L64, o + OFF_TSC);
        final long stack = s.get(L64, o + OFF_STACK);
        final int machine = s.get(I32, o + OFF_MACHINE);
        final int core = s.get(I32, o + OFF_CORE);
        final int tid = s.get(I32, o + OFF_TID);
        final int mixStore = s.get(I32, o + OFF_MIX);
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
    public static void readIntoOrThrow(final MemorySegment s, final long o, final Uid32Fields out) {
        if (!readIntoChecked(s, o, out)) throw new IllegalArgumentException("UID32 mix mismatch (segment)");
    }
}