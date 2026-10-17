package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.drive.detector.DeviceEventDetector;
import br.com.playanywhere.playanywhere.drive.detector.HybridDeviceEventDetector;
import br.com.playanywhere.playanywhere.launcher.GameLauncher;
import br.com.playanywhere.playanywhere.launcher.LaunchResult;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Monitor de montagem automática de unidades de armazenamento (Pen Drives, HDs externos, CDs/DVDs).
 * Utiliza o modelo HÍBRIDO:
 * 1. Primário: Orientado a eventos do SO em tempo real (Inotify, UDisks2, Win32 WMI, macOS FSEvents) para resposta instantânea (0ms).
 * 2. Fallback: Varredura periódica em segundo plano (polling) para garantia e resiliência total de detecção.
 */
public class MountMonitor {
    private static final Logger LOGGER = Logger.getLogger(MountMonitor.class.getName());
    private static final MountMonitor INSTANCE = new MountMonitor();

    private final DriveManager driveManager = new DriveManager();
    private final GameRegistry gameRegistry = GameRegistry.getInstance();
    private GameLauncher gameLauncher = new GameLauncher();
    private DeviceEventDetector eventDetector;

    private final Set<String> knownMounts = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> launchedGamePaths = Collections.synchronizedSet(new HashSet<>());
    private final List<MountListener> listeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService executor;

    public static MountMonitor getInstance() {
        return INSTANCE;
    }

    public void setGameLauncher(GameLauncher gameLauncher) {
        this.gameLauncher = (gameLauncher != null) ? gameLauncher : new GameLauncher();
    }

    public void setEventDetector(DeviceEventDetector eventDetector) {
        this.eventDetector = eventDetector;
    }

    public DeviceEventDetector getEventDetector() {
        return eventDetector;
    }

    public boolean isEventDrivenActive() {
        return eventDetector != null && eventDetector.isRunning();
    }

    public String getActiveStrategyName() {
        if (isEventDrivenActive()) {
            return "Híbrido (Tempo Real: " + eventDetector.getDetectorName() + " + Fallback Polling)";
        }
        return "Polling Contínuo";
    }

    public void addMountListener(MountListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeMountListener(MountListener listener) {
        listeners.remove(listener);
    }

    public synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }

        // 1. Inicializa o detector primário orientado a eventos nativos
        if (eventDetector == null) {
            eventDetector = new HybridDeviceEventDetector();
        }
        try {
            eventDetector.start((path, eventType) -> {
                LOGGER.info(() -> "⚡ Evento nativo de armazenamento recebido pelo MountMonitor: tipo=" + eventType + ", caminho=" + path);
                if ("ATTACH".equalsIgnoreCase(eventType)) {
                    if (path != null && !path.trim().isEmpty()) {
                        handleMount(path);
                    }
                    triggerImmediateCheckWithRetries();
                } else if ("DETACH".equalsIgnoreCase(eventType)) {
                    triggerImmediateCheck();
                }
            });
            LOGGER.info(() -> "Detector nativo iniciado: " + eventDetector.getDetectorName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Aviso ao iniciar detector nativo de eventos: " + e.getMessage(), e);
        }

        // 2. Inicializa o fallback por polling em segundo plano (contingência e segurança)
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayAnywhere-MountMonitorFallback");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::checkMounts, 0, 5, TimeUnit.SECONDS);
        LOGGER.info(() -> "Modelo Híbrido ativado com sucesso: " + getActiveStrategyName());
    }

