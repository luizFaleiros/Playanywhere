package br.com.playanywhere.playanywhere.drive.detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Detector de eventos nativos para Windows.
 * Intercepta notificações de conexão e desconexão de volumes (Win32_VolumeChangeEvent)
 * via eventos do WMI / PowerShell e monitoramento de raízes de disco Win32.
 */
public class WindowsDeviceEventDetector implements DeviceEventDetector {
    private static final Logger LOGGER = Logger.getLogger(WindowsDeviceEventDetector.class.getName());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread eventThread;
    private Process psProcess;
    private DeviceEventListener listener;
    private final Set<String> knownRoots = Collections.synchronizedSet(new HashSet<>());

    @Override
    public synchronized void start(DeviceEventListener listener) {
        if (running.get()) return;
        this.listener = listener;
        this.running.set(true);

        // Inicializa raízes conhecidas
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File r : roots) {
                knownRoots.add(r.getAbsolutePath());
            }
        }

        // Inicia escuta via PowerShell WMI Event subscription
        eventThread = new Thread(this::listenWindowsEvents, "PlayAnywhere-WindowsWmiWatcher");
        eventThread.setDaemon(true);
        eventThread.start();
        LOGGER.info("Detector de eventos do Windows (WMI/Win32) iniciado.");
    }

    private void listenWindowsEvents() {
        try {
            // Script PowerShell que se inscreve em Win32_VolumeChangeEvent e emite eventos de chegada (2) e remoção (3)
            String psCommand = "Register-WmiEvent -Class Win32_VolumeChangeEvent -SourceIdentifier 'PA_VolChange'; " +
                    "while ($true) { " +
                    "  $event = Wait-Event -SourceIdentifier 'PA_VolChange'; " +
                    "  $type = $event.SourceEventArgs.NewEvent.EventType; " +
                    "  $drive = $event.SourceEventArgs.NewEvent.DriveName; " +
                    "  Write-Output \"EVENT:$type:$drive\"; " +
                    "  Remove-Event -EventIdentifier $event.EventIdentifier; " +
                    "}";

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand);
            pb.redirectErrorStream(true);
            psProcess = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.startsWith("EVENT:")) {
                        String[] parts = line.split(":", 3);
                        if (parts.length >= 3) {
                            String eventCode = parts[1]; // 2 = Arrived, 3 = Removed
                            String driveName = parts[2];
                            String type = "2".equals(eventCode) ? "ATTACH" : ("3".equals(eventCode) ? "DETACH" : "CHANGE");
                            LOGGER.info(() -> "⚡ Sinal nativo Win32_VolumeChangeEvent capturado: " + type + " em " + driveName);
                            if (listener != null) {
                                listener.onDeviceEvent(driveName, type);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Escuta via PowerShell WMI não disponível ou interrompida: " + e.getMessage());
        }
    }

    @Override
    public synchronized void stop() {
        running.set(false);
        if (psProcess != null) {
            psProcess.destroy();
            psProcess = null;
        }
        if (eventThread != null) {
            eventThread.interrupt();
            eventThread = null;
        }
        LOGGER.info("Detector nativo Windows finalizado.");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAvailable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    @Override
    public String getDetectorName() {
        return "Windows WMI Win32_VolumeChangeEvent / Shell Events";
    }
}
