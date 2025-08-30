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

/**
 * Быстрые проверки целостности UID32 по полю {@code mix}.
 *
 * <h2>Формула</h2>
 * {@code mix = (uint32_t)(tsc ^ stackPtr ^ machineId ^ coreId ^ tid)}.
 * Не криптография. Стоимость — наносекунды.
 *
 * @since 1.0
 */
public final class Uid32Checks {

    private Uid32Checks() {
    }

    private static final java.lang.foreign.ValueLayout.OfLong L64 = JAVA_LONG_UNALIGNED.withOrder(ByteOrder.nativeOrder());
    private static final java.lang.foreign.ValueLayout.OfInt I32 = JAVA_INT_UNALIGNED.withOrder(ByteOrder.nativeOrder());

    /**
     * Пересчёт контрольной суммы {@code mix} из разобранных значений.
     */
    public static int calcMix(final long tsc, final long stackPtr, final int machineId, final int coreId, final int tid) {
        return (int) (tsc ^ stackPtr
                ^ (machineId & 0xFFFF_FFFFL)
                ^ (coreId & 0xFFFF_FFFFL)
                ^ (tid & 0xFFFF_FFFFL));
    }

    // Удобные вспомогательные проверки (опциональны к использованию):

    /**
     * Проверка по MemorySegment (native order), без разборки.
     */
    public static boolean verifyMix(final MemorySegment s, final long o) {
        final long tsc = s.get(L64, o + OFF_TSC);
        final long stack = s.get(L64, o + OFF_STACK);
        final int machine = s.get(I32, o + OFF_MACHINE);
        final int core = s.get(I32, o + OFF_CORE);
        final int tid = s.get(I32, o + OFF_TID);
        final int mixStore = s.get(I32, o + OFF_MIX);
        return mixStore == calcMix(tsc, stack, machine, core, tid);
    }

    /**
     * Проверка по уже заполненным полям.
     */
    public static boolean verifyFields(final Uid32Fields f) {
        return f.mix == calcMix(f.tsc, f.stackPtr, f.machineId, f.coreId, f.tid);
    }
}