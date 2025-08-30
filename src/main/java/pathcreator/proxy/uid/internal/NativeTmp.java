/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 *
 * ВНИМАНИЕ: данный класс размещён в пакете *.internal и предназначен
 * исключительно для внутреннего использования модулем UID32. Публичный
 * доступ к нему не является частью стабильного API и может быть изменён.
 */

package pathcreator.proxy.uid.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static pathcreator.proxy.uid.internal.Uid32Symbols.UID_LEN;

/**
 * Временный (per-thread) native-буфер фиксированного размера {@value Uid32Symbols#UID_LEN} байт
 * для высокопроизводительной записи UID непосредственно из нативного кода.
 *
 * <h2>Назначение</h2>
 * Класс инкапсулирует создание и хранение небольшого off-heap буфера, который:
 * <ul>
 *   <li>выделяется один раз на поток с помощью {@link ThreadLocal};</li>
 *   <li>имеет <em>автоматически управляемый</em> жизненный цикл благодаря
 *       {@link Arena#ofAuto()} — освобождение памяти выполняется рантаймом после того,
 *       как объект становится недостижимым;</li>
 *   <li>используется как целевой адрес для нативной функции
 *       {@code generate_uid32_into(uint8_t* dst)}, исключая передачу heap-сегментов
 *       в {@code downcall} и устраняя потребность в блокировках.</li>
 * </ul>
 *
 * <h2>Потоковая модель и безопасность</h2>
 * Каждый экземпляр {@code NativeTmp} рассчитан на использование <b>строго внутри одного потока</b>.
 * В модуле {@code Uid32Native} экземпляры хранятся в {@link ThreadLocal}, что гарантирует:
 * <ul>
 *   <li>отсутствие гонок между потоками за один и тот же буфер;</li>
 *   <li>корректную работу как с платформенными потоками, так и с виртуальными потоками (VT)
 *       — у виртуальных потоков собственные {@code ThreadLocal}-карты.</li>
 * </ul>
 *
 * <h2>Временная безопасность</h2>
 * Буфер выделяется в арене {@link Arena#ofAuto()}, поэтому:
 * <ul>
 *   <li>ручное закрытие арены не требуется (и не предусмотрено в данном классе);</li>
 *   <li>память будет освобождена автоматически после того, как соответствующий объект
 *       {@code NativeTmp} станет недостижимым (например, при завершении потока и очистке
 *       его {@code ThreadLocal}-карты).</li>
 * </ul>
 *
 * <h2>Выравнивание</h2>
 * Буфер выделяется вызовом {@code allocate(UID_LEN)} c минимальным выравниванием (обычно 1 байт),
 * чего достаточно для записи 32 последовательных байт. Если в будущем нативная реализация будет
 * требовать строгое выравнивание (например, 8 или 16), используйте форму
 * {@link Arena#allocate(long, long)} с нужным {@code byteAlignment}.
 *
 * <h2>Типичный сценарий использования</h2>
 * Ниже показано, как {@code NativeTmp} применяется внутри биндингов:
 * {@snippet lang = java:
 * // ThreadLocal<NativeTmp> TL = ThreadLocal.withInitial(NativeTmp::new);
 * MemorySegment tmp = TL.get().seg();           // native-сегмент длиной 32 байта
 * MH_GEN_INTO.invokeExact(tmp);                 // натив пишет прямо в tmp
 * MemorySegment.copy(tmp, 0, dst, off, 32);     // одна копия в целевой сегмент (heap/native)
 *}
 *
 * @implNote Класс не потокобезопасен сам по себе и полагается на изоляцию экземпляров через {@link ThreadLocal}.
 * Это намеренное решение для устранения накладных расходов синхронизации в горячем пути.
 * @see Arena
 * @see MemorySegment
 * @see pathcreator.proxy.uid.internal.Uid32Native
 * @since 1.0
 */
public final class NativeTmp {

    /**
     * Сегмент native-памяти фиксированного размера {@value Uid32Symbols#UID_LEN} байт.
     * Сегмент ассоциирован с автоматической ареной и не требует явного закрытия.
     */
    private final MemorySegment seg;

    /**
     * Создаёт новый временный буфер в автоматической арене.
     * Размер буфера — {@value Uid32Symbols#UID_LEN} байт.
     *
     * @apiNote Конструктор предполагается вызывать только из {@code ThreadLocal.withInitial(...)}.
     * Экземпляр следует использовать исключительно в пределах того потока, где он создан.
     */
    public NativeTmp() {
        this.seg = Arena.ofAuto().allocate(UID_LEN);
    }

    /**
     * {@return native-сегмент длиной {@value Uid32Symbols#UID_LEN} байт}
     * Сегмент может передаваться в нативные вызовы (downcall) как адрес назначения.
     */
    public MemorySegment seg() {
        return seg;
    }
}