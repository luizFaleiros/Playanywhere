package br.com.playanywhere.playanywhere.application.dto;

import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.Product;
import java.nio.file.Path;

/**
 * Record que representa uma requisição de instalação de jogo.
 */
public record GameInstallationRequest(
    Product product,
    InstallationType installationType,
    Path targetPath,
    Path sourcePath,
    boolean autoRunner,
    boolean renameDrive
) {
    public GameInstallationRequest(Product product, InstallationType installationType, Path targetPath, Path sourcePath, boolean autoRunner) {
        this(product, installationType, targetPath, sourcePath, autoRunner, true);
    }

    public Product getProduct() { return product; }
    public InstallationType getInstallationType() { return installationType; }
    public Path getTargetPath() { return targetPath; }
    public Path getSourcePath() { return sourcePath; }
    public boolean isAutoRunner() { return autoRunner; }
    public boolean isRenameDrive() { return renameDrive; }
}
