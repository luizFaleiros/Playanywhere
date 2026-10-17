package br.com.playanywhere.playanywhere.launcher;

/**
 * Identificador de plataforma de sistema operacional.
 */
public enum PlatformType {
    WINDOWS,
    LINUX,
    MACOS,
    OTHER;

    public static PlatformType currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return WINDOWS;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return MACOS;
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return LINUX;
        }
        return OTHER;
    }
}
