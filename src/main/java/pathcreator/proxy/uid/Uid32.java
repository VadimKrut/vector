/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 *
 * ВНИМАНИЕ: публичный стабильный API для генерации 32-байтовых UID.
 * Внутренняя реализация основана на FFM (Foreign Function & Memory API) и
 * нативной функции generate_uid32_into(uint8_t* dst).
 */

package pathcreator.proxy.uid;

import pathcreator.proxy.uid.internal.Uid32Symbols;

import java.lang.foreign.MemorySegment;

import static pathcreator.proxy.uid.internal.Uid32Native.nativeGenerateInto;
import static pathcreator.proxy.uid.internal.Uid32Native.nativeSetMachineId;

/**
 * Высокопроизводительный генератор 32-байтовых UID с потокобезопасной записью результата.
 * <p>
 * Класс предоставляет минимальный и стабильный API уровня Java:
 * <ul>
 *   <li>{@link #setMachineId(int)} — установка 32-битного идентификатора машины;</li>
 *   <li>{@link #generateInto(MemorySegment, long)} — запись UID в заданный сегмент памяти;</li>
 *   <li>{@link #generateInto(byte[], int)} — запись UID в массив байт;</li>
 *   <li>{@link #generate()} — создание нового массива длиной 32 байта с результатом.</li>
 * </ul>
 *
 * <h2>Потокобезопасность</h2>
 * Все методы потокобезопасны. Генерация выполняется полностью на стороне нативного кода
 * с записью непосредственно в предоставленный буфер назначения (для heap-целей используется
 * потоковый промежуточный native-буфер). Глобальное состояние ограничено значением
 * {@code machineId}, которое вы можете задать один раз при инициализации процесса.
 *
 * <h2>Производительность</h2>
 * Горячий путь — один нативный вызов; для heap-назначений добавляется одна копия 32 байт.
 * Реализация избегает локации и дополнительных аллокаций в горячем пути.
 *
 * <h2>Требования к запуску</h2>
 * Для доступа к нативному API рекомендуется запускать JVM с флагом:
 * <pre>{@code --enable-native-access=ALL-UNNAMED}</pre>
 *
 * <h2>Длина UID</h2>
 * UID имеет фиксированную длину {@value pathcreator.proxy.uid.internal.Uid32Symbols#UID_LEN} байт.
 *
 * @since 1.0
 */
public final class Uid32 {

    private Uid32() {
    }

    /**
     * Устанавливает 32-битный идентификатор машины, учитываемый генератором UID на стороне нативного кода.
     * <p>
     * Рекомендуется вызывать один раз при старте приложения.
     *
     * @param id значение идентификатора машины (используются младшие 32 бита)
     * @throws RuntimeException если нативный вызов завершился с ошибкой
     */
    public static void setMachineId(final int id) {
        nativeSetMachineId(id);
    }

    /**
     * Генерирует 32-байтовый UID и записывает его в указанный сегмент памяти.
     * <p>
     * Если {@code dst} — native-сегмент, запись выполняется напрямую в
     * {@code dst.asSlice(offset, 32)}. Для heap-сегмента применяется потоковый промежуточный
     * native-буфер с последующим копированием 32 байт.
     *
     * @param dst    целевой сегмент (heap или native)
     * @param offset смещение (в байтах) в пределах {@code dst}, начиная с которого будет записан UID
     * @throws IndexOutOfBoundsException если {@code offset} &lt; 0 или в сегменте недостаточно места
     * @throws RuntimeException          если нативный вызов завершился с ошибкой
     */
    public static void generateInto(final MemorySegment dst, final long offset) {
        nativeGenerateInto(dst, offset);
    }

    /**
     * Генерирует 32-байтовый UID и записывает его в массив байт.
     *
     * @param dst    целевой массив; должен вмещать {@code offset + 32} байт
     * @param offset смещение (в байтах) в массиве, начиная с которого будет записан UID
     * @throws IndexOutOfBoundsException если {@code offset} &lt; 0 или в массиве недостаточно места
     * @throws RuntimeException          если нативный вызов завершился с ошибкой
     */
    public static void generateInto(final byte[] dst, final int offset) {
        nativeGenerateInto(dst, offset);
    }

    /**
     * Создаёт новый массив длиной {@value pathcreator.proxy.uid.internal.Uid32Symbols#UID_LEN} байт
     * и заполняет его сгенерированным UID.
     *
     * @return новый массив длиной 32 байта с UID
     * @throws RuntimeException если нативный вызов завершился с ошибкой
     */
    public static byte[] generate() {
        final byte[] out = new byte[Uid32Symbols.UID_LEN];
        nativeGenerateInto(out, 0);
        return out;
    }
}