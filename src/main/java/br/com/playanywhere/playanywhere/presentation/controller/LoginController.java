package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.api.GOGApi;
import br.com.playanywhere.playanywhere.application.usecase.AuthenticateUseCase;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller unificado para a tela de Login (com fluxo oficial OAuth2 GOG).
 */
public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    private final AuthenticateUseCase authenticateUseCase;
    private LibraryController libraryController;
    private Parent rootNode;
    private Stage explicitStage;
    private boolean autoLoginAttempted = false;

    public LoginController() {
        this(new AuthenticateUseCase(GOGApi.getInstance()));
    }

    public LoginController(AuthenticateUseCase authenticateUseCase) {
        this.authenticateUseCase = authenticateUseCase;
    }

    public void setStage(Stage stage) {
        this.explicitStage = stage;
    }

    @FXML
    public void initialize() {
        LOGGER.info("Inicializando LoginController...");
        if (loginButton != null) {
            loginButton.setDisable(false);
        }
        attemptAutoLogin();
    }

    public synchronized void attemptAutoLogin() {
        if (autoLoginAttempted) {
            return;
        }
        autoLoginAttempted = true;
        updateStatus("🔍 Verificando sessão salva no SQLite...");
        new Thread(() -> {
            try {
                boolean restored = authenticateUseCase.restoreSavedSession();
                if (restored) {
                    LOGGER.info("Sessão salva restaurada com sucesso. Redirecionando para a biblioteca...");
                    Platform.runLater(() -> {
                        updateStatus("✅ Sessão restaurada com sucesso!");
                        showLibrary();
                    });
                } else {
                    LOGGER.info("Nenhuma sessão salva válida encontrada. Aguardando login.");
                    Platform.runLater(() -> {
                        updateStatus("🔘 Aguardando autenticação...");
                    });
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao restaurar sessão salva: " + e.getMessage(), e);
                Platform.runLater(() -> {
                    updateStatus("🔘 Aguardando autenticação...");
                });
            }
        }, "PlayAnywhere-AutoLogin").start();
    }

    @FXML
    public void handleLogin() {
        handleLogin(null);
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        LOGGER.info("handleLogin acionado. Redirecionando para fluxo de autenticação oficial GOG / OAuth2...");
        updateStatus("🔐 Abrindo autenticação oficial da GOG...");
        handleOAuthLogin(event);
    }

    @FXML
    public void handleOAuthLogin() {
        handleOAuthLogin(null);
    }

    @FXML
    public void handleOAuthLogin(ActionEvent event) {
        LOGGER.info("Iniciando fluxo de login OAuth2 (GOG / Google)...");
        updateStatus("🔗 Abrindo autenticação GOG / Google...");
        Window currentWindow = resolveCurrentWindow();

        OAuthLoginDialog.open(currentWindow, code -> {
            LOGGER.info(() -> "Código de autorização recebido pelo LoginController. Disparando troca de token...");
            updateStatus("🔐 Trocando código por token de acesso...");
            new Thread(() -> {
                try {
                    String token = authenticateUseCase.exchangeCodeForToken(code);
                    LOGGER.info(() -> "Troca de token concluída com sucesso. Usuário autenticado.");
                    Platform.runLater(() -> {
                        updateStatus("✅ Autenticado com sucesso!");
                        showLibrary();
                    });
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Erro durante a troca de código por token: " + e.getMessage(), e);
                    Platform.runLater(() -> {
                        updateStatus("❌ Erro ao autenticar: " + e.getMessage());
                    });
                }
            }).start();
        }, error -> {
            LOGGER.log(Level.SEVERE, "Erro no diálogo de autenticação OAuth: " + error.getMessage(), error);
            updateStatus("❌ Erro ao abrir janela de login: " + error.getMessage());
        });
    }

    public void showLibrary() {
        LOGGER.info("Navegando para a visualização da biblioteca (LibraryController)...");
        libraryController = new LibraryController();
        Parent root = libraryController.getView();
        Stage stage = resolveCurrentStage();
        
        if (stage != null) {
            Scene scene = new Scene(root, 1024, 768);
            stage.setScene(scene);
            LOGGER.info("Cena da biblioteca configurada com sucesso no Stage principal.");
        } else {
            LOGGER.warning("Stage ativo não disponível imediatamente. Aguardando vinculação de janela para exibir biblioteca...");
            if (rootNode != null) {
                rootNode.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin instanceof Stage s) {
                                Platform.runLater(() -> {
                                    Scene scene = new Scene(root, 1024, 768);
                                    s.setScene(scene);
                                    LOGGER.info("Cena da biblioteca configurada com sucesso após vinculação de Stage.");
                                });
                            }
                        });
                        if (newScene.getWindow() instanceof Stage s) {
                            Platform.runLater(() -> {
                                Scene scene = new Scene(root, 1024, 768);
                                s.setScene(scene);
                                LOGGER.info("Cena da biblioteca configurada com sucesso após vinculação de Scene.");
                            });
                        }
                    }
                });
            }
        }
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private Window resolveCurrentWindow() {
        if (explicitStage != null) {
            return explicitStage;
        }
        if (rootNode != null && rootNode.getScene() != null && rootNode.getScene().getWindow() != null) {
            return rootNode.getScene().getWindow();
        }
        if (loginButton != null && loginButton.getScene() != null && loginButton.getScene().getWindow() != null) {
            return loginButton.getScene().getWindow();
        }
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .orElse(null);
    }

    private Stage resolveCurrentStage() {
        if (explicitStage != null) {
            return explicitStage;
        }
        Window window = resolveCurrentWindow();
        if (window instanceof Stage stage) {
            return stage;
        }
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage)
                .map(w -> (Stage) w)
                .findFirst()
                .orElse(null);
    }

    public Parent getView() {
        VBox root = new VBox(25);
        this.rootNode = root;
        root.setStyle("-fx-padding: 40; -fx-alignment: center; -fx-background-color: #1a1a2e;");
        
        Label title = new Label("🎮 PlayAnywhere GOG Edition");
        title.setStyle("-fx-font-size: 32px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label subtitle = new Label("Conecte-se à sua conta GOG para acessar e gerenciar sua biblioteca");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #a0a0b0;");

        loginButton = new Button("Entrar");
        loginButton.setPrefWidth(300);
        loginButton.setPrefHeight(50);
        loginButton.setStyle("-fx-background-color: #5c2d91; -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 10; -fx-cursor: hand;");
        loginButton.setOnAction(e -> handleLogin());

        statusLabel = new Label("🔘 Aguardando autenticação...");
        statusLabel.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 14px;");

        root.getChildren().addAll(
            title, 
            subtitle, 
            loginButton, 
            statusLabel
        );
        attemptAutoLogin();
        return root;
    }
}
