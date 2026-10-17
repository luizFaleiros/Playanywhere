package br.com.playanywhere.playanywhere.install;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo visual JavaFX exibido na inicialização quando ferramentas essenciais ou recomendadas
 * de descompressão (.exe, Inno Setup, 7z, unzip) não são localizadas no sistema.
 */
public class ExtractionDependencyDialog {
    private static final Logger LOGGER = Logger.getLogger(ExtractionDependencyDialog.class.getName());

    /**
     * Executa a verificação na inicialização e exibe o diálogo caso ferramentas recomendadas estejam ausentes.
     */
    public static void checkAndShowIfNeeded(Window owner) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                ExtractionDependencyChecker.ExtractionReport report = ExtractionDependencyChecker.checkDependencies();
                LOGGER.info("Ambiente headless detectado.\n" + report.summaryText());
                return;
            }

            Platform.runLater(() -> {
                try {
                    ExtractionDependencyChecker.ExtractionReport report = ExtractionDependencyChecker.checkDependencies();
                    if (!report.isOptimal() || !report.isReady()) {
                        LOGGER.warning("Ferramentas de extração ausentes ou incompletas detectadas no startup.");
                        show(owner, report);
                    } else {
                        LOGGER.info("Todas as ferramentas de descompressão essenciais estão presentes.");
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Erro ao exibir diálogo de verificação de dependências: " + e.getMessage(), e);
                }
            });
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Aviso na verificação de dependências em modo não-gráfico: " + t.getMessage());
        }
    }

    /**
     * Exibe o diálogo com os detalhes do relatório de dependências.
     */
    public static void show(Window owner, ExtractionDependencyChecker.ExtractionReport initialReport) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("PlayAnywhere - Utilitários de Descompressão");
        dialog.setResizable(true);
        dialog.setMinWidth(640);
        dialog.setMinHeight(480);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1e1e2e; -fx-text-fill: #ffffff;");

        // Cabeçalho
        Label headerTitle = new Label("📦 Ferramentas de Descompressão e Unzip");
        headerTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        headerTitle.setTextFill(Color.web("#89b4fa"));

        Label headerSubtitle = new Label("Para extrair e instalar jogos (.exe, Inno Setup, ZIP) diretamente na sua unidade portátil, o PlayAnywhere utiliza utilitários nativos de alta performance.");
        headerSubtitle.setWrapText(true);
        headerSubtitle.setTextFill(Color.web("#cdd6f4"));

        VBox contentBox = new VBox(12);
        renderContent(contentBox, initialReport, dialog);

        // Barra inferior de botões
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button recheckBtn = new Button("🔄 Verificar Novamente");
        recheckBtn.setStyle("-fx-background-color: #45475a; -fx-text-fill: #ffffff; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 8 16;");
        recheckBtn.setOnAction(e -> {
            ExtractionDependencyChecker.ExtractionReport freshReport = ExtractionDependencyChecker.checkDependencies();
            renderContent(contentBox, freshReport, dialog);
        });

        Button continueBtn = new Button("Continuar para o Aplicativo");
        continueBtn.setStyle("-fx-background-color: #a6e3a1; -fx-text-fill: #11111b; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 8 18;");
        continueBtn.setOnAction(e -> dialog.close());

        buttonBar.getChildren().addAll(recheckBtn, continueBtn);

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1e1e2e; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(headerTitle, headerSubtitle, scrollPane, buttonBar);

        Scene scene = new Scene(root, 680, 520);
        dialog.setScene(scene);
        dialog.show();
    }

    private static void renderContent(VBox container, ExtractionDependencyChecker.ExtractionReport report, Stage dialog) {
        container.getChildren().clear();

        // 1. Status do Sistema Operacional
        VBox osBox = new VBox(6);
        osBox.setPadding(new Insets(10));
        osBox.setStyle("-fx-background-color: #313244; -fx-background-radius: 6;");

        String osLabelText;
        if (report.osFamily() == DistroDetector.OsFamily.LINUX && report.distroInfo() != null) {
            osLabelText = "🐧 Sistema Detectado: " + report.distroInfo().name() 
                    + " (Gerenciador: " + report.distroInfo().packageManager() + ")";
        } else if (report.osFamily() == DistroDetector.OsFamily.MACOS) {
            osLabelText = "🍎 Sistema Detectado: macOS (Darwin)";
        } else if (report.osFamily() == DistroDetector.OsFamily.WINDOWS) {
            osLabelText = "🪟 Sistema Detectado: Microsoft Windows";
        } else {
            osLabelText = "💻 Sistema Detectado: " + System.getProperty("os.name");
        }

        Label osLabel = new Label(osLabelText);
        osLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        osLabel.setTextFill(Color.web("#f9e2af"));
        osBox.getChildren().add(osLabel);
        container.getChildren().add(osBox);

        // 2. Lista de Ferramentas
        VBox toolsBox = new VBox(6);
        toolsBox.setPadding(new Insets(10));
        toolsBox.setStyle("-fx-background-color: #313244; -fx-background-radius: 6;");

        Label toolsTitle = new Label("Status das Ferramentas no Sistema:");
        toolsTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        toolsTitle.setTextFill(Color.web("#cdd6f4"));
        toolsBox.getChildren().add(toolsTitle);

        for (ExtractionDependencyChecker.ToolStatus tool : report.tools()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label icon = new Label(tool.available() ? "✅" : "❌");
            Label name = new Label(tool.name() + ":");
            name.setFont(Font.font("System", FontWeight.BOLD, 12));
            name.setTextFill(tool.available() ? Color.web("#a6e3a1") : Color.web("#f38ba8"));

            Label detail = new Label(tool.available() ? tool.resolvedPath() : "Não localizado");
            detail.setTextFill(Color.web("#bac2de"));

            row.getChildren().addAll(icon, name, detail);
            toolsBox.getChildren().add(row);
        }
        container.getChildren().add(toolsBox);

        // 3. Comando sugerido para o terminal
        if (report.suggestedCommand() != null && !report.suggestedCommand().isBlank()) {
            VBox cmdBox = new VBox(8);
            cmdBox.setPadding(new Insets(10));
            cmdBox.setStyle("-fx-background-color: #181825; -fx-border-color: #fab387; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label cmdTitle = new Label("💻 Execute o comando abaixo no terminal da sua distribuição / SO:");
            cmdTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
            cmdTitle.setTextFill(Color.web("#fab387"));

            TextField cmdField = new TextField(report.suggestedCommand());
            cmdField.setEditable(false);
            cmdField.setStyle("-fx-background-color: #11111b; -fx-text-fill: #a6e3a1; -fx-font-family: monospace; -fx-font-size: 12px;");

            Button copyBtn = new Button("📋 Copiar Comando");
            copyBtn.setStyle("-fx-background-color: #fab387; -fx-text-fill: #11111b; -fx-font-weight: bold; -fx-cursor: hand;");
            copyBtn.setOnAction(e -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(report.suggestedCommand());
                clipboard.setContent(content);
                copyBtn.setText("✅ Copiado!");
            });

            HBox cmdRow = new HBox(8, cmdField, copyBtn);
            HBox.setHgrow(cmdField, Priority.ALWAYS);
            cmdRow.setAlignment(Pos.CENTER_LEFT);

            cmdBox.getChildren().addAll(cmdTitle, cmdRow);
            container.getChildren().add(cmdBox);
        }

        // 4. Links para Download Manual
        if (!report.downloadLinks().isEmpty()) {
            VBox linksBox = new VBox(6);
            linksBox.setPadding(new Insets(10));
            linksBox.setStyle("-fx-background-color: #313244; -fx-background-radius: 6;");

            Label linksTitle = new Label("🌐 Links Oficiais para Download / Informações:");
            linksTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
            linksTitle.setTextFill(Color.web("#89dceb"));
            linksBox.getChildren().add(linksTitle);

            for (var entry : report.downloadLinks().entrySet()) {
                Hyperlink link = new Hyperlink(entry.getKey() + " (" + entry.getValue() + ")");
                link.setTextFill(Color.web("#89b4fa"));
                link.setOnAction(e -> openUrl(entry.getValue()));
                linksBox.getChildren().add(link);
            }
            container.getChildren().add(linksBox);
        }
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            LOGGER.warning("Não foi possível abrir o link: " + url + " (" + e.getMessage() + ")");
        }
    }
}