    public synchronized void stop() {
        if (eventDetector != null) {
            LOGGER.info("Parando detector nativo de eventos...");
            eventDetector.stop();
        }
        if (executor != null) {
            LOGGER.info("Parando executor de fallback do monitor de pendrives...");
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Dispara uma verificação imediata das unidades de forma assíncrona.
     */
    public void triggerImmediateCheck() {
        if (executor != null && !executor.isShutdown()) {
            executor.submit(this::checkMounts);
        } else {
            new Thread(this::checkMounts, "PlayAnywhere-InstantCheck").start();
        }
    }

    /**
     * Dispara verificação imediata e retentativas escalonadas para lidar com
     * latência de montagem do sistema de arquivos após detecção do dispositivo.
     */
    public void triggerImmediateCheckWithRetries() {
        triggerImmediateCheck();
        if (executor != null && !executor.isShutdown()) {
            executor.schedule(this::checkMounts, 400, TimeUnit.MILLISECONDS);
            executor.schedule(this::checkMounts, 1200, TimeUnit.MILLISECONDS);
            executor.schedule(this::checkMounts, 2500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Verifica unidades conectadas e processa novos dispositivos montados.
     */
    public void checkMounts() {
        try {
            List<String> currentMounts = driveManager.getMountableDrives();
            Set<String> currentSet = new HashSet<>(currentMounts);

            // Limpa registros de mídias que foram desconectadas
            boolean hadRemovals = false;
            if (knownMounts.removeIf(mount -> !currentSet.contains(mount))) {
                hadRemovals = true;
            }
            if (launchedGamePaths.removeIf(pathStr -> {
                Path p = Paths.get(pathStr);
                return !Files.exists(p);
            })) {
                hadRemovals = true;
            }

            if (hadRemovals) {
                gameRegistry.pruneMissingGames();
                notifyMountsChanged();
            }

            for (String mount : currentMounts) {
                if (!knownMounts.contains(mount)) {
                    LOGGER.info(() -> "Novo dispositivo de armazenamento montado detectado: " + mount);
                    knownMounts.add(mount);
                }
                handleMount(mount);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro durante verificação de montagens de unidades: " + e.getMessage(), e);
        }
    }

    /**
     * Processa um ponto de montagem específico, procurando por arquivos playanywhere.json.
     */
    public void handleMount(String mountPoint) {
        if (mountPoint == null || mountPoint.trim().isEmpty()) {
            return;
        }
        Path rootPath = Paths.get(mountPoint);
        if (!Files.exists(rootPath)) {
            return;
        }

        List<Path> candidateGameDirs = findPlayAnywhereGameDirectories(rootPath);
        if (candidateGameDirs.isEmpty()) {
            LOGGER.fine(() -> "Nenhum arquivo playanywhere.json encontrado no dispositivo: " + mountPoint);
            return;
        }

        for (Path gameDir : candidateGameDirs) {
            processGameDirectory(rootPath, gameDir);
        }
    }

    private List<Path> findPlayAnywhereGameDirectories(Path mountRoot) {
        List<Path> result = new ArrayList<>();
        if (mountRoot == null || !Files.exists(mountRoot)) {
            return result;
        }

        // 1. playanywhere.json na raiz do ponto de montagem
        if (containsPlayAnywhereMetadata(mountRoot)) {
            result.add(mountRoot);
        }

        // 2. Varredura recursiva até profundidade 4 nos subdiretórios
        try (Stream<Path> stream = Files.walk(mountRoot, 4)) {
            stream.filter(Files::isDirectory)
                  .filter(this::containsPlayAnywhereMetadata)
                  .forEach(dir -> {
                      if (!result.contains(dir)) {
                          result.add(dir);
                      }
                  });
        } catch (Exception e) {
            LOGGER.fine(() -> "Aviso ao varrer diretórios em " + mountRoot + ": " + e.getMessage());
        }

        return result;
    }

    private boolean containsPlayAnywhereMetadata(Path dir) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                         .anyMatch(p -> p.getFileName().toString().equalsIgnoreCase("playanywhere.json"));
        } catch (Exception e) {
            return false;
        }
    }

    private void processGameDirectory(Path driveRoot, Path gameDir) {
        String key = gameDir.toAbsolutePath().normalize().toString();
        if (launchedGamePaths.contains(key)) {
            LOGGER.fine(() -> "Jogo em " + key + " já foi processado nesta conexão.");
            return;
        }

        Optional<PlayAnywhereMetadata> metadataOpt = gameLauncher.readMetadata(gameDir);
        PlayAnywhereMetadata metadata = metadataOpt.orElseGet(() -> 
            PlayAnywhereMetadata.createDefault(
                new Product(0, gameDir.getFileName().toString(), "game", 0),
                "Pen Drive",
                "Pen Drive",
                true
            )
        );

        LOGGER.info(() -> "🎮 Jogo identificado no pendrive: " + metadata.title() + " em " + gameDir);
        launchedGamePaths.add(key);

        // Registra o jogo no GameRegistry local primeiro para que esteja disponível no carregamento da UI
        try {
            Product product = metadata.toProduct();
            gameRegistry.registerGame(product, gameDir, metadata.autoRunner());
        } catch (Exception e) {
            LOGGER.warning("Aviso ao registrar jogo no GameRegistry: " + e.getMessage());
        }

        // Notifica detecção para a interface gráfica
        notifyGameDetected(driveRoot, gameDir, metadata);

        // Se configurado para auto-executar ou conexão automática de pendrive
        if (metadata.autoRunner()) {
            LOGGER.info(() -> "🚀 Lançando jogo automaticamente: " + metadata.title() + "...");
            LaunchResult launchResult = gameLauncher.launchGame(gameDir, metadata);

            if (launchResult.success()) {
                LOGGER.info(() -> "✅ Jogo " + metadata.title() + " aberto com sucesso via " + launchResult.runnerUsed());
                notifyGameLaunched(metadata, launchResult);
            } else {
                LOGGER.severe(() -> "❌ Falha ao abrir jogo " + metadata.title() + ": " + launchResult.errorMessage());
                notifyGameLaunchFailed(metadata, launchResult.errorMessage());
            }
        }
    }

    private void notifyMountsChanged() {
        for (MountListener listener : listeners) {
            try {
                listener.onMountsChanged();
            } catch (Exception e) {
                LOGGER.warning("Erro no listener de alteração de montagens: " + e.getMessage());
            }
        }
    }

    private void notifyGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
        for (MountListener listener : listeners) {
            try {
                listener.onGameDetected(drivePath, gameDir, metadata);
            } catch (Exception e) {
                LOGGER.warning("Erro no listener de detecção: " + e.getMessage());
            }
        }
    }

    private void notifyGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {
        for (MountListener listener : listeners) {
            try {
                listener.onGameLaunched(metadata, result);
            } catch (Exception e) {
                LOGGER.warning("Erro no listener de lançamento: " + e.getMessage());
            }
        }
    }

    private void notifyGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {
        for (MountListener listener : listeners) {
            try {
                listener.onGameLaunchFailed(metadata, error);
            } catch (Exception e) {
                LOGGER.warning("Erro no listener de falha: " + e.getMessage());
            }
        }
    }

    public Set<String> getKnownMounts() {
        return Collections.unmodifiableSet(knownMounts);
    }

    public Set<String> getLaunchedGamePaths() {
        return Collections.unmodifiableSet(launchedGamePaths);
    }
}
