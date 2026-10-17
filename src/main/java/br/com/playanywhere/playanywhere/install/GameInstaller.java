package br.com.playanywhere.playanywhere.install;

import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.application.usecase.InstallGameUseCase;
import br.com.playanywhere.playanywhere.application.usecase.InstallationProgressListener;
import br.com.playanywhere.playanywhere.drive.GameRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Instalador de jogos (fachada para compatibilidade e execução no sistema de arquivos).
 */
public class GameInstaller {
    private static final Logger LOGGER = Logger.getLogger(GameInstaller.class.getName());

    private final InstallGameUseCase installGameUseCase;
    private final ExeExtractor exeExtractor;

    public GameInstaller() {
        this.exeExtractor = new ExeExtractor();
        this.installGameUseCase = new InstallGameUseCase(GameRegistry.getInstance(), null, this.exeExtractor);
    }

    public GameInstaller(InstallGameUseCase installGameUseCase) {
        this.installGameUseCase = installGameUseCase;
        this.exeExtractor = new ExeExtractor();
    }

    public GameInstaller(InstallGameUseCase installGameUseCase, ExeExtractor exeExtractor) {
        this.installGameUseCase = installGameUseCase;
        this.exeExtractor = exeExtractor != null ? exeExtractor : new ExeExtractor();
    }

    public void install(GameInstallationRequest request) throws IOException {
        install(request, null);
    }

    public void install(GameInstallationRequest request, InstallationProgressListener progressListener) throws IOException {
        if (request == null) {
            LOGGER.severe("Tentativa de instalação com requisição nula.");
            throw new IllegalArgumentException("Requisição de instalação não pode ser nula");
        }
        LOGGER.info(() -> "GameInstaller delegando instalação de " + 
                (request.getProduct() != null ? request.getProduct().getTitle() : "jogo") + 
                " para " + request.getTargetPath());
        installGameUseCase.execute(request, progressListener);
    }

    /**
     * Descomprime diretamente um arquivo executável (.exe) para o diretório ou unidade de destino.
     */
    public ExtractionResult extractExe(Path exePath, Path targetDriveOrDir) throws IOException {
        return exeExtractor.extract(exePath, targetDriveOrDir, null);
    }

    /**
     * Descomprime diretamente um arquivo executável (.exe) para o diretório ou unidade de destino com listener de progresso.
     */
    public ExtractionResult extractExe(Path exePath, Path targetDriveOrDir, InstallationProgressListener progressListener) throws IOException {
        return exeExtractor.extract(exePath, targetDriveOrDir, progressListener);
    }

    /**
     * Descomprime diretamente um arquivo executável (.exe) para o diretório ou unidade de destino.
     */
    public ExtractionResult extractExe(File exeFile, File targetDriveOrDir) throws IOException {
        return exeExtractor.extract(exeFile, targetDriveOrDir);
    }

    /**
     * Descomprime diretamente um arquivo executável (.exe) para o diretório ou unidade de destino.
     */
    public ExtractionResult extractExe(String exePath, String targetDriveOrDir) throws IOException {
        return exeExtractor.extract(exePath, targetDriveOrDir);
    }

    public ExeExtractor getExeExtractor() {
        return exeExtractor;
    }
}
