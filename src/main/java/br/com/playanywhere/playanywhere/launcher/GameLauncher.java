package br.com.playanywhere.playanywhere.launcher;

import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Lançador central de jogos portáteis do PlayAnywhere.
 * Suporta execução nativa no Windows e execução compatível com Wine/Proton no Linux.
 */
public class GameLauncher {
    private static final Logger LOGGER = Logger.getLogger(GameLauncher.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static PlatformType simulatedPlatform;

    public static void setSimulatedPlatform(PlatformType platform) {
        simulatedPlatform = platform;
    }

    public static void resetSimulatedPlatform() {
        simulatedPlatform = null;
    }

    public PlatformType getCurrentPlatform() {
        return simulatedPlatform != null ? simulatedPlatform : PlatformType.currentPlatform();
    }

    /**
     * Tenta ler o arquivo playanywhere.json em um diretório.
     */
    public Optional<PlayAnywhereMetadata> readMetadata(Path gameDirectory) {
        if (gameDirectory == null || !Files.exists(gameDirectory)) {
            return Optional.empty();
        }
        Path metaFile = findMetadataFile(gameDirectory);
        if (metaFile != null && Files.exists(metaFile) && Files.isRegularFile(metaFile)) {
            try {
                PlayAnywhereMetadata meta = MAPPER.readValue(metaFile.toFile(), PlayAnywhereMetadata.class);
                return Optional.ofNullable(meta);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao ler playanywhere.json em " + gameDirectory + ": " + e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    private Path findMetadataFile(Path gameDirectory) {
        Path direct = gameDirectory.resolve("playanywhere.json");
        if (Files.exists(direct)) {
            return direct;
        }
        try (Stream<Path> stream = Files.list(gameDirectory)) {
            return stream.filter(Files::isRegularFile)
                         .filter(f -> f.getFileName().toString().equalsIgnoreCase("playanywhere.json"))
                         .findFirst()
                         .orElse(direct);
        } catch (Exception e) {
            return direct;
        }
    }

    /**
     * Localiza o executável do jogo a partir dos metadados ou varredura de arquivos.
     */
    public Path findExecutable(Path gameDirectory, PlayAnywhereMetadata metadata) {
        if (gameDirectory == null || !Files.exists(gameDirectory)) {
            return null;
        }

        // 1. Tentar executável especificado nos metadados
        if (metadata != null && metadata.executable() != null && !metadata.executable().trim().isEmpty()) {
            Path candidate = gameDirectory.resolve(metadata.executable());
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        // 2. Busca por arquivos executáveis comuns
        String[] preferredNames = {"game.exe", "launcher.exe", "start.exe", "start_game.sh", "game.sh", "autorun.exe"};
        for (String name : preferredNames) {
            Path candidate = gameDirectory.resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        // 3. Varredura recursiva por .exe ou .sh
        try (Stream<Path> stream = Files.walk(gameDirectory, 2)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String lower = p.getFileName().toString().toLowerCase();
                    return lower.endsWith(".exe") || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".app");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao varrer executáveis em " + gameDirectory + ": " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Lança o jogo a partir do diretório fornecido.
     */
    public LaunchResult launchGame(Path gameDirectory) {
        Optional<PlayAnywhereMetadata> meta = readMetadata(gameDirectory);
        return launchGame(gameDirectory, meta.orElse(null));
    }

    /**
     * Lança o jogo utilizando os metadados fornecidos e adaptando ao SO e ambiente de compatibilidade.
     */
    public LaunchResult launchGame(Path gameDirectory, PlayAnywhereMetadata metadata) {
        String gameTitle = metadata != null ? metadata.title() : (gameDirectory != null ? gameDirectory.getFileName().toString() : "Jogo");
        LOGGER.info(() -> "Iniciando lançamento do jogo: " + gameTitle + " em " + gameDirectory);

        if (gameDirectory == null || !Files.exists(gameDirectory)) {
            return LaunchResult.failure("NONE", "Diretório do jogo não encontrado: " + gameDirectory, gameTitle);
        }

        Path executable = findExecutable(gameDirectory, metadata);
        if (executable == null) {
            LOGGER.severe(() -> "Nenhum executável encontrado para " + gameTitle + " em " + gameDirectory);
            return LaunchResult.failure("NONE", "Nenhum arquivo executável (.exe, .sh) encontrado em " + gameDirectory, gameTitle);
        }

        PlatformType platform = getCurrentPlatform();
        List<String> extraArgs = metadata != null && metadata.launchArguments() != null ? metadata.launchArguments() : List.of();

        LOGGER.info(() -> "Plataforma detectada: " + platform + ", executável: " + executable);

        try {
            if (platform == PlatformType.WINDOWS) {
                return launchOnWindows(executable, gameDirectory, extraArgs, gameTitle);
            } else if (platform == PlatformType.LINUX) {
                return launchOnLinux(executable, gameDirectory, metadata, extraArgs, gameTitle);
            } else {
                return launchOnGenericPlatform(executable, gameDirectory, extraArgs, gameTitle);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha durante o lançamento de " + gameTitle + ": " + e.getMessage(), e);
            return LaunchResult.failure("ERROR", "Erro ao executar processo: " + e.getMessage(), gameTitle);
        }
    }

    private LaunchResult launchOnWindows(Path executable, Path gameDirectory, List<String> extraArgs, String gameTitle) throws IOException {
        LOGGER.info(() -> "Executando nativamente no Windows: " + executable);
        List<String> command = new ArrayList<>();
        command.add(executable.toAbsolutePath().toString());
        command.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDirectory.toFile());
        pb.inheritIO();

        Process process = pb.start();
        LOGGER.info(() -> "Processo Windows iniciado com sucesso para: " + gameTitle);
        return LaunchResult.success("WINDOWS_NATIVE", command, process, gameTitle);
    }

    private LaunchResult launchOnLinux(Path executable, Path gameDirectory, PlayAnywhereMetadata metadata, List<String> extraArgs, String gameTitle) throws IOException {
        String exeName = executable.getFileName().toString().toLowerCase();

        // 1. Se for script Linux nativo (.sh) ou binário sem extensão .exe
        if (exeName.endsWith(".sh")) {
            LOGGER.info(() -> "Executando script nativo do Linux: " + executable);
            executable.toFile().setExecutable(true);
            List<String> command = new ArrayList<>();
            command.add("/bin/bash");
            command.add(executable.toAbsolutePath().toString());
            command.addAll(extraArgs);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(gameDirectory.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return LaunchResult.success("LINUX_NATIVE", command, process, gameTitle);
        }

        // 2. Para jogos Windows (.exe): Tentar Proton primeiro
        String requestedRunner = metadata != null ? metadata.runnerType() : "AUTO";
        String slug = metadata != null ? metadata.slug() : "game";

        if (!"WINE_ONLY".equalsIgnoreCase(requestedRunner)) {
            Optional<Path> protonOpt = ProtonDetector.findProtonExecutable();
            if (protonOpt.isPresent()) {
                Path protonPath = protonOpt.get();
                LOGGER.info(() -> "Iniciando jogo no Linux via Proton: " + protonPath);
                ProcessBuilder pb = ProtonDetector.buildProtonProcess(protonPath, executable, gameDirectory, slug, extraArgs);
                pb.inheritIO();
                try {
                    Process process = pb.start();
                    LOGGER.info(() -> "Jogo iniciado com sucesso via Valve Proton!");
                    return LaunchResult.success("PROTON", pb.command(), process, gameTitle);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Falha ao iniciar com Proton, tentando fallback com Wine: " + e.getMessage(), e);
                }
            } else {
                LOGGER.info("Proton não detectado no sistema, verificando Wine...");
            }
        }

        // 3. Fallback: Tentar Wine
        if (!"PROTON_ONLY".equalsIgnoreCase(requestedRunner)) {
            Optional<Path> wineOpt = WineDetector.findWineExecutable();
            if (wineOpt.isPresent()) {
                Path winePath = wineOpt.get();
                LOGGER.info(() -> "Iniciando jogo no Linux via Wine: " + winePath);
                ProcessBuilder pb = WineDetector.buildWineProcess(winePath, executable, gameDirectory, extraArgs);
                pb.inheritIO();
                try {
                    Process process = pb.start();
                    LOGGER.info(() -> "Jogo iniciado com sucesso via Wine!");
                    return LaunchResult.success("WINE", pb.command(), process, gameTitle);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Falha ao iniciar com Wine: " + e.getMessage(), e);
                }
            } else {
                LOGGER.warning("Wine não detectado no sistema.");
            }
        }

        // 4. Se houver script fallback no diretório
        Path fallbackScript = gameDirectory.resolve("start_game.sh");
        if (Files.exists(fallbackScript)) {
            LOGGER.info(() -> "Tentando script de fallback: " + fallbackScript);
            fallbackScript.toFile().setExecutable(true);
            List<String> command = List.of("/bin/bash", fallbackScript.toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(gameDirectory.toFile());
            pb.inheritIO();
            Process process = pb.start();
            return LaunchResult.success("LINUX_FALLBACK_SCRIPT", command, process, gameTitle);
        }

        // 5. Nenhum executor compatível encontrado
        String msg = "Não foi possível abrir o jogo Windows (" + exeName + ") no Linux: nem Valve Proton nem Wine foram encontrados no sistema.";
        LOGGER.severe(msg);
        return LaunchResult.failure("LINUX_NO_COMPAT_LAYER", msg, gameTitle);
    }

    private LaunchResult launchOnGenericPlatform(Path executable, Path gameDirectory, List<String> extraArgs, String gameTitle) throws IOException {
        LOGGER.info(() -> "Executando em plataforma genérica: " + executable);
        List<String> command = new ArrayList<>();
        command.add(executable.toAbsolutePath().toString());
        command.addAll(extraArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameDirectory.toFile());
        pb.inheritIO();

        Process process = pb.start();
        return LaunchResult.success("GENERIC", command, process, gameTitle);
    }
}
