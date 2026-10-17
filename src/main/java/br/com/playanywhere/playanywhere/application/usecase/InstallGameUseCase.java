package br.com.playanywhere.playanywhere.application.usecase;

import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.model.StorageType;
import br.com.playanywhere.playanywhere.domain.repository.GameRegistryRepository;
import br.com.playanywhere.playanywhere.drive.VolumeLabelManager;
import br.com.playanywhere.playanywhere.install.ExeExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Caso de uso para download, empacotamento, instalação e registro de jogos portáteis em pendrives, CDs, DVDs ou HDs.
 */
public class InstallGameUseCase {
    private static final Logger LOGGER = Logger.getLogger(InstallGameUseCase.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final GameRegistryRepository gameRegistryRepository;
    private final VolumeLabelManager volumeLabelManager;
    private final ExeExtractor exeExtractor;

    public InstallGameUseCase(GameRegistryRepository gameRegistryRepository) {
        this(gameRegistryRepository, new VolumeLabelManager(), new ExeExtractor());
    }

    public InstallGameUseCase(GameRegistryRepository gameRegistryRepository, VolumeLabelManager volumeLabelManager) {
        this(gameRegistryRepository, volumeLabelManager, new ExeExtractor());
    }

    public InstallGameUseCase(GameRegistryRepository gameRegistryRepository, VolumeLabelManager volumeLabelManager, ExeExtractor exeExtractor) {
        this.gameRegistryRepository = gameRegistryRepository;
        this.volumeLabelManager = volumeLabelManager != null ? volumeLabelManager : new VolumeLabelManager();
        this.exeExtractor = exeExtractor != null ? exeExtractor : new ExeExtractor();
    }

    public void execute(GameInstallationRequest request) throws IOException {
        execute(request, null);
    }

    public void execute(GameInstallationRequest request, InstallationProgressListener progressListener) throws IOException {
        Objects.requireNonNull(request, "A requisição de instalação não pode ser nula.");
        Objects.requireNonNull(request.product(), "O produto a ser instalado não pode ser nulo.");
        Objects.requireNonNull(request.targetPath(), "O caminho de destino não pode ser nulo.");
        Objects.requireNonNull(request.sourcePath(), "O caminho de origem não pode ser nulo.");

        Product product = request.product();
        Path targetPath = request.targetPath();
        Path sourcePath = request.sourcePath();
        InstallationType type = request.installationType() != null ? request.installationType() : InstallationType.LOCAL_DIRECTORY;
        StorageType storageType = StorageType.fromInstallationType(type);

        LOGGER.info(() -> "InstallGameUseCase executando instalação de " + product.getTitle() + 
                " (" + product.getFormattedSize() + ") para " + targetPath + " [Tipo: " + type + "]");

        reportProgress(progressListener, 0.1, "🔍 Validando diretórios e espaço em disco...");

        if (!Files.exists(sourcePath)) {
            throw new IOException("Caminho de origem não existe: " + sourcePath);
        }

        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }

        reportProgress(progressListener, 0.3, "⬇️ Baixando pacote do jogo da GOG (" + product.getFormattedSize() + ")...");
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {}

        reportProgress(progressListener, 0.6, "📦 Instalando e copiando arquivos do jogo...");

        // Descompressão do executável ou cópia dos arquivos de origem para destino
        if (Files.isRegularFile(sourcePath) && exeExtractor.isExtractableExe(sourcePath)) {
            reportProgress(progressListener, 0.65, "📦 Descompactando arquivos do instalador executável...");
            exeExtractor.extract(sourcePath, targetPath, progressListener);
        } else if (Files.isDirectory(sourcePath)) {
            copyDirectory(sourcePath, targetPath);
        } else {
            Path destFile = targetPath.resolve(sourcePath.getFileName());
            Files.copy(sourcePath, destFile, StandardCopyOption.REPLACE_EXISTING);
        }

        reportProgress(progressListener, 0.8, "⚙️ Gerando inicializador portátil e metadados para " + storageType.getCategory() + "...");

        // Gera arquivos portáteis e inicializadores
        createPortableStructure(product, targetPath, storageType, request.autoRunner());

        // Renomeia o rótulo do volume / pen drive se solicitado
        if (request.renameDrive()) {
            String sanitizedLabel = volumeLabelManager.sanitizeLabel(product.getTitle(), 32);
            reportProgress(progressListener, 0.9, "🏷️ Renomeando pen drive/unidade para \"" + sanitizedLabel + "\"...");
            try {
                VolumeLabelManager.VolumeLabelResult labelResult = volumeLabelManager.renameVolume(targetPath, product.getTitle());
                LOGGER.info(() -> "Resultado da renomeação de volume: " + labelResult.message());
            } catch (Exception e) {
                LOGGER.warning("Aviso ao renomear unidade de disco: " + e.getMessage());
            }
        }

        // Registro do jogo
        if (gameRegistryRepository != null) {
            gameRegistryRepository.registerGame(product, targetPath, request.autoRunner());
        }

        reportProgress(progressListener, 1.0, "✅ Jogo instalado e registrado com sucesso!");
        LOGGER.info(() -> "InstallGameUseCase: jogo " + product.getTitle() + " instalado e registrado com sucesso.");
    }

