/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 */

package pathcreator.proxy.uid.read;

import java.lang.foreign.MemorySegment;

/**
 * Фасад над быстрыми ридерами. В хот-пути обращайтесь напрямую к
 * {@link Uid32ReaderSegment} / {@link Uid32ReaderArray}.
 *
 * <h2>Границы</h2>
 * Методы не делают проверок длины/смещений. Вызывающий обязан гарантировать наличие
 * как минимум 32 байт по указанному офсету.
 *
 * @since 1.0
 */
public final class Uid32Reader {

    private Uid32Reader() {
    }

    // --- MemorySegment ---
    public static long tsc(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.tsc(s, o);
    }

    public static long stackPtr(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.stackPtr(s, o);
    }

    public static int machineId(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.machineId(s, o);
    }

    public static int coreId(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.coreId(s, o);
    }

    public static int tid(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.tid(s, o);
    }

    public static int mix(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.mix(s, o);
    }

    public static void readInto(final MemorySegment s, final long o, final Uid32Fields f) {
        Uid32ReaderSegment.readInto(s, o, f);
    }

    public static boolean verify(final MemorySegment s, final long o) {
        return Uid32ReaderSegment.verify(s, o);
    }

    public static boolean readIntoChecked(final MemorySegment s, final long o, final Uid32Fields f) {
        return Uid32ReaderSegment.readIntoChecked(s, o, f);
    }

    public static void readIntoOrThrow(final MemorySegment s, final long o, final Uid32Fields f) {
        Uid32ReaderSegment.readIntoOrThrow(s, o, f);
    }

    // --- byte[] ---
    public static long tsc(final byte[] a, final int o) {
        return Uid32ReaderArray.tsc(a, o);
    }

    public static long stackPtr(final byte[] a, final int o) {
        return Uid32ReaderArray.stackPtr(a, o);
    }

    public static int machineId(final byte[] a, final int o) {
        return Uid32ReaderArray.machineId(a, o);
    }

    public static int coreId(final byte[] a, final int o) {
        return Uid32ReaderArray.coreId(a, o);
    }

    public static int tid(final byte[] a, final int o) {
        return Uid32ReaderArray.tid(a, o);
    }

    public static int mix(final byte[] a, final int o) {
        return Uid32ReaderArray.mix(a, o);
    }

    public static void readInto(final byte[] a, final int o, final Uid32Fields f) {
        Uid32ReaderArray.readInto(a, o, f);
    }

    public static boolean verify(final byte[] a, final int o) {
        return Uid32ReaderArray.verify(a, o);
    }

    public static boolean readIntoChecked(final byte[] a, final int o, final Uid32Fields f) {
        return Uid32ReaderArray.readIntoChecked(a, o, f);
    }

    public static void readIntoOrThrow(final byte[] a, final int o, final Uid32Fields f) {
        Uid32ReaderArray.readIntoOrThrow(a, o, f);
    }
}