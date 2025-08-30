/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 */

package pathcreator.proxy.uid.read;

/**
 * Контейнер полей UID32 для переиспользования без аллокаций.
 *
 * <h2>Семантика</h2>
 * <ul>
 *   <li>{@code tsc} — 64-битный TSC (CPU Time Stamp Counter);</li>
 *   <li>{@code stackPtr} — 64-битный адрес локальной переменной (энтропия адресного пространства);</li>
 *   <li>{@code machineId} — 32-битный идентификатор машины, заданный через {@code Uid32.setMachineId(int)};</li>
 *   <li>{@code coreId} — 32-битный идентификатор CPU-ядра;</li>
 *   <li>{@code tid} — 32-битный идентификатор потока (OS TID);</li>
 *   <li>{@code mix} — 32-битная контрольная сумма: (uint32)(tsc ^ stackPtr ^ machineId ^ coreId ^ tid).</li>
 * </ul>
 *
 * <h2>Назначение</h2>
 * Заполняется методами {@code Uid32Reader*}. Поля публичные намеренно — минимальная цена доступа.
 * Для быстрой верификации используйте {@code Uid32Reader.readIntoChecked(...)} или {@code Uid32Checks.verifyFields(...)}.
 *
 * @since 1.0
 */
public final class Uid32Fields {

    public long tsc;
    public long stackPtr;
    public int machineId;
    public int coreId;
    public int tid;
    public int mix;

    /**
     * Обнуляет поля (опционально).
     */
    public void clear() {
        tsc = stackPtr = 0L;
        machineId = coreId = tid = mix = 0;
    }
}