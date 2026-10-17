package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.api.GOGApi;
import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.application.usecase.InstallGameUseCase;
import br.com.playanywhere.playanywhere.domain.model.GalleryImage;
import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.model.StorageDevice;
import br.com.playanywhere.playanywhere.domain.model.StorageType;
import br.com.playanywhere.playanywhere.drive.DriveManager;
import br.com.playanywhere.playanywhere.drive.GameRegistry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Controller para a tela e diálogo do Instalador de Jogos Portáteis.
 * Gerencia download, escolha de mídia (CD, DVD, Pen Drive, HD) baseada no tamanho do jogo,
 * detecção de unidades conectadas e geração de arquivos portáteis.
 */
public class InstallController {
    private static final Logger LOGGER = Logger.getLogger(InstallController.class.getName());

    @FXML private Label selectedGameTitle;
    @FXML private Label selectedGameDescription;
    @FXML private TextField targetPathField;
    @FXML private Button installButton;
    @FXML private Label installStatusLabel;
    @FXML private Label availableSpaceLabel;
    @FXML private CheckBox autoRunnerCheckBox;
    @FXML private CheckBox renameDriveCheckBox;

    private final InstallGameUseCase installGameUseCase;
    private final DriveManager driveManager;
    private Product selectedProduct;

    public InstallController() {
        this.installGameUseCase = new InstallGameUseCase(GameRegistry.getInstance());
        this.driveManager = new DriveManager();
    }

    public InstallController(InstallGameUseCase installGameUseCase) {
        this.installGameUseCase = installGameUseCase;
        this.driveManager = new DriveManager();
    }

    public static void open(Window owner, Product product) {
        new InstallController().show(owner, product);
    }

