/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 *
 * ВНИМАНИЕ: данный класс размещён в пакете *.internal и предназначен
 * исключительно для внутреннего использования модулем UID32. Публичный
 * доступ к нему не является частью стабильного API и может быть изменён.
 */

package pathcreator.proxy.uid.internal;

/**
 * Набор констант (имена символов, размеры, служебные значения) для модуля UID32.
 * <p>
 * Константы этого класса образуют «контракт» между Java-обвязкой (FFM) и нативной
 * библиотекой. Изменение значений без синхронного обновления нативного кода приведёт
 * к ошибкам разрешения символов или нарушению бинарной совместимости.
 *
 * <h2>Назначение и области применения</h2>
 * <ul>
 *   <li>{@link #UID_LEN} — фиксированная длина UID (в байтах), генерируемого нативной функцией;</li>
 *   <li>{@link #INT0} — удобная константа «нулевого смещения» для операций копирования сегментов;</li>
 *   <li>{@link #LIB_UID32} — базовое имя нативной библиотеки. Лоадер формирует полное имя
 *       с учётом ОС: {@code libuid32.so} (Linux), {@code uid32.dll} (Windows), {@code libuid32.dylib} (macOS);</li>
 *   <li>{@link #FN_SET_MACHINE_ID} — точное имя экспортируемого C-символа
 *       {@code void set_machine_id(uint32_t id)};</li>
 *   <li>{@link #FN_GEN_INTO} — точное имя экспортируемого C-символа
 *       {@code void generate_uid32_into(uint8_t* dst)}, который записывает ровно 32 байта по адресу {@code dst}.</li>
 * </ul>
 *
 * <h2>Загрузка библиотеки</h2>
 * По умолчанию библиотека ищется:
 * <ol>
 *   <li>в системном свойстве {@code -Duid32.native.dir=/abs/path} (если задано);</li>
 *   <li>в каталоге разработки {@code target/native/uid32/};</li>
 *   <li>в ресурсах JAR по пути {@code /native/uid32/}.</li>
 * </ol>
 * См. реализацию {@code NativeLibraryLoader}.
 *
 * <h2>Совместимость</h2>
 * Имена {@link #FN_SET_MACHINE_ID} и {@link #FN_GEN_INTO} должны в точности совпадать
 * с экспортируемыми символами нативной библиотеки. Любое расхождение приведёт к
 * {@code NoSuchElementException} при {@code SymbolLookup.find(...)}.
 *
 * @implNote Класс не содержит логики и не должен иметь экземпляров.
 * @see pathcreator.proxy.uid.internal.Uid32Native
 * @since 1.0
 */
public final class Uid32Symbols {

    private Uid32Symbols() {
        throw new AssertionError("No instances");
    }

    /**
     * Фиксированная длина UID в байтах.
     */
    public static final int UID_LEN = 32;

    /**
     * Удобная константа «нулевого смещения» для операций copy/slice.
     */
    public static final int INT0 = 0;

    /**
     * Базовое имя нативной библиотеки (без префикса/расширения).
     */
    public static final String LIB_UID32 = "uid32";

    /**
     * Имя C-символа: {@code void set_machine_id(uint32_t id)}.
     */
    public static final String FN_SET_MACHINE_ID = "set_machine_id";

    /**
     * Имя C-символа: {@code void generate_uid32_into(uint8_t* dst)}.
     */
    public static final String FN_GEN_INTO = "generate_uid32_into";
}