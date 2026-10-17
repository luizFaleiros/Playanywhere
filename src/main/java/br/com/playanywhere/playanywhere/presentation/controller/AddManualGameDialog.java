package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.drive.GameRegistry;
import br.com.playanywhere.playanywhere.launcher.GameLauncher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo para adição manual de jogos instalados ou portáteis na biblioteca.
 */
public class AddManualGameDialog {
    private static final Logger LOGGER = Logger.getLogger(AddManualGameDialog.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Window ownerWindow;
    private final Consumer<InstalledGame> onGameAdded;

    private TextField pathField;
    private TextField titleField;
    private TextField executableField;
    private TextField sizeField;
    private CheckBox autoRunnerCheckBox;
    private Label validationLabel;
    private Stage dialogStage;

    public AddManualGameDialog(Window ownerWindow, Consumer<InstalledGame> onGameAdded) {
        this.ownerWindow = ownerWindow;
        this.onGameAdded = onGameAdded;
    }

    public static void open(Window owner, Consumer<InstalledGame> onGameAdded) {
        Platform.runLater(() -> {
            AddManualGameDialog dialog = new AddManualGameDialog(owner, onGameAdded);
            dialog.show();
        });
    }

    public void show() {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (ownerWindow != null) {
            dialogStage.initOwner(ownerWindow);
        }
        dialogStage.setTitle("Adicionar Jogo Manualmente");

        VBox root = new VBox(15);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setPrefWidth(550);

        Label header = new Label("➕ Adicionar Jogo Manualmente");
        header.setStyle("-fx-font-size: 20px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label subHeader = new Label("Selecione a pasta do jogo ou o arquivo executável (.exe / .sh / .bin):");
        subHeader.setStyle("-fx-font-size: 13px; -fx-text-fill: #a0a0b0;");

        // Path selection
        Label pathLabel = new Label("Caminho do Jogo / Pasta:");
        pathLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");

        pathField = new TextField();
        pathField.setPromptText("Ex: /media/pendrive/TheWitcher3 ou C:\\Games\\TheWitcher3");
        pathField.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; -fx-prompt-text-fill: #6c7a89; -fx-border-color: #0f3460; -fx-border-radius: 5;");
        pathField.textProperty().addListener((obs, oldVal, newVal) -> onPathChanged(newVal));

        Button browseFolderBtn = new Button("📂 Pasta...");
        browseFolderBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 5;");
        browseFolderBtn.setOnAction(e -> handleBrowseFolder());

        Button browseFileBtn = new Button("📄 Executável...");
        browseFileBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 5;");
        browseFileBtn.setOnAction(e -> handleBrowseFile());

        HBox pathBox = new HBox(8, pathField, browseFolderBtn, browseFileBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        // Title input
        Label titleLabel = new Label("Título do Jogo:");
        titleLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");

        titleField = new TextField();
        titleField.setPromptText("Nome do jogo (ex: Cyberpunk 2077)");
        titleField.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; -fx-prompt-text-fill: #6c7a89; -fx-border-color: #0f3460; -fx-border-radius: 5;");

        // Executable input
        Label exeLabel = new Label("Nome do Executável:");
        exeLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");

        executableField = new TextField("game.exe");
        executableField.setPromptText("game.exe ou start.sh");
        executableField.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; -fx-prompt-text-fill: #6c7a89; -fx-border-color: #0f3460; -fx-border-radius: 5;");

        // Size input
        Label sizeLabel = new Label("Tamanho Estimado (MB):");
        sizeLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");

        sizeField = new TextField("1000");
        sizeField.setPromptText("Ex: 5000 para 5 GB");
        sizeField.setStyle("-fx-background-color: #16213e; -fx-text-fill: white; -fx-prompt-text-fill: #6c7a89; -fx-border-color: #0f3460; -fx-border-radius: 5;");

        // AutoRunner CheckBox
        autoRunnerCheckBox = new CheckBox("Lançar automaticamente ao conectar o dispositivo");
        autoRunnerCheckBox.setSelected(true);
        autoRunnerCheckBox.setStyle("-fx-text-fill: #4ecca3; -fx-font-weight: bold;");

        validationLabel = new Label();
        validationLabel.setStyle("-fx-text-fill: #e94560; -fx-font-size: 12px;");

        // Action Buttons
        Button saveBtn = new Button("💾 Adicionar Jogo");
        saveBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 6;");
        saveBtn.setOnAction(e -> handleSave());

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setStyle("-fx-background-color: #2d2d44; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        HBox buttonBox = new HBox(12, cancelBtn, saveBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            header,
            subHeader,
            pathLabel,
            pathBox,
            titleLabel,
            titleField,
            exeLabel,
            executableField,
            sizeLabel,
            sizeField,
            autoRunnerCheckBox,
            validationLabel,
            buttonBox
        );

        Scene scene = new Scene(root);
        dialogStage.setScene(scene);
        dialogStage.show();
    }

    private void handleBrowseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Selecione a Pasta do Jogo");
        File dir = chooser.showDialog(dialogStage);
        if (dir != null) {
            pathField.setText(dir.getAbsolutePath());
        }
    }

    private void handleBrowseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecione o Executável do Jogo");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Executáveis (*.exe, *.sh, *.bin, *.bat, *.app)", "*.exe", "*.sh", "*.bin", "*.bat", "*.app"),
            new FileChooser.ExtensionFilter("Todos os Arquivos (*.*)", "*.*")
        );
        File file = chooser.showOpenDialog(dialogStage);
        if (file != null) {
            pathField.setText(file.getAbsolutePath());
        }
    }

    private void onPathChanged(String newPath) {
        if (newPath == null || newPath.trim().isEmpty()) {
            return;
        }
        Path path = Paths.get(newPath.trim());
        if (!Files.exists(path)) {
            return;
        }

        Path targetDir = Files.isDirectory(path) ? path : path.getParent();
        if (targetDir == null) return;

        // Verifica se há playanywhere.json existente
        GameLauncher launcher = new GameLauncher();
        Optional<PlayAnywhereMetadata> metaOpt = launcher.readMetadata(targetDir);
        if (metaOpt.isPresent()) {
            PlayAnywhereMetadata meta = metaOpt.get();
            if (titleField.getText().trim().isEmpty() || titleField.getText().equals("game")) {
                titleField.setText(meta.title());
            }
            if (meta.executable() != null && !meta.executable().trim().isEmpty()) {
                executableField.setText(meta.executable());
            }
            if (meta.sizeMB() > 0) {
                sizeField.setText(String.valueOf(meta.sizeMB()));
            }
            autoRunnerCheckBox.setSelected(meta.autoRunner());
            return;
        }

        // Se for um arquivo executável selecionado
        if (Files.isRegularFile(path)) {
            executableField.setText(path.getFileName().toString());
            if (titleField.getText().trim().isEmpty()) {
                String folderName = targetDir.getFileName() != null ? targetDir.getFileName().toString() : "Jogo";
                titleField.setText(formatGameTitle(folderName));
            }
        } else if (Files.isDirectory(path)) {
            // Tenta localizar executável na pasta
            Path foundExe = launcher.findExecutable(path, null);
            if (foundExe != null) {
                executableField.setText(foundExe.getFileName().toString());
            }
            if (titleField.getText().trim().isEmpty()) {
                String folderName = path.getFileName() != null ? path.getFileName().toString() : "Jogo";
                titleField.setText(formatGameTitle(folderName));
            }
        }
    }

    private String formatGameTitle(String raw) {
        if (raw == null) return "Jogo";
        return raw.replace("_", " ").replace("-", " ").trim();
    }

    public boolean processManualAddition(String pathStr, String titleStr, String exeStr, String sizeStr, boolean autoRunner) {
        if (pathStr == null || pathStr.trim().isEmpty()) {
            if (validationLabel != null) validationLabel.setText("Por favor, selecione o caminho do jogo.");
            return false;
        }

        Path path = Paths.get(pathStr.trim());
        if (!Files.exists(path)) {
            if (validationLabel != null) validationLabel.setText("O caminho especificado não existe no disco.");
            return false;
        }

        Path resolvedGameDir = Files.isDirectory(path) ? path : path.getParent();
        final Path gameDir = resolvedGameDir != null ? resolvedGameDir : path;

        String title = (titleStr != null && !titleStr.trim().isEmpty()) ? titleStr.trim() : gameDir.getFileName().toString();
        String executable = (exeStr != null && !exeStr.trim().isEmpty()) ? exeStr.trim() : "game.exe";

        long sizeMB = 1000;
        try {
            if (sizeStr != null && !sizeStr.trim().isEmpty()) {
                sizeMB = Long.parseLong(sizeStr.trim());
            }
        } catch (NumberFormatException ignored) {}

        Product product = new Product(0, title, title.toLowerCase().replaceAll("[^a-z0-9]", "-"), sizeMB);

        // Cria ou atualiza o playanywhere.json se possível
        try {
            Path metaFile = gameDir.resolve("playanywhere.json");
            if (!Files.exists(metaFile)) {
                PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
                    0,
                    title,
                    product.getSlug(),
                    executable,
                    sizeMB,
                    product.getFormattedSize(),
                    "Manual",
                    "Manual",
                    autoRunner,
                    Instant.now().toString(),
                    List.of(),
                    "AUTO"
                );
                MAPPER.writeValue(metaFile.toFile(), meta);
                LOGGER.info(() -> "Arquivo playanywhere.json criado com sucesso em " + metaFile);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Não foi possível escrever playanywhere.json em " + gameDir + ": " + e.getMessage());
        }

        // Registra o jogo no GameRegistry local
        GameRegistry.getInstance().registerGame(product, gameDir, autoRunner);
        LOGGER.info(() -> "Jogo manual '" + title + "' registrado com sucesso em " + gameDir);

        InstalledGame installedGame = new InstalledGame(product, gameDir, autoRunner);
        if (onGameAdded != null) {
            onGameAdded.accept(installedGame);
        }

        return true;
    }

    private void handleSave() {
        boolean success = processManualAddition(
            pathField.getText(),
            titleField.getText(),
            executableField.getText(),
            sizeField.getText(),
            autoRunnerCheckBox.isSelected()
        );

        if (success && dialogStage != null) {
            dialogStage.close();
        }
    }
}
