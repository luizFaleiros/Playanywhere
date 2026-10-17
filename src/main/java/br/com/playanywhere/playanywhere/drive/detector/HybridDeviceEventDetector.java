package br.com.playanywhere.playanywhere.drive.detector;

import java.util.logging.Logger;

/**
 * Orquestrador do modelo híbrido de detecção de dispositivos.
 * Seleciona e gerencia a implementação nativa orientada a eventos apropriada para o SO hospedeiro,
 * provendo ponto central de despacho para eventos imediatos.
 */
public class HybridDeviceEventDetector implements DeviceEventDetector {
    private static final Logger LOGGER = Logger.getLogger(HybridDeviceEventDetector.class.getName());

    private final DeviceEventDetector activeDetector;

    public HybridDeviceEventDetector() {
        this(resolveDefaultDetector());
    }

    public HybridDeviceEventDetector(DeviceEventDetector detector) {
        this.activeDetector = (detector != null) ? detector : resolveDefaultDetector();
        LOGGER.info(() -> "Detector nativo de eventos selecionado: " + activeDetector.getDetectorName());
    }

    private static DeviceEventDetector resolveDefaultDetector() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new WindowsDeviceEventDetector();
        } else if (os.contains("mac") || os.contains("darwin")) {
            return new MacDeviceEventDetector();
        } else {
            return new LinuxDeviceEventDetector();
        }
    }

    @Override
    public synchronized void start(DeviceEventListener listener) {
        if (activeDetector != null && activeDetector.isAvailable()) {
            activeDetector.start(listener);
        }
    }

    @Override
    public synchronized void stop() {
        if (activeDetector != null) {
            activeDetector.stop();
        }
    }

    @Override
    public boolean isRunning() {
        return activeDetector != null && activeDetector.isRunning();
    }

    @Override
    public boolean isAvailable() {
        return activeDetector != null && activeDetector.isAvailable();
    }

    @Override
    public String getDetectorName() {
        return activeDetector != null ? activeDetector.getDetectorName() : "Nenhum detector nativo disponível";
    }

    public DeviceEventDetector getActiveDetector() {
        return activeDetector;
    }
}
