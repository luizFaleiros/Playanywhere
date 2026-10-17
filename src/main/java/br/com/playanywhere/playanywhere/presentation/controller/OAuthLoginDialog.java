package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.application.usecase.AuthenticateUseCase;
import br.com.playanywhere.playanywhere.api.GOGApi;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diálogo com WebView embutido para fluxo de autenticação OAuth2 da GOG (suporta Google e SSO).
 */
public class OAuthLoginDialog {
    private static final Logger LOGGER = Logger.getLogger(OAuthLoginDialog.class.getName());

    private final AuthenticateUseCase authenticateUseCase;

    public OAuthLoginDialog() {
        this.authenticateUseCase = new AuthenticateUseCase(GOGApi.getInstance());
    }

    public OAuthLoginDialog(AuthenticateUseCase authenticateUseCase) {
        this.authenticateUseCase = authenticateUseCase;
    }

    public static void open(Window owner, Consumer<String> onCodeSuccess, Consumer<Throwable> onError) {
        new OAuthLoginDialog().show(owner, onCodeSuccess, onError);
    }

    public void show(Window owner, Consumer<String> onCodeSuccess, Consumer<Throwable> onError) {
        LOGGER.info("Abrindo diálogo de autenticação OAuth2 (WebView / Navegador)...");
        try {
            Stage stage = new Stage();
            stage.setTitle("Login GOG / Google - Autenticação OAuth2");
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(Modality.WINDOW_MODAL);
            }

            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #1a1a2e;");

            // Barra Superior
            VBox topBox = new VBox(5);
            topBox.setPadding(new Insets(10, 15, 10, 15));
            topBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #0f3460; -fx-border-width: 0 0 1 0;");

            Label titleLabel = new Label("🔐 Autenticação Oficial GOG / Google");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

            Label statusLabel = new Label("Carregando página de autenticação segura da GOG...");
            statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #00adb5;");

            ProgressBar progressBar = new ProgressBar();
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.setStyle("-fx-accent: #e94560;");

            topBox.getChildren().addAll(titleLabel, statusLabel, progressBar);
            root.setTop(topBox);

            // Centro - WebView
            WebView webView = new WebView();
            WebEngine webEngine = webView.getEngine();
            webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            webEngine.setJavaScriptEnabled(true);
            webEngine.setCreatePopupHandler(config -> {
                LOGGER.info("Popup solicitado pelo WebView, reutilizando janela principal do diálogo.");
                return webEngine;
            });

            if (java.net.CookieHandler.getDefault() == null) {
                java.net.CookieHandler.setDefault(new java.net.CookieManager());
            }

            root.setCenter(webView);

            String authUrl = authenticateUseCase.buildAuthorizationUrl();
            LOGGER.info(() -> "Carregando URL de autorização no WebView: " + authUrl);
            webEngine.load(authUrl);

            progressBar.progressProperty().bind(webEngine.getLoadWorker().progressProperty());

            AtomicBoolean codeCaptured = new AtomicBoolean(false);

            webEngine.locationProperty().addListener((observable, oldLocation, newLocation) -> {
                if (newLocation == null) return;
                LOGGER.info(() -> "WebView navegou para nova URL: " + sanitizeUrl(newLocation));

                if (newLocation.contains("error=") || newLocation.contains("error_description=")) {
                    LOGGER.warning(() -> "Erro de autenticação recebido na URL do WebView: " + newLocation);
                }

                if (newLocation.contains("on_login_success") || newLocation.contains("code=")) {
                    LOGGER.info(() -> "URL de retorno com sucesso/código detectada: " + sanitizeUrl(newLocation));
                    String code = authenticateUseCase.extractCodeFromUrl(newLocation);
                    if (code != null && !code.isEmpty() && codeCaptured.compareAndSet(false, true)) {
                        LOGGER.info(() -> "Código de autorização interceptado com sucesso via WebView (tamanho=" + code.length() + ")");
                        Platform.runLater(() -> {
                            statusLabel.setText("✅ Código de autorização recebido!");
                            stage.close();
                            if (onCodeSuccess != null) {
                                onCodeSuccess.accept(code);
                            }
                        });
                    }
                }
            });

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                LOGGER.fine(() -> "Estado do WebView alterado de " + oldState + " para " + newState);
                switch (newState) {
                    case SUCCEEDED -> {
                        LOGGER.info("Página de login GOG carregada com sucesso no WebView.");
                        statusLabel.setText("Página carregada. Efetue login com sua conta GOG ou Google.");
                    }
                    case FAILED -> {
                        Throwable ex = webEngine.getLoadWorker().getException();
                        LOGGER.log(Level.WARNING, "Falha ao carregar página de login no WebView: " + (ex != null ? ex.getMessage() : "Desconhecido"), ex);
                        statusLabel.setText("Falha ao carregar navegador embutido. Use o navegador externo abaixo.");
                    }
                    case RUNNING -> {
                        LOGGER.fine("WebView conectando aos servidores GOG...");
                        statusLabel.setText("Conectando aos servidores de autenticação GOG...");
                    }
                    default -> {}
                }
            });

            // Barra Inferior
            HBox bottomBar = new HBox(10);
            bottomBar.setAlignment(Pos.CENTER_RIGHT);
            bottomBar.setPadding(new Insets(10, 15, 10, 15));
            bottomBar.setStyle("-fx-background-color: #16213e; -fx-border-color: #0f3460; -fx-border-width: 1 0 0 0;");

            Button browserBtn = new Button("🌐 Abrir no Navegador Externo");
            browserBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand;");
            browserBtn.setOnAction(e -> {
                LOGGER.info(() -> "Usuário solicitou abertura no navegador externo com URL: " + authUrl);
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(authUrl));
                        LOGGER.info("Navegador do sistema aberto com sucesso.");
                    } else {
                        LOGGER.warning("Desktop browse não suportado pelo ambiente gráfico.");
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Erro ao abrir navegador externo: " + ex.getMessage(), ex);
                }
            });

            Button manualCodeBtn = new Button("📝 Inserir Código/URL Manualmente");
            manualCodeBtn.setStyle("-fx-background-color: #533483; -fx-text-fill: white; -fx-cursor: hand;");
            manualCodeBtn.setOnAction(e -> {
                LOGGER.info("Usuário abriu diálogo de inserção manual de código.");
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Inserir Código ou URL de Retorno");
                dialog.setHeaderText("Após fazer login no navegador, cole a URL de redirecionamento ou o código:");
                dialog.setContentText("Código ou URL:");
                Optional<String> result = dialog.showAndWait();
                result.ifPresent(input -> {
                    LOGGER.info(() -> "Entrada manual recebida pelo usuário: " + sanitizeUrl(input));
                    String extracted = authenticateUseCase.extractCodeFromUrl(input);
                    if (extracted != null && !extracted.isEmpty() && codeCaptured.compareAndSet(false, true)) {
                        LOGGER.info("Código extraído com sucesso a partir da entrada manual.");
                        stage.close();
                        if (onCodeSuccess != null) {
                            onCodeSuccess.accept(extracted);
                        }
                    } else {
                        LOGGER.warning("Falha ao extrair código da entrada manual fornecida.");
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Não foi possível encontrar o código de autorização no texto informado.");
                        alert.showAndWait();
                    }
                });
            });

            Button cancelBtn = new Button("Cancelar");
            cancelBtn.setStyle("-fx-background-color: #e94560; -fx-text-fill: white; -fx-cursor: hand;");
            cancelBtn.setOnAction(e -> {
                LOGGER.info("Login OAuth cancelado pelo usuário.");
                stage.close();
            });

            HBox.setHgrow(browserBtn, Priority.NEVER);
            bottomBar.getChildren().addAll(browserBtn, manualCodeBtn, cancelBtn);
            root.setBottom(bottomBar);

            Scene scene = new Scene(root, 750, 680);
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao inicializar OAuthLoginDialog: " + e.getMessage(), e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "null";
        return url.replaceAll("(code=)[^&#]+", "$1[EXTRAÍDO]");
    }
}
