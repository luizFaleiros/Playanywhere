package br.com.playanywhere.playanywhere.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Detector e configurador do runtime Valve Proton para execução de jogos Windows no Linux.
 */
public class ProtonDetector {
    private static final Logger LOGGER = Logger.getLogger(ProtonDetector.class.getName());
    private static Path customProtonPath;
    private static boolean forceDisabled = false;

    public static void setCustomProtonPath(Path path) {
        customProtonPath = path;
    }

    public static void setForceDisabled(boolean disabled) {
        forceDisabled = disabled;
    }

    public static void resetCustomProtonPath() {
        customProtonPath = null;
        forceDisabled = false;
    }

    /**
     * Verifica se o Proton está disponível no sistema.
     */
    public static boolean isProtonAvailable() {
        return findProtonExecutable().isPresent();
    }

    /**
     * Localiza o executável/script do Proton mais recente ou configurado.
     */
    public static Optional<Path> findProtonExecutable() {
        if (forceDisabled) {
            return Optional.empty();
        }

        if (customProtonPath != null) {
            return Files.exists(customProtonPath) ? Optional.of(customProtonPath) : Optional.empty();
        }

        // 1. Variável de ambiente PROTON_PATH
        String envProton = System.getenv("PROTON_PATH");
        if (envProton != null && !envProton.trim().isEmpty()) {
            Path p = Paths.get(envProton);
            if (isValidProtonExecutable(p)) return Optional.of(p);
            Path pScript = p.resolve("proton");
            if (isValidProtonExecutable(pScript)) return Optional.of(pScript);
        }

        // 2. Diretórios comuns da Steam no Linux
        String userHome = System.getProperty("user.home", "/home");
        List<Path> searchRoots = List.of(
            Paths.get(userHome, ".local/share/Steam/steamapps/common"),
            Paths.get(userHome, ".steam/steam/steamapps/common"),
            Paths.get(userHome, ".steam/root/compatibilitytools.d"),
            Paths.get(userHome, ".local/share/Steam/compatibilitytools.d"),
            Paths.get("/var/lib/flatpak/app/com.valvesoftware.Steam/current/active/files/share/steam/compatibilitytools.d"),
            Paths.get("/usr/share/steam/compatibilitytools.d")
        );

        for (Path root : searchRoots) {
            if (Files.exists(root) && Files.isDirectory(root)) {
                try (Stream<Path> stream = Files.list(root)) {
                    Optional<Path> found = stream
                        .filter(Files::isDirectory)
                        .filter(dir -> {
                            String name = dir.getFileName().toString().toLowerCase();
                            return name.contains("proton") || name.contains("ge-proton");
                        })
                        .sorted((p1, p2) -> p2.getFileName().toString().compareToIgnoreCase(p1.getFileName().toString()))
                        .map(dir -> dir.resolve("proton"))
                        .filter(ProtonDetector::isValidProtonExecutable)
                        .findFirst();

                    if (found.isPresent()) {
                        LOGGER.info(() -> "Proton localizado em: " + found.get());
                        return found;
                    }
                } catch (IOException ignored) {}
            }
        }

        // 3. Checar proton no PATH do sistema
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            Path p = Paths.get(dir, "proton");
            if (isValidProtonExecutable(p)) {
                LOGGER.info(() -> "Proton encontrado no PATH: " + p);
                return Optional.of(p);
            }
        }

        return Optional.empty();
    }

    private static boolean isValidProtonExecutable(Path path) {
        return path != null && Files.exists(path) && (Files.isExecutable(path) || Files.isRegularFile(path));
    }

    /**
     * Localiza o diretório raiz de instalação da Steam.
     */
    public static Path findSteamDirectory() {
        String userHome = System.getProperty("user.home", "/home");
        List<Path> candidatePaths = List.of(
            Paths.get(userHome, ".local/share/Steam"),
            Paths.get(userHome, ".steam/steam"),
            Paths.get(userHome, ".steam/root")
        );
        for (Path p : candidatePaths) {
            if (Files.exists(p) && Files.isDirectory(p)) {
                return p;
            }
        }
        return Paths.get(userHome, ".steam/steam");
    }

    /**
     * Retorna o diretório de dados compatíveis do jogo (STEAM_COMPAT_DATA_PATH).
     */
    public static Path getCompatDataDirectory(String gameSlug) {
        String slug = (gameSlug != null && !gameSlug.trim().isEmpty()) ? gameSlug : "default_game";
        Path compatDir = Paths.get(System.getProperty("user.home", "/home"), ".playanywhere", "compatdata", slug);
        try {
            if (!Files.exists(compatDir)) {
                Files.createDirectories(compatDir);
            }
        } catch (IOException e) {
            LOGGER.warning("Não foi possível criar STEAM_COMPAT_DATA_PATH: " + e.getMessage());
        }
        return compatDir;
    }

    /**
     * Cria e configura o ProcessBuilder para executar o jogo via Proton.
     */
    public static ProcessBuilder buildProtonProcess(Path protonExecutable, Path gameExecutable, Path workingDirectory, String gameSlug, List<String> additionalArgs) {
        List<String> command = new ArrayList<>();
        command.add(protonExecutable.toAbsolutePath().toString());
        command.add("run");
        command.add(gameExecutable.toAbsolutePath().toString());
        if (additionalArgs != null) {
            command.addAll(additionalArgs);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        Path steamDir = findSteamDirectory();
        Path compatDataDir = getCompatDataDirectory(gameSlug);

        pb.environment().put("STEAM_COMPAT_CLIENT_INSTALL_PATH", steamDir.toAbsolutePath().toString());
        pb.environment().put("STEAM_COMPAT_DATA_PATH", compatDataDir.toAbsolutePath().toString());
        pb.environment().put("PROTON_LOG", "1");

        return pb;
    }
}
