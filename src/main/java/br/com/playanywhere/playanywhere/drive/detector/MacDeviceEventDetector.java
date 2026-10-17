package br.com.playanywhere.playanywhere.drive.detector;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detector de eventos nativos para macOS.
 * Monitora o diretório /Volumes via Java NIO WatchService (FSEvents/kqueue).
 */
public class MacDeviceEventDetector implements DeviceEventDetector {
    private static final Logger LOGGER = Logger.getLogger(MacDeviceEventDetector.class.getName());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread watchThread;
    private DeviceEventListener listener;

    @Override
    public synchronized void start(DeviceEventListener listener) {
        if (running.get()) return;
        this.listener = listener;
        this.running.set(true);

        Path volumes = Paths.get("/Volumes");
        if (!Files.exists(volumes)) {
            LOGGER.warning("Diretório /Volumes não encontrado no macOS.");
            return;
        }

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            volumes.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY
            );

            this.watchThread = new Thread(this::runWatchLoop, "PlayAnywhere-MacVolumesWatcher");
            this.watchThread.setDaemon(true);
            this.watchThread.start();
            LOGGER.info("Detector /Volumes do macOS iniciado com sucesso.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível inicializar WatchService no macOS: " + e.getMessage(), e);
        }
    }

    private void runWatchLoop() {
        Path volumes = Paths.get("/Volumes");
        while (running.get() && watchService != null) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fullPath = volumes.resolve(ev.context());
                    String eventType = (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) ? "DETACH" : "ATTACH";

                    LOGGER.info(() -> "⚡ Evento macOS /Volumes capturado [" + eventType + "]: " + fullPath);
                    if (listener != null) {
                        listener.onDeviceEvent(fullPath.toString(), eventType);
                    }
                }
                boolean valid = key.reset();
                if (!valid) break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro no loop WatchService do macOS: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
            watchService = null;
        }
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
        LOGGER.info("Detector nativo macOS finalizado.");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }

    @Override
    public String getDetectorName() {
        return "macOS /Volumes WatchService (FSEvents)";
    }
}
