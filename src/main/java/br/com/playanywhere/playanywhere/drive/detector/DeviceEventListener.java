package br.com.playanywhere.playanywhere.drive.detector;

/**
 * Listener para eventos nativos de hardware e sistema de arquivos emitidos pelo sistema operacional.
 */
@FunctionalInterface
public interface DeviceEventListener {
    /**
     * Invocado quando um dispositivo de armazenamento é conectado, montado, alterado ou removido.
     *
     * @param path Caminho do ponto de montagem ou dispositivo (pode ser null se genérico)
     * @param eventType Tipo de evento ("ATTACH", "DETACH", "CHANGE")
     */
    void onDeviceEvent(String path, String eventType);
}
