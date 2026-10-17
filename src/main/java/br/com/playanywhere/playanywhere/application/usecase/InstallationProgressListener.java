package br.com.playanywhere.playanywhere.application.usecase;

/**
 * Interface funcional para acompanhamento de progresso de download e instalação de jogos.
 */
@FunctionalInterface
public interface InstallationProgressListener {
    void onProgress(double progress, String statusMessage);
}