    public void show(Window owner, Product product) {
        this.selectedProduct = product != null ? product : new Product(1, "Jogo Desconhecido", "jogo-desconhecido");
        StorageType recommendedMedia = selectedProduct.getRecommendedStorageType();
        LOGGER.info(() -> "Abrindo diálogo do Instalador para: " + selectedProduct.getTitle() 
                + " (Tamanho: " + selectedProduct.getFormattedSize() 
                + ", Mídia recomendada: " + recommendedMedia.getDisplayName() + ")");

        Stage stage = new Stage();
        stage.setTitle("Instalador - " + selectedProduct.getTitle());
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        VBox root = new VBox(15);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1a2e; -fx-font-family: 'Segoe UI', sans-serif;");

        // --- Cabeçalho e Identificação do Jogo ---
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        ImageView coverView = new ImageView();
        coverView.setFitWidth(100);
        coverView.setFitHeight(60);
        coverView.setPreserveRatio(true);
        loadProductCoverImage(selectedProduct, coverView);

        VBox gameInfoBox = new VBox(4);
        Label titleLabel = new Label("📦 " + selectedProduct.getTitle());
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label sizeInfoLabel = new Label("📊 Tamanho do Jogo: " + selectedProduct.getFormattedSize() + " (" + selectedProduct.getSizeMB() + " MB)");
        sizeInfoLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #00adb5; -fx-font-weight: bold;");

        gameInfoBox.getChildren().addAll(titleLabel, sizeInfoLabel);
        headerBox.getChildren().addAll(coverView, gameInfoBox);

        // --- Banner de Recomendação de Mídia ---
        HBox recommendationBanner = new HBox(10);
        recommendationBanner.setAlignment(Pos.CENTER_LEFT);
        recommendationBanner.setPadding(new Insets(10, 14, 10, 14));
        recommendationBanner.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8; -fx-border-color: #4ecca3; -fx-border-radius: 8; -fx-border-width: 1;");

        Label recIcon = new Label(getMediaIcon(recommendedMedia));
        recIcon.setStyle("-fx-font-size: 22px;");

        VBox recTextBox = new VBox(2);
        Label recTitle = new Label("Mídia recomendada com base no tamanho do jogo:");
        recTitle.setStyle("-fx-text-fill: #aaa; -fx-font-size: 11px;");
        Label recDetail = new Label(recommendedMedia.getDisplayName() + " (" + recommendedMedia.getCategory() + ")");
        recDetail.setStyle("-fx-text-fill: #4ecca3; -fx-font-size: 14px; -fx-font-weight: bold;");
        recTextBox.getChildren().addAll(recTitle, recDetail);

        recommendationBanner.getChildren().addAll(recIcon, recTextBox);

        // --- Caminho de Destino e Detecção de Unidades ---
        Label pathLabel = new Label("Local ou Unidade de Destino:");
        pathLabel.setStyle("-fx-text-fill: #ccc; -fx-font-size: 13px; -fx-font-weight: bold;");

        HBox pathBox = new HBox(10);
        TextField pathField = new TextField();
        pathField.setPromptText("Selecione a unidade (ex: Pen Drive, pasta do CD/DVD ou diretório)...");
        pathField.setStyle("-fx-background-color: #2d2d44; -fx-text-fill: white; -fx-prompt-text-fill: #8888aa; -fx-background-radius: 6;");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("📁 Procurar Pasta");
        browseBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Selecione o local de instalação do jogo");
            File chosen = dc.showDialog(stage);
            if (chosen != null) {
                LOGGER.info(() -> "Pasta de destino selecionada: " + chosen.getAbsolutePath());
                pathField.setText(chosen.getAbsolutePath());
            }
        });

        Button detectDrivesBtn = new Button("💾 Detectar Pen Drive / Drives");
        detectDrivesBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");
        detectDrivesBtn.setOnAction(e -> {
            List<String> drives = driveManager.getMountableDrives();
            if (!drives.isEmpty()) {
                pathField.setText(drives.get(0));
                LOGGER.info(() -> "Drive externo detectado automaticamente: " + drives.get(0));
            } else {
                pathField.setText(System.getProperty("user.home") + File.separator + "Games" + File.separator + selectedProduct.getSlug());
            }
        });

        pathBox.getChildren().addAll(pathField, browseBtn, detectDrivesBtn);

        // Auto-detect inicial de drive
        List<String> detectedInitial = driveManager.getMountableDrives();
        if (!detectedInitial.isEmpty()) {
            pathField.setText(detectedInitial.get(0));
        }

        // --- Espaço em Disco Disponível vs Necessário ---
        Label spaceStatusLabel = new Label("Espaço livre no destino: Calculando...");
        spaceStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaa;");

        Runnable updateSpaceCheck = () -> {
            String currentPath = pathField.getText();
            long freeMB = driveManager.getAvailableSpaceMB(currentPath);
            long reqMB = selectedProduct.getSizeMB();
            if (freeMB > 0) {
                boolean hasSpace = freeMB >= reqMB;
                String color = hasSpace ? "#4ecca3" : "#ff4b5c";
                String status = hasSpace ? "✅ Espaço Suficiente" : "⚠️ Espaço Insuficiente";
                spaceStatusLabel.setText(String.format("Espaço livre: %d MB | Necessário: %d MB (%s)", freeMB, reqMB, status));
                spaceStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
            } else {
                spaceStatusLabel.setText("Espaço necessário: " + selectedProduct.getFormattedSize());
                spaceStatusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #aaa;");
            }
        };

        pathField.textProperty().addListener((obs, oldV, newV) -> updateSpaceCheck.run());
        updateSpaceCheck.run();

        // --- Opções Adicionais ---
        CheckBox autoRunCheck = new CheckBox("⚡ Criar autorun e inicializador portátil (game.exe / autorun.inf / Linux launcher)");
        autoRunCheck.setSelected(true);
        autoRunCheck.setStyle("-fx-text-fill: #ddd; -fx-font-size: 12px;");

        CheckBox renameDriveCheck = new CheckBox("🏷️ Renomear pen drive/unidade para o nome do jogo (\"" + selectedProduct.getTitle() + "\")");
        renameDriveCheck.setSelected(true);
        renameDriveCheck.setStyle("-fx-text-fill: #4ecca3; -fx-font-size: 12px; -fx-font-weight: bold;");

        // --- Barra de Progresso e Status de Instalação ---
        ProgressBar progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #4ecca3;");
        progressBar.setVisible(false);

        Label statusLbl = new Label("🔘 Pronto para iniciar instalação.");
        statusLbl.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 13px;");

        // --- Botão de Ação Principal ---
        Button startInstallBtn = new Button("🚀 Baixar e Instalar Jogo");
        startInstallBtn.setMaxWidth(Double.MAX_VALUE);
        startInstallBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 12; -fx-cursor: hand; -fx-background-radius: 8;");
        startInstallBtn.setOnAction(e -> {
            String targetStr = pathField.getText();
            if (targetStr == null || targetStr.trim().isEmpty()) {
                LOGGER.warning("Tentativa de instalação sem diretório de destino selecionado.");
                statusLbl.setText("⚠️ Por favor, escolha a pasta ou unidade de destino.");
                return;
            }

            LOGGER.info(() -> "Iniciando download e instalação de " + selectedProduct.getTitle() + " em: " + targetStr);
            startInstallBtn.setDisable(true);
            browseBtn.setDisable(true);
            detectDrivesBtn.setDisable(true);
            progressBar.setVisible(true);
            progressBar.setProgress(0.05);
            statusLbl.setText("🔄 Iniciando download e preparação...");

            new Thread(() -> {
                Path tempDownloadDir = null;
                try {
                    Path target = Paths.get(targetStr);
                    tempDownloadDir = Files.createTempDirectory("playanywhere-staging-");

                    Platform.runLater(() -> statusLbl.setText("🔍 Buscando instalador offline do jogo..."));

                    Path installerExe = GOGApi.getInstance().downloadGameInstaller(selectedProduct, tempDownloadDir, (prog, msg) -> {
                        Platform.runLater(() -> {
                            progressBar.setProgress(prog);
                            statusLbl.setText(msg);
                        });
                    });

                    InstallationType chosenInstallationType = InstallationType.USB_DRIVE;

                    GameInstallationRequest request = new GameInstallationRequest(
                        selectedProduct,
                        chosenInstallationType,
                        target,
                        installerExe,
                        autoRunCheck.isSelected(),
                        renameDriveCheck.isSelected()
                    );

                    installGameUseCase.execute(request, (progress, message) -> {
                        Platform.runLater(() -> {
                            progressBar.setProgress(progress);
                            statusLbl.setText(message);
                        });
                    });

                    LOGGER.info(() -> "Download e instalação finalizados com êxito em " + target);

                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        statusLbl.setText("✅ Jogo baixado e descompactado com sucesso!");
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Instalação Concluída");
                        alert.setHeaderText("🎮 " + selectedProduct.getTitle() + " está pronto!");
                        alert.setContentText("O jogo foi baixado e descompactado com sucesso na mídia:\n" 
                                + targetStr + "\n\nVocê já pode plugá-lo e jogar em qualquer computador!");
                        alert.showAndWait();
                        stage.close();
                    });
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Erro durante processo de instalação: " + ex.getMessage(), ex);
                    Platform.runLater(() -> {
                        progressBar.setStyle("-fx-accent: #ff4b5c;");
                        statusLbl.setText("❌ Erro ao instalar: " + ex.getMessage());
                        startInstallBtn.setDisable(false);
                        browseBtn.setDisable(false);
                        detectDrivesBtn.setDisable(false);
                    });
                } finally {
                    if (tempDownloadDir != null) {
                        try {
                            deleteDirectoryRecursively(tempDownloadDir);
                        } catch (Exception ignored) {}
                    }
                }
            }).start();
        });

        root.getChildren().addAll(
            headerBox,
            recommendationBanner,
            pathLabel,
            pathBox,
            spaceStatusLabel,
            autoRunCheck,
            renameDriveCheck,
            progressBar,
            statusLbl,
            startInstallBtn
        );

        Scene scene = new Scene(root, 620, 520);
        stage.setScene(scene);
        stage.show();
    }

    private String getMediaIcon(StorageType type) {
        if (type == null) return "💾";
        return switch (type) {
            case CD_ROM -> "💿";
            case DVD_5, DVD_9 -> "📀";
            case USB_DRIVE_8GB, USB_DRIVE_16GB, USB_DRIVE_32GB, USB_DRIVE_64GB -> "💾";
        };
    }

    private InstallationType determineInstallationType(String choiceValue) {
        if (choiceValue == null) return InstallationType.PEN_DRIVE;
        String lower = choiceValue.toLowerCase();
        if (lower.contains("cd")) return InstallationType.CD;
        if (lower.contains("dvd")) return InstallationType.DVD;
        if (lower.contains("pen drive") || lower.contains("usb")) return InstallationType.PEN_DRIVE;
        if (lower.contains("hd")) return InstallationType.EXTERNAL_HD;
        return InstallationType.LOCAL_DIRECTORY;
    }

    private void loadProductCoverImage(Product product, ImageView imageView) {
        if (product == null || imageView == null) return;
        String imageUrl = null;
        if (product.getGallery() != null && !product.getGallery().isEmpty()) {
            GalleryImage firstImg = product.getGallery().get(0);
            if (firstImg != null && firstImg.getImagePath() != null) {
                imageUrl = firstImg.getImagePath();
            }
        }
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            String normalizedUrl = GOGApi.normalizeImageUrl(imageUrl);
            if (normalizedUrl != null) {
                try {
                    imageView.setImage(new Image(normalizedUrl, true));
                } catch (Exception e) {
                    LOGGER.fine("Não foi possível carregar a imagem de capa: " + e.getMessage());
                }
            }
        }
    }

    private Path createDummyGameSource(Product product) throws Exception {
        Path tempDir = Files.createTempDirectory("playanywhere-download-");
        String slug = (product != null && product.getSlug() != null && !product.getSlug().isBlank()) ? product.getSlug() : "game";
        Path exePath = tempDir.resolve("setup_" + slug + ".exe");

        // Gera o arquivo instalador executável (.exe) contendo os arquivos compactados do jogo
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] mzStub = new byte[]{
            (byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00
        };
        baos.write(mzStub);
        baos.write("This program cannot be run in DOS mode.\r\r\n$".getBytes(StandardCharsets.US_ASCII));
        baos.write(new byte[128]);

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String gameTitle = product != null ? product.getTitle() : "Game";

            ZipEntry entryExe = new ZipEntry("game.exe");
            zos.putNextEntry(entryExe);
            zos.write(("PlayAnywhere Game Executable for " + gameTitle).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entryReadme = new ZipEntry("README.txt");
            zos.putNextEntry(entryReadme);
            zos.write(("PlayAnywhere Portable Game: " + gameTitle + "\nTamanho: " + (product != null ? product.getFormattedSize() : "N/A")).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entryData = new ZipEntry("data/game.dat");
            zos.putNextEntry(entryData);
            zos.write("game data content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Files.write(exePath, baos.toByteArray());
        return exePath;
    }

    @FXML
    public void initialize() {
        LOGGER.info("Inicializando InstallController FXML...");
    }

    @FXML
    public void exploreExternalDrives(ActionEvent event) {
        LOGGER.info("Explorando unidades de armazenamento externas...");
        List<String> drives = driveManager.getMountableDrives();
        if (drives.isEmpty()) {
            LOGGER.info("Nenhuma unidade de armazenamento externa detectada.");
            if (installStatusLabel != null) {
                installStatusLabel.setText("Nenhum drive externo encontrado.");
            }
        } else {
            LOGGER.info(() -> "Drives detectados: " + drives);
            if (targetPathField != null) {
                targetPathField.setText(drives.get(0));
            }
            if (availableSpaceLabel != null) {
                long freeMB = driveManager.getAvailableSpaceMB(drives.get(0));
                availableSpaceLabel.setText(freeMB + " MB livres");
            }
        }
    }

    @FXML
    public void navigateFolders(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecione o diretório");
        Window window = installStatusLabel != null && installStatusLabel.getScene() != null ? installStatusLabel.getScene().getWindow() : null;
        File dir = chooser.showDialog(window);
        if (dir != null && targetPathField != null) {
            LOGGER.info(() -> "Diretório selecionado: " + dir.getAbsolutePath());
            targetPathField.setText(dir.getAbsolutePath());
            if (availableSpaceLabel != null) {
                long freeMB = driveManager.getAvailableSpaceMB(dir.getAbsolutePath());
                availableSpaceLabel.setText(freeMB + " MB livres");
            }
        }
    }

    @FXML
    public void handleInstall(ActionEvent event) {
        if (targetPathField == null || targetPathField.getText().isEmpty()) {
            LOGGER.warning("Tentativa de instalação sem caminho de destino selecionado.");
            if (installStatusLabel != null) installStatusLabel.setText("Selecione um caminho de destino.");
            return;
        }

        String targetStr = targetPathField.getText();
        LOGGER.info(() -> "Iniciando instalação FXML para caminho: " + targetStr);
        if (installStatusLabel != null) installStatusLabel.setText("Baixando e instalando jogo...");
        if (installButton != null) installButton.setDisable(true);

        new Thread(() -> {
            Path tempDownloadDir = null;
            try {
                Product productToInstall = selectedProduct != null ? selectedProduct : new Product(1, "Jogo GOG", "jogo-gog", 1500);
                Path target = Paths.get(targetStr);
                tempDownloadDir = Files.createTempDirectory("playanywhere-staging-fxml-");

                Platform.runLater(() -> {
                    if (installStatusLabel != null) installStatusLabel.setText("🔍 Buscando instalador offline do jogo...");
                });

                Path installerExe = GOGApi.getInstance().downloadGameInstaller(productToInstall, tempDownloadDir, (progress, message) -> {
                    Platform.runLater(() -> {
                        if (installStatusLabel != null) installStatusLabel.setText(message);
                    });
                });

                InstallationType instType = InstallationType.USB_DRIVE;

                GameInstallationRequest request = new GameInstallationRequest(
                    productToInstall,
                    instType,
                    target,
                    installerExe,
                    autoRunnerCheckBox != null && autoRunnerCheckBox.isSelected(),
                    renameDriveCheckBox != null ? renameDriveCheckBox.isSelected() : true
                );

                installGameUseCase.execute(request, (progress, message) -> {
                    Platform.runLater(() -> {
                        if (installStatusLabel != null) installStatusLabel.setText(message);
                    });
                });

                LOGGER.info("Instalação FXML finalizada com sucesso.");

                Platform.runLater(() -> {
                    if (installStatusLabel != null) installStatusLabel.setText("✅ Instalação concluída com sucesso!");
                    if (installButton != null) installButton.setDisable(false);
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro na instalação FXML: " + e.getMessage(), e);
                Platform.runLater(() -> {
                    if (installStatusLabel != null) installStatusLabel.setText("Erro: " + e.getMessage());
                    if (installButton != null) installButton.setDisable(false);
                });
            } finally {
                if (tempDownloadDir != null) {
                    try {
                        deleteDirectoryRecursively(tempDownloadDir);
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
