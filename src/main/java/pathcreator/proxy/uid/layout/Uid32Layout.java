/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 */

package pathcreator.proxy.uid.layout;

/**
 * Спецификация бинарного формата UID32 (ровно 32 байта), формируемого нативной библиотекой.
 *
 * <h2>Формат</h2>
 * <pre>
 *  Offset  Size  Поле          Тип       Описание
 *  ------  ----  ------------  --------  -----------------------------------------------
 *      0     8   tsc           uint64    CPU Time Stamp Counter (снимок при генерации)
 *      8     8   stackPtr      u64       Адрес локальной переменной (энтропия стека)
 *     16     4   machineId     uint32    Идентификатор машины (задаётся из Java)
 *     20     4   coreId        uint32    Идентификатор CPU-ядра
 *     24     4   tid           uint32    Идентификатор потока (OS TID)
 *     28     4   mix           uint32    Контрольная сумма: (uint32)(tsc ^ stackPtr ^ machineId ^ coreId ^ tid)
 * </pre>
 *
 * <h2>Эндианность</h2>
 * Порядок байт соответствует порядку записи в нативной реализации. Для чтения используйте:
 * <ul>
 *   <li>{@code Uid32ReaderSegment} — читает в {@code ByteOrder.nativeOrder()} через FFM;</li>
 *   <li>{@code Uid32ReaderArray} — читает как little-endian для {@code byte[]}.</li>
 * </ul>
 *
 * <h2>Совместимость</h2>
 * Смещения считаются частью ABI. Изменение раскладки требует поднятия версии формата
 * и синхронного обновления всех ридеров и валидаторов.
 *
 * @since 1.0
 */
public final class Uid32Layout {

    private Uid32Layout() {
        throw new AssertionError("No instances");
    }

    /**
     * Полная длина UID в байтах.
     */
    public static final int LEN = 32;

    public static final int OFF_TSC = 0;
    public static final int OFF_STACK = 8;
    public static final int OFF_MACHINE = 16;
    public static final int OFF_CORE = 20;
    public static final int OFF_TID = 24;
    public static final int OFF_MIX = 28;
}