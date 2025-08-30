package pathcreator.proxy.translate.load;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class NativeLibraryLoader {

    private NativeLibraryLoader() {
    }

    public static Path load(final String libName) {
        final String os = detectOS();
        final String ext = os.equals("windows") ? ".dll" : ".so";
        final String prefix = os.equals("windows") ? "" : "lib";
        final String resourcePath = String.format("native/%s/%s%s", libName, prefix + libName, ext);
        try {
            final InputStream is = NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                final Path devPath = Paths.get("target", "native", libName, prefix + libName + ext).toAbsolutePath();
                if (!Files.exists(devPath)) {
                    throw new RuntimeException("Native library not found: " + devPath);
                }
                System.load(devPath.toString());
                return devPath;
            }
            final Path temp = Files.createTempFile("lib_", ext);
            temp.toFile().deleteOnExit();
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            System.load(temp.toString());
            return temp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract native library: " + resourcePath, e);
        }
    }

    private static String detectOS() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("linux")) return "linux";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}