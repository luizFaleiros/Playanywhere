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

/**
 * Detector e configurador do runtime Wine para execução de jogos Windows no Linux.
 */
public class WineDetector {
    private static final Logger LOGGER = Logger.getLogger(WineDetector.class.getName());
    private static Path customWinePath;
    private static boolean forceDisabled = false;

    public static void setCustomWinePath(Path path) {
        customWinePath = path;
    }

    public static void setForceDisabled(boolean disabled) {
        forceDisabled = disabled;
    }

    public static void resetCustomWinePath() {
        customWinePath = null;
        forceDisabled = false;
    }

    /**
     * Verifica se o Wine está disponível no sistema.
     */
    public static boolean isWineAvailable() {
        return findWineExecutable().isPresent();
    }

    /**
     * Localiza o executável do Wine (wine ou wine64).
     */
    public static Optional<Path> findWineExecutable() {
        if (forceDisabled) {
            return Optional.empty();
        }

        if (customWinePath != null) {
            return Files.exists(customWinePath) ? Optional.of(customWinePath) : Optional.empty();
        }

        // 1. Variável de ambiente WINE_PATH
        String envWine = System.getenv("WINE_PATH");
        if (envWine != null && !envWine.trim().isEmpty()) {
            Path p = Paths.get(envWine);
            if (isValidWineExecutable(p)) return Optional.of(p);
        }

        // 2. Diretórios comuns do sistema Linux
        List<Path> commonPaths = List.of(
            Paths.get("/usr/bin/wine"),
            Paths.get("/usr/bin/wine64"),
            Paths.get("/usr/local/bin/wine"),
            Paths.get("/usr/local/bin/wine64"),
            Paths.get("/opt/wine-staging/bin/wine"),
            Paths.get("/opt/wine/bin/wine")
        );

        for (Path p : commonPaths) {
            if (isValidWineExecutable(p)) {
                LOGGER.info(() -> "Wine localizado em: " + p);
                return Optional.of(p);
            }
        }

        // 3. Checar wine / wine64 no PATH
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            for (String binName : List.of("wine64", "wine")) {
                Path p = Paths.get(dir, binName);
                if (isValidWineExecutable(p)) {
                    LOGGER.info(() -> "Wine encontrado no PATH: " + p);
                    return Optional.of(p);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isValidWineExecutable(Path path) {
        return path != null && Files.exists(path) && (Files.isExecutable(path) || Files.isRegularFile(path));
    }

    /**
     * Retorna o diretório do prefixo Wine (WINEPREFIX).
     */
    public static Path getWinePrefixDirectory() {
        Path winePrefix = Paths.get(System.getProperty("user.home", "/home"), ".playanywhere", "wineprefix");
        try {
            if (!Files.exists(winePrefix)) {
                Files.createDirectories(winePrefix);
            }
        } catch (IOException e) {
            LOGGER.warning("Não foi possível criar WINEPREFIX: " + e.getMessage());
        }
        return winePrefix;
    }

    /**
     * Cria e configura o ProcessBuilder para executar o jogo via Wine.
     */
    public static ProcessBuilder buildWineProcess(Path wineExecutable, Path gameExecutable, Path workingDirectory, List<String> additionalArgs) {
        List<String> command = new ArrayList<>();
        command.add(wineExecutable.toAbsolutePath().toString());
        command.add(gameExecutable.toAbsolutePath().toString());
        if (additionalArgs != null) {
            command.addAll(additionalArgs);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }

        Path winePrefix = getWinePrefixDirectory();
        pb.environment().put("WINEPREFIX", winePrefix.toAbsolutePath().toString());
        pb.environment().put("WINEDEBUG", "-all");

        return pb;
    }
}
