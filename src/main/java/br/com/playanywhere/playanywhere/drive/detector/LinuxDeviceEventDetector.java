package br.com.playanywhere.playanywhere.drive.detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detector de eventos nativos para Linux.
 * Utiliza Java NIO WatchService (Inotify) nos diretórios de montagem (/media, /run/media, /mnt)
 * e, opcionalmente, escuta eventos do UDisks2/Udev para reação instantânea (0ms de latência)
 * com consumo zero de CPU em repouso.
 */
public class LinuxDeviceEventDetector implements DeviceEventDetector {
    private static final Logger LOGGER = Logger.getLogger(LinuxDeviceEventDetector.class.getName());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private WatchService watchService;
    private Thread watchThread;
    private Thread udisksThread;
    private Process udisksProcess;
    private DeviceEventListener listener;

    @Override
    public synchronized void start(DeviceEventListener listener) {
        if (running.get()) {
            return;
        }
        this.listener = listener;
        this.running.set(true);

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            registerMountPoints();

            this.watchThread = new Thread(this::runWatchLoop, "PlayAnywhere-LinuxInotifyWatcher");
            this.watchThread.setDaemon(true);
            this.watchThread.start();
            LOGGER.info("Detector Inotify do Linux iniciado com sucesso.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível inicializar WatchService no Linux: " + e.getMessage(), e);
        }

        startUdisksListener();
    }

    private void registerMountPoints() {
        if (watchService == null) return;

        String user = System.getProperty("user.name", "");
        List<String> targetDirs = new ArrayList<>(List.of(
                "/media",
                "/mnt",
                "/run/media"
        ));

        if (!user.isEmpty()) {
            targetDirs.add("/run/media/" + user);
            targetDirs.add("/media/" + user);
        }

        for (String dirStr : targetDirs) {
            Path path = Paths.get(dirStr);
            registerDirectory(path);
        }
    }

    public synchronized void registerDirectory(Path path) {
        if (watchService == null || path == null) return;
        try {
            if (Files.exists(path) && Files.isDirectory(path)) {
                WatchKey key = path.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                );
                watchKeys.put(key, path);
                LOGGER.fine(() -> "Monitorando diretório de montagem Linux via Inotify: " + path);
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Não foi possível registrar Inotify em " + path + ": " + e.getMessage());
        }
    }

    private void runWatchLoop() {
        while (running.get() && watchService != null) {
            try {
                WatchKey key = watchService.take();
                Path parent = watchKeys.get(key);

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = (parent != null) ? parent.resolve(filename) : filename;

                    String eventType = (kind == StandardWatchEventKinds.ENTRY_DELETE) ? "DETACH" : "ATTACH";
                    LOGGER.info(() -> "⚡ Evento nativo de montagem detectado via Inotify [" + eventType + "]: " + fullPath);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                        registerDirectory(fullPath);
                    }

                    if (listener != null) {
                        listener.onDeviceEvent(fullPath.toString(), eventType);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro no loop Inotify: " + e.getMessage(), e);
            }
        }
    }

    private void startUdisksListener() {
        udisksThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("udisksctl", "monitor");
                pb.redirectErrorStream(true);
                udisksProcess = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(udisksProcess.getInputStream()))) {
                    String line;
                    while (running.get() && (line = reader.readLine()) != null) {
                        final String currentLine = line;
                        // Ignora jobs efêmeros do UDisks2 (ex: /jobs/7) que causam falsos positivos de ATTACH e DETACH
                        if (currentLine.contains("/jobs/")) {
                            continue;
                        }
                        if (currentLine.contains("Added") || currentLine.contains("Mounted") || currentLine.contains("interfaces added") || currentLine.contains("DeviceAdded")) {
                            LOGGER.info(() -> "⚡ Sinal UDisks2 capturado: " + currentLine);
                            if (listener != null) {
                                listener.onDeviceEvent(null, "ATTACH");
                            }
                        } else if (currentLine.contains("Removed") || currentLine.contains("Unmounted") || currentLine.contains("DeviceRemoved")) {
                            LOGGER.info(() -> "⚡ Sinal UDisks2 (Remoção) capturado: " + currentLine);
                            if (listener != null) {
                                listener.onDeviceEvent(null, "DETACH");
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // udisksctl pode não estar instalado em alguns ambientes mínimos, inotify atua como principal
            }
        }, "PlayAnywhere-LinuxUDisksWatcher");
        udisksThread.setDaemon(true);
        udisksThread.start();
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

        if (udisksProcess != null) {
            udisksProcess.destroy();
            udisksProcess = null;
        }

        if (udisksThread != null) {
            udisksThread.interrupt();
            udisksThread = null;
        }

        watchKeys.clear();
        LOGGER.info("Detector nativo Linux finalizado.");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux") || os.contains("unix");
    }

    @Override
    public String getDetectorName() {
        return "Linux Inotify (WatchService) + UDisks2 Event Stream";
    }
}
