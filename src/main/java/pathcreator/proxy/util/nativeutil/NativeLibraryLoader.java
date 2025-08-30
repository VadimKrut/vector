/*
 * Copyright (c) 2025, PathCreator.
 * All rights reserved.
 *
 * ВНИМАНИЕ: данный класс предназначен для служебной загрузки нативных библиотек (.dll/.so/.dylib)
 * из ресурсов JAR или из путей разработки. Публичный API может быть изменён.
 */

package pathcreator.proxy.util.nativeutil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Унифицированный загрузчик нативных библиотек для модулей, использующих FFM.
 *
 * <h2>Порядок поиска</h2>
 * Метод {@link #load(String)} ищет и загружает библиотеку с базовым именем {@code libName}
 * в следующем порядке:
 * <ol>
 *   <li><b>Переопределение через системное свойство</b>:
 *       {@code -D&lt;libName&gt;.native.dir=/abs/path}.
 *       Если задано, библиотека ищется по пути
 *       {@code &lt;dir&gt;/[lib]&lt;libName&gt;.&lt;ext&gt;} и загружается через {@link System#load(String)}.</li>
 *   <li><b>Путь разработки</b>:
 *       {@code target/native/&lt;libName&gt;/[lib]&lt;libName&gt;.&lt;ext&gt;} — удобен при локальной сборке.</li>
 *   <li><b>Ресурсы JAR</b>:
 *       {@code /native/&lt;libName&gt;/[lib]&lt;libName&gt;.&lt;ext&gt;}.
 *       Ресурс извлекается во временный файл и загружается через {@link System#load(String)}.</li>
 * </ol>
 *
 * <h2>Имена файлов</h2>
 * Имя файла формируется с учётом ОС:
 * <ul>
 *   <li>Windows: {@code uid32.dll}</li>
 *   <li>Linux:   {@code libuid32.so}</li>
 *   <li>macOS:   {@code libuid32.dylib}</li>
 * </ul>
 *
 * <h2>Безопасность и замечания</h2>
 * <ul>
 *   <li>Загрузка выполняется через {@link System#load(String)} (не {@code loadLibrary}),
 *       что позволяет указывать абсолютные пути и временные файлы из ресурсов.</li>
 *   <li>Ресурсный поток всегда закрывается (try-with-resources).</li>
 *   <li>Временный файл помечается на удаление при завершении процесса ({@code deleteOnExit()}).</li>
 * </ul>
 *
 * @since 1.0
 */
public final class NativeLibraryLoader {

    private NativeLibraryLoader() {
    }

    /**
     * Загружает нативную библиотеку с базовым именем {@code libName} и возвращает путь к загруженному файлу.
     *
     * <p><b>Примеры:</b> при {@code libName="uid32"} будет загружен один из файлов:
     * {@code uid32.dll}, {@code libuid32.so} или {@code libuid32.dylib} — в зависимости от ОС.</p>
     *
     * @param libName базовое имя библиотеки (без префикса {@code lib} и без расширения)
     * @return путь к фактически загруженному файлу библиотеки
     * @throws RuntimeException если библиотека не найдена ни по одному из путей или извлечение ресурса завершилось с ошибкой
     */
    public static Path load(final String libName) {
        final String os = detectOS();
        final String ext = switch (os) {
            case "windows" -> ".dll";
            case "linux" -> ".so";
            case "mac" -> ".dylib";
            default -> throw new UnsupportedOperationException("Unsupported OS: " + os);
        };
        final String prefix = os.equals("windows") ? "" : "lib";

        // (1) Переопределение через системное свойство: -D<libName>.native.dir=/abs/path
        final String overrideDir = System.getProperty(libName + ".native.dir");
        if (overrideDir != null && !overrideDir.isBlank()) {
            final Path overridden = Paths.get(overrideDir, prefix + libName + ext).toAbsolutePath();
            if (!Files.exists(overridden)) {
                throw new RuntimeException("Native library not found at override path: " + overridden);
            }
            System.load(overridden.toString());
            return overridden;
        }

        // (2) Путь разработки: target/native/<libName>/
        final Path devPath = Paths.get("target", "native", libName, prefix + libName + ext).toAbsolutePath();
        if (Files.exists(devPath)) {
            System.load(devPath.toString());
            return devPath;
        }

        // (3) Ресурсы JAR: /native/<libName>/
        final String resourcePath = "native/" + libName + "/" + prefix + libName + ext;
        try (final InputStream is = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Native library resource not found: " + resourcePath);
            }
            final Path temp = Files.createTempFile("lib_" + libName + "_", ext);
            temp.toFile().deleteOnExit();
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            System.load(temp.toString());
            return temp;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to load native library: " + resourcePath, e);
        }
    }

    /**
     * Определяет целевую платформу по системному свойству {@code os.name}.
     *
     * @return строковый код ОС: {@code "windows"}, {@code "linux"} или {@code "mac"}
     * @throws UnsupportedOperationException если платформа не распознана
     */
    private static String detectOS() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "mac";
        if (os.contains("linux")) return "linux";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}