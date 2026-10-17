package br.com.playanywhere.playanywhere.install;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Utilitário para detecção precisa do sistema operacional e da distribuição Linux ativa.
 */
public class DistroDetector {
    private static final Logger LOGGER = Logger.getLogger(DistroDetector.class.getName());

    public enum OsFamily {
        LINUX,
        MACOS,
        WINDOWS,
        UNKNOWN
    }

    public record DistroInfo(
            String id,
            String name,
            String idLike,
            String version,
            String packageManager,
            String installCommand
    ) {
    }

    public static OsFamily detectOsFamily() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return OsFamily.WINDOWS;
        } else if (os.contains("mac") || os.contains("darwin")) {
            return OsFamily.MACOS;
        } else if (os.contains("linux") || os.contains("unix")) {
            return OsFamily.LINUX;
        }
        return OsFamily.UNKNOWN;
    }

    public static boolean isLinux() {
        return detectOsFamily() == OsFamily.LINUX;
    }

    public static boolean isMac() {
        return detectOsFamily() == OsFamily.MACOS;
    }

    public static boolean isWindows() {
        return detectOsFamily() == OsFamily.WINDOWS;
    }

    /**
     * Detecta a distribuição Linux atual lendo os arquivos padrão (/etc/os-release, etc.).
     */
    public static DistroInfo detectLinuxDistro() {
        Map<String, String> osReleaseProps = readOsRelease();

        String id = osReleaseProps.getOrDefault("ID", "").toLowerCase(Locale.ROOT);
        String name = osReleaseProps.getOrDefault("PRETTY_NAME", osReleaseProps.getOrDefault("NAME", "Linux"));
        String idLike = osReleaseProps.getOrDefault("ID_LIKE", "").toLowerCase(Locale.ROOT);
        String version = osReleaseProps.getOrDefault("VERSION_ID", osReleaseProps.getOrDefault("VERSION", ""));

        // Se id estiver vazio, tentar outros arquivos de identificação de distros legadas
        if (id.isEmpty()) {
            if (Files.exists(Paths.get("/etc/debian_version"))) {
                id = "debian";
                name = "Debian GNU/Linux";
            } else if (Files.exists(Paths.get("/etc/fedora-release"))) {
                id = "fedora";
                name = "Fedora Linux";
            } else if (Files.exists(Paths.get("/etc/redhat-release"))) {
                id = "rhel";
                name = "Red Hat Enterprise Linux / CentOS";
            } else if (Files.exists(Paths.get("/etc/arch-release"))) {
                id = "arch";
                name = "Arch Linux";
            } else if (Files.exists(Paths.get("/etc/SuSE-release"))) {
                id = "opensuse";
                name = "openSUSE";
            } else if (Files.exists(Paths.get("/etc/alpine-release"))) {
                id = "alpine";
                name = "Alpine Linux";
            }
        }

        String pkgManager = resolvePackageManager(id, idLike);
        String installCmd = resolveInstallCommand(id, idLike, pkgManager);

        return new DistroInfo(id, name, idLike, version, pkgManager, installCmd);
    }

    private static Map<String, String> readOsRelease() {
        Map<String, String> props = new HashMap<>();
        List<Path> paths = List.of(
                Paths.get("/etc/os-release"),
                Paths.get("/usr/lib/os-release")
        );

        for (Path path : paths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String val = line.substring(eqIdx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            props.put(key, val);
                        }
                    }
                    if (!props.isEmpty()) {
                        break;
                    }
                } catch (IOException e) {
                    LOGGER.fine("Não foi possível ler " + path + ": " + e.getMessage());
                }
            }
        }
        return props;
    }

    private static String resolvePackageManager(String id, String idLike) {
        String combined = (id + " " + idLike).toLowerCase(Locale.ROOT);

        if (combined.contains("debian") || combined.contains("ubuntu") || combined.contains("mint") || combined.contains("pop") || combined.contains("elementary") || combined.contains("zorin") || combined.contains("kali")) {
            return "apt";
        }
        if (combined.contains("fedora") || combined.contains("rhel") || combined.contains("centos") || combined.contains("rocky") || combined.contains("alma")) {
            return "dnf";
        }
        if (combined.contains("arch") || combined.contains("manjaro") || combined.contains("endeavouros") || combined.contains("garuda") || combined.contains("artix")) {
            return "pacman";
        }
        if (combined.contains("suse") || combined.contains("opensuse")) {
            return "zypper";
        }
        if (combined.contains("alpine")) {
            return "apk";
        }
        if (combined.contains("void")) {
            return "xbps";
        }
        if (combined.contains("solus")) {
            return "eopkg";
        }
        if (combined.contains("gentoo")) {
            return "emerge";
        }
        return "apt / dnf / pacman";
    }

    private static String resolveInstallCommand(String id, String idLike, String pkgManager) {
        String combined = (id + " " + idLike).toLowerCase(Locale.ROOT);

        if (combined.contains("debian") || combined.contains("ubuntu") || combined.contains("mint") || combined.contains("pop") || combined.contains("elementary") || combined.contains("zorin") || combined.contains("kali") || "apt".equals(pkgManager)) {
            return "sudo apt update && sudo apt install -y innoextract p7zip-full unar cabextract";
        }
        if (combined.contains("fedora") || combined.contains("rhel") || combined.contains("centos") || combined.contains("rocky") || combined.contains("alma") || "dnf".equals(pkgManager)) {
            return "sudo dnf install -y innoextract p7zip p7zip-plugins unar cabextract";
        }
        if (combined.contains("arch") || combined.contains("manjaro") || combined.contains("endeavouros") || combined.contains("garuda") || combined.contains("artix") || "pacman".equals(pkgManager)) {
            return "sudo pacman -S --noconfirm innoextract p7zip unar cabextract";
        }
        if (combined.contains("suse") || combined.contains("opensuse") || "zypper".equals(pkgManager)) {
            return "sudo zypper install -y innoextract p7zip-full unar cabextract";
        }
        if (combined.contains("alpine") || "apk".equals(pkgManager)) {
            return "sudo apk add innoextract 7zip cabextract";
        }
        if (combined.contains("void") || "xbps".equals(pkgManager)) {
            return "sudo xbps-install -S innoextract 7zip unar cabextract";
        }
        if (combined.contains("solus") || "eopkg".equals(pkgManager)) {
            return "sudo eopkg install innoextract p7zip unar cabextract";
        }
        if (combined.contains("gentoo") || "emerge".equals(pkgManager)) {
            return "sudo emerge --ask app-arch/innoextract app-arch/p7zip app-arch/cabextract";
        }

        return "sudo apt install innoextract p7zip-full unar || sudo dnf install innoextract p7zip unar || sudo pacman -S innoextract p7zip unar";
    }
}