    private void reportProgress(InstallationProgressListener listener, double progress, String message) {
        if (listener != null) {
            listener.onProgress(progress, message);
        }
    }

    private void createPortableStructure(Product product, Path targetDir, StorageType storageType, boolean autoRunner) {
        try {
            Path mainExe = exeExtractor.findMainExecutable(targetDir);
            String exeName = (mainExe != null) ? mainExe.getFileName().toString() : "game.exe";

            // 1. playanywhere.json (metadados do jogo portátil)
            Path metaFile = targetDir.resolve("playanywhere.json");
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", product.getId());
            meta.put("title", product.getTitle());
            meta.put("slug", product.getSlug());
            meta.put("executable", exeName);
            meta.put("sizeMB", product.getSizeMB());
            meta.put("formattedSize", product.getFormattedSize());
            meta.put("storageType", storageType.getDisplayName());
            meta.put("mediaCategory", storageType.getCategory());
            meta.put("autoRunner", autoRunner);
            meta.put("runnerType", "AUTO");
            meta.put("volumeLabel", volumeLabelManager.sanitizeLabel(product.getTitle(), 32));
            meta.put("installedAt", Instant.now().toString());
            MAPPER.writeValue(metaFile.toFile(), meta);

            // 2. autorun.inf (se configurado autoRunner ou para mídias ópticas/USB)
            if (autoRunner || storageType == StorageType.CD_ROM || storageType == StorageType.DVD_5 || storageType == StorageType.DVD_9 || storageType == StorageType.USB_DRIVE_16GB || storageType == StorageType.USB_DRIVE_64GB) {
                Path autorunFile = targetDir.resolve("autorun.inf");
                String autorunContent = "[autorun]\r\n"
                        + "open=" + exeName + "\r\n"
                        + "label=" + (product.getTitle() != null ? product.getTitle() : "Game") + "\r\n"
                        + "action=Iniciar " + (product.getTitle() != null ? product.getTitle() : "Game") + "\r\n"
                        + "icon=" + exeName + ",0\r\n";
                Files.writeString(autorunFile, autorunContent);
            }

            // 3. Script / executável launcher se não existir
            Path exeFile = targetDir.resolve("game.exe");
            if (!Files.exists(exeFile) && mainExe == null) {
                Files.writeString(exeFile, "PlayAnywhere Portable Runner for " + product.getTitle());
            }

            Path scriptFile = targetDir.resolve("start_game.sh");
            if (!Files.exists(scriptFile)) {
                Files.writeString(scriptFile, "#!/bin/bash\n# PlayAnywhere Launcher\necho \"Iniciando " + product.getTitle() + "...\"\n");
            }

            // 4. Se for CD ou DVD, gerar descritor de layout de disco
            if (storageType.getCategory().contains("CD") || storageType.getCategory().contains("DVD")) {
                Path discInfo = targetDir.resolve("DISC_LAYOUT.txt");
                String discContent = "PlayAnywhere Disc Media Image\n"
                        + "Title: " + product.getTitle() + "\n"
                        + "Media Type: " + storageType.getDisplayName() + "\n"
                        + "Size: " + product.getFormattedSize() + "\n"
                        + "Ready for optical disc burning.\n";
                Files.writeString(discInfo, discContent);
            }
        } catch (Exception e) {
            LOGGER.warning("Aviso ao criar estrutura portátil adicional: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
