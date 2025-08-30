/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 *
 * ВНИМАНИЕ: данный класс размещён в пакете *.internal и предназначен
 * исключительно для внутреннего использования модулем UID32. Публичный
 * доступ к нему не является частью стабильного API и может быть изменён.
 */

package pathcreator.proxy.uid.internal;

import pathcreator.proxy.util.nativeutil.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.FunctionDescriptor.ofVoid;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static pathcreator.proxy.uid.internal.Uid32Symbols.*;

/**
 * Низкоуровневые биндинги к нативной библиотеке UID32 на базе FFM (Foreign Function &amp; Memory API).
 * <p>
 * Обеспечивает:
 * <ul>
 *   <li>инициализацию и разрешение символов нативной библиотеки ({@code set_machine_id}, {@code generate_uid32_into});</li>
 *   <li>вызовы нативных функций через {@link MethodHandle};</li>
 *   <li>высокопроизводительную запись 32-байтового UID либо напрямую в <em>native</em>-сегмент,
 *       либо через потоковый временный буфер с последующим копированием в <em>heap</em>-сегмент.</li>
 * </ul>
 *
 * <h2>Жизненный цикл и арены</h2>
 * Для разрешения символов используется общая арена {@link Arena#ofShared()} — она живёт
 * весь срок процесса и не закрывается явно. Для горячего пути выделяется небольшой
 * потоковый буфер в {@link NativeTmp}, основанный на {@link Arena#ofAuto()} — память
 * освобождается сборщиком мусора, когда {@code ThreadLocal}-экземпляр становится недостижимым.
 *
 * <h2>Потоки и безопасность</h2>
 * Класс статичен и потокобезопасен:
 * <ul>
 *   <li>все {@code MethodHandle} и {@code Linker} — неизменяемые и инициализируются один раз;</li>
 *   <li>для записи в heap-сегмент используется отдельный <em>per-thread</em> native-буфер
 *       ({@link #TL_NATIVE_DST}), что исключает гонки;</li>
 *   <li>при записи в целевой <em>native</em>-сегмент нативная функция получает срез
 *       нужной длины и пишет непосредственно по переданному адресу.</li>
 * </ul>
 *
 * <h2>Производительность</h2>
 * Горячий путь состоит из одного {@code downcall}. Для heap-назначений добавляется одна
 * операция копирования 32 байт ({@link MemorySegment#copy(MemorySegment, long, MemorySegment, long, long)}).
 * Дополнительной синхронизации нет.
 *
 * <h2>Требования к запуску</h2>
 * Для доступа к нативному API в будущих версиях JDK необходимо запускать JVM с флагом:
 * <pre>{@code --enable-native-access=ALL-UNNAMED}</pre>
 *
 * @implNote Класс не должен создавать/закрывать пользовательские арены в горячем пути. Все объекты
 * инициализируются в статическом блоке. Исключения из нативных вызовов заворачиваются
 * в {@link RuntimeException} через {@link #sneaky(Throwable)}.
 * @see Uid32Symbols
 * @see NativeTmp
 * @since 1.0
 */
public final class Uid32Native {

    /**
     * Долгоживущая арена для разрешения символов нативной библиотеки.
     */
    private static final Arena ARENA = Arena.ofShared();

    /**
     * Линкер FFM для создания {@link MethodHandle} к нативным функциям.
     */
    private static final Linker LINKER = Linker.nativeLinker();

    /**
     * MH: {@code void set_machine_id(int)}.
     */
    private static final MethodHandle MH_SET;

    /**
     * MH: {@code void generate_uid32_into(uint8_t* dst)}.
     */
    private static final MethodHandle MH_GEN_INTO;

    /**
     * Потоковый временный native-буфер фиксированной длины {@value Uid32Symbols#UID_LEN} байт.
     * Применяется только при записи в heap-сегмент, чтобы не передавать heap в {@code downcall}.
     */
    private static final ThreadLocal<NativeTmp> TL_NATIVE_DST = ThreadLocal.withInitial(NativeTmp::new);

    static {
        try {
            final String libPath = NativeLibraryLoader.load(LIB_UID32).toString();
            final SymbolLookup lookup = SymbolLookup.libraryLookup(libPath, ARENA);
            MH_SET = LINKER.downcallHandle(lookup.find(FN_SET_MACHINE_ID).orElseThrow(), ofVoid(JAVA_INT));
            MH_GEN_INTO = LINKER.downcallHandle(lookup.find(FN_GEN_INTO).orElseThrow(), ofVoid(ADDRESS));
        } catch (final Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    /**
     * Утилитарный класс; экземпляры не создаются.
     */
    private Uid32Native() {
    }

    /**
     * Устанавливает значение {@code machineId} в нативной библиотеке.
     *
     * @param id 32-битное значение идентификатора машины (используется генератором UID).
     * @throws RuntimeException если нативный вызов завершился с ошибкой
     */
    public static void nativeSetMachineId(final int id) {
        try {
            MH_SET.invokeExact(id);
        } catch (final Throwable t) {
            throw sneaky(t);
        }
    }

    /**
     * Генерирует 32-байтовый UID и записывает его в указанный сегмент.
     * <p>
     * Поведение зависит от типа целевого сегмента:
     * <ul>
     *   <li>если {@code dst.isNative() == true} — нативная функция пишет <b>напрямую</b> в
     *       срез {@code dst.asSlice(offset, UID_LEN)};</li>
     *   <li>если сегмент heap-овый — запись производится в потоковый native-буфер, после чего
     *       выполняется один вызов {@link MemorySegment#copy(MemorySegment, long, MemorySegment, long, long)}
     *       (32 байта) в {@code dst}.</li>
     * </ul>
     *
     * @param dst    целевой сегмент (heap или native)
     * @param offset смещение в байтах в пределах {@code dst}, начиная с которого будет записан UID
     * @throws RuntimeException          если нативный вызов завершился с ошибкой
     * @throws IndexOutOfBoundsException если {@code offset} &lt; 0 или свободного места меньше {@value Uid32Symbols#UID_LEN} байт
     */
    public static void nativeGenerateInto(final MemorySegment dst, final long offset) {
        try {
            if (dst.isNative()) {
                // Прямая запись в целевой native-сегмент (без промежуточной копии).
                MH_GEN_INTO.invokeExact(dst.asSlice(offset, UID_LEN));
                return;
            }
            // Heap-сегмент: пишем в thread-local native-буфер и копируем 32 байта.
            final MemorySegment tmp = TL_NATIVE_DST.get().seg();
            MH_GEN_INTO.invokeExact(tmp);
            MemorySegment.copy(tmp, INT0, dst, offset, UID_LEN);
        } catch (final Throwable t) {
            throw sneaky(t);
        }
    }

    /**
     * Удобный перегруз для записи в массив байт.
     *
     * @param dst    целевой массив длиной не менее {@code offset + 32}
     * @param offset смещение в массиве, начиная с которого будет записан UID
     * @throws RuntimeException          если нативный вызов завершился с ошибкой
     * @throws IndexOutOfBoundsException если {@code offset} &lt; 0 или недостаточно места в массиве
     */
    public static void nativeGenerateInto(final byte[] dst, final int offset) {
        nativeGenerateInto(MemorySegment.ofArray(dst), offset);
    }

    /**
     * Преобразует любые checked-исключения и ошибки в {@link RuntimeException}
     * для удобства вызова из горячего пути.
     *
     * @param t исходное исключение/ошибка
     * @return обёртка в {@link RuntimeException} (или пробрасывает {@link Error} как есть)
     */
    private static RuntimeException sneaky(final Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error e) throw e;
        return new RuntimeException(t);
    }
}