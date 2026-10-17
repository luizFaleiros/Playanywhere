package br.com.playanywhere.playanywhere.drive.detector;

/**
 * Interface comum para detectores nativos orientados a eventos de dispositivos de armazenamento.
 */
public interface DeviceEventDetector {

    /**
     * Inicia a escuta por sinais/eventos nativos do sistema operacional.
     *
     * @param listener Callback invocado quando um evento é interceptado.
     */
    void start(DeviceEventListener listener);

    /**
     * Interrompe a escuta de eventos nativos.
     */
    void stop();

    /**
     * Retorna se o detector está ativo e escutando eventos.
     */
    boolean isRunning();

    /**
     * Retorna se a infraestrutura necessária para este detector está disponível no ambiente atual.
     */
    boolean isAvailable();

    /**
     * Nome descritivo da estratégia nativa utilizada.
     */
    String getDetectorName();
}
