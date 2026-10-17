package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.api.GOGApi;
import br.com.playanywhere.playanywhere.application.usecase.AuthenticateUseCase;
import br.com.playanywhere.playanywhere.application.usecase.GetLibraryUseCase;
import br.com.playanywhere.playanywhere.domain.model.GalleryImage;
import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.drive.GameRegistry;
import br.com.playanywhere.playanywhere.drive.MountListener;
import br.com.playanywhere.playanywhere.drive.MountMonitor;
import br.com.playanywhere.playanywhere.launcher.GameLauncher;
import br.com.playanywhere.playanywhere.launcher.LaunchResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller unificado para a biblioteca de jogos (Game Grid / Library).
 * Suporta abas para Catálogo GOG e Jogos Instalados, além de adição manual de jogos.
 */
public class LibraryController {
    private static final Logger LOGGER = Logger.getLogger(LibraryController.class.getName());

    public enum TabType {
        CATALOG,
        INSTALLED
    }

    @FXML private Label usernameLabel;
    @FXML private GridPane gameGrid;

    private final GetLibraryUseCase getLibraryUseCase;
    private final GameRegistry gameRegistry;
    private final GameLauncher gameLauncher;

    private final List<Product> products = new ArrayList<>();
    private final List<InstalledGame> installedGames = new ArrayList<>();

    private TabType activeTab = TabType.CATALOG;
    private FlowPane gamesContainer;
    private FlowPane installedGamesContainer;
    private ScrollPane centerScrollPane;
    private Button catalogTabBtn;
    private Button installedTabBtn;
    private Button addGameBtn;
    private Label statusLabel;
    private Parent rootNode;
    private String currentSearchFilter = "";

    public LibraryController() {
        this(new GetLibraryUseCase(GOGApi.getInstance()), GameRegistry.getInstance(), new GameLauncher());
    }

    public LibraryController(GetLibraryUseCase getLibraryUseCase) {
        this(getLibraryUseCase, GameRegistry.getInstance(), new GameLauncher());
    }

    public LibraryController(GetLibraryUseCase getLibraryUseCase, GameRegistry gameRegistry, GameLauncher gameLauncher) {
        this.getLibraryUseCase = getLibraryUseCase;
        this.gameRegistry = gameRegistry;
        this.gameLauncher = gameLauncher;
    }

    @FXML
    public void initialize() {
        LOGGER.info("Inicializando LibraryController...");
        updateUserDisplay();
        loadGames();
        loadInstalledGames();
    }

    private void updateUserDisplay() {
        String username = GOGApi.getInstance().getUsername();
        if (username == null || username.trim().isEmpty()) {
            username = GOGApi.getInstance().getUserId();
        }
        String displayName = username != null && !username.trim().isEmpty() ? username : "Jogador GOG";
        if (usernameLabel != null) {
            usernameLabel.setText("👤 " + displayName);
        }
    }

    public void loadGames() {
        LOGGER.info("Iniciando requisição assíncrona para carregar jogos da biblioteca GOG...");
        if (statusLabel != null) {
            statusLabel.setText("🔄 Carregando todos os jogos da sua conta GOG...");
        }

        new Thread(() -> {
            try {
                List<Product> loaded = getLibraryUseCase.execute(1, 50);
                Platform.runLater(() -> {
                    updateUserDisplay();
                    products.clear();
                    if (loaded != null && !loaded.isEmpty()) {
                        LOGGER.info(() -> "Recebidos " + loaded.size() + " jogos da API da GOG.");
                        products.addAll(loaded);
                        renderGames();
                        updateTabButtons();
                        if (statusLabel != null) {
                            statusLabel.setText("✅ " + products.size() + " jogos carregados com sucesso da sua conta GOG!");
                        }
                    } else {
                        LOGGER.warning("Nenhum produto retornado pela API da GOG. Carregando catálogo de exemplo.");
                        products.addAll(createSampleProducts());
                        renderGames();
                        updateTabButtons();
                        if (statusLabel != null) {
                            statusLabel.setText("📦 " + products.size() + " jogos disponíveis (Modo Exemplo/Offline)");
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao carregar jogos da API GOG (" + e.getMessage() + "). Alternando para modo offline.", e);
                Platform.runLater(() -> {
                    updateUserDisplay();
                    products.clear();
                    products.addAll(createSampleProducts());
                    renderGames();
                    updateTabButtons();
                    if (statusLabel != null) {
                        statusLabel.setText("📦 " + products.size() + " jogos disponíveis (Modo Offline - " + e.getMessage() + ")");
                    }
                });
            }
        }).start();
    }

    public void loadInstalledGames() {
        LOGGER.info("Carregando lista de jogos instalados do registro local...");
        List<InstalledGame> loaded = gameRegistry.getInstalledGames();
        installedGames.clear();
        if (loaded != null) {
            installedGames.addAll(loaded);
        }
        LOGGER.info(() -> "Total de jogos instalados registrados: " + installedGames.size());
        Platform.runLater(() -> {
            renderInstalledGames();
            updateTabButtons();
        });
    }

    public void switchTab(TabType tab) {
        this.activeTab = tab;
        updateTabButtons();
        if (centerScrollPane != null) {
            if (activeTab == TabType.CATALOG) {
                renderGames();
                centerScrollPane.setContent(gamesContainer);
            } else {
                renderInstalledGames();
                centerScrollPane.setContent(installedGamesContainer);
            }
        }
    }

    private void updateTabButtons() {
        if (catalogTabBtn != null) {
            catalogTabBtn.setText("🎮 Catálogo GOG (" + products.size() + ")");
            if (activeTab == TabType.CATALOG) {
                catalogTabBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #4ecca3; -fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: #4ecca3; -fx-border-width: 0 0 3 0; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
            } else {
                catalogTabBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a0a0b0; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
            }
        }

        if (installedTabBtn != null) {
            installedTabBtn.setText("💾 Jogos Instalados (" + installedGames.size() + ")");
            if (activeTab == TabType.INSTALLED) {
                installedTabBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #4ecca3; -fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: #4ecca3; -fx-border-width: 0 0 3 0; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
            } else {
                installedTabBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #a0a0b0; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 8 16 8 16;");
            }
        }
    }

    private void renderGames() {
        if (gamesContainer == null) return;
        LOGGER.fine(() -> "Renderizando catálogo GOG (total=" + products.size() + ", filtro='" + currentSearchFilter + "')...");
        gamesContainer.getChildren().clear();

        for (Product product : products) {
            if (currentSearchFilter != null && !currentSearchFilter.trim().isEmpty()) {
                String title = product.getTitle() != null ? product.getTitle().toLowerCase() : "";
                if (!title.contains(currentSearchFilter.toLowerCase())) {
                    continue;
                }
            }
            VBox card = createGameCard(product);
            gamesContainer.getChildren().add(card);
        }
    }

    private void renderInstalledGames() {
        if (installedGamesContainer == null) return;
        LOGGER.fine(() -> "Renderizando jogos instalados (total=" + installedGames.size() + ", filtro='" + currentSearchFilter + "')...");
        installedGamesContainer.getChildren().clear();

        List<InstalledGame> filtered = new ArrayList<>();
        for (InstalledGame game : installedGames) {
            if (currentSearchFilter != null && !currentSearchFilter.trim().isEmpty()) {
                String title = (game.product() != null && game.product().getTitle() != null) 
                        ? game.product().getTitle().toLowerCase() 
                        : (game.targetPath() != null ? game.targetPath().toString().toLowerCase() : "");
                if (!title.contains(currentSearchFilter.toLowerCase())) {
                    continue;
                }
            }
            filtered.add(game);
        }

        if (filtered.isEmpty()) {
            VBox emptyState = createEmptyInstalledState();
            installedGamesContainer.getChildren().add(emptyState);
            return;
        }

        for (InstalledGame game : filtered) {
            VBox card = createInstalledGameCard(game);
            installedGamesContainer.getChildren().add(card);
        }
    }

    private VBox createEmptyInstalledState() {
        VBox emptyBox = new VBox(15);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(40, 20, 40, 20));
        emptyBox.setPrefWidth(800);

        Label iconLabel = new Label("📦");
        iconLabel.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label("Nenhum jogo instalado encontrado");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label descLabel = new Label("Conecte um pendrive com jogos ou adicione um jogo manualmente à sua biblioteca.");
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #a0a0b0;");

        Button addBtn = new Button("➕ Adicionar Jogo Manualmente");
        addBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 10 20 10 20;");
        addBtn.setOnAction(e -> handleAddManualGame());

        emptyBox.getChildren().addAll(iconLabel, titleLabel, descLabel, addBtn);
        return emptyBox;
    }

    private VBox createGameCard(Product product) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setMaxWidth(200);
        card.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8; -fx-border-color: #0f3460; -fx-border-radius: 8;");

        ImageView coverImage = new ImageView();
        coverImage.setFitWidth(180);
        coverImage.setFitHeight(110);
        coverImage.setPreserveRatio(true);

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
                    coverImage.setImage(new Image(normalizedUrl, true));
                } catch (Exception e) {
                    LOGGER.fine(() -> "Falha ao carregar imagem para o produto " + product.getTitle() + ": " + e.getMessage());
                }
            }
        }

        Label titleLabel = new Label(product.getTitle() != null ? product.getTitle() : "Jogo");
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 13px;");
        titleLabel.setMaxWidth(180);

        Label sizeLabel = new Label("💾 " + product.getFormattedSize() + " • " + product.getRecommendedStorageType().getCategory());
        sizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4ecca3;");
        sizeLabel.setMaxWidth(180);

        Button installBtn = new Button("⬇️ Instalar");
        installBtn.setMaxWidth(Double.MAX_VALUE);
        installBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-cursor: hand;");
        installBtn.setOnAction(e -> {
            LOGGER.info(() -> "Usuário clicou em Instalar para o jogo: " + product.getTitle() + " (ID=" + product.getId() + ")");
            Window window = resolveCurrentWindow();
            InstallController.open(window, product);
        });

        card.getChildren().addAll(coverImage, titleLabel, sizeLabel, installBtn);
        return card;
    }

    private VBox createInstalledGameCard(InstalledGame game) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(210);
        card.setMaxWidth(210);
        card.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8; -fx-border-color: #0f3460; -fx-border-radius: 8;");

        Product product = game.product();
        String gameTitle = product != null && product.getTitle() != null 
                ? product.getTitle() 
                : (game.targetPath() != null ? game.targetPath().getFileName().toString() : "Jogo Instalado");

        // Banner de imagem ou placeholder
        StackPane imageHolder = new StackPane();
        imageHolder.setPrefSize(190, 110);
        imageHolder.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 6;");

        ImageView coverImage = new ImageView();
        coverImage.setFitWidth(190);
        coverImage.setFitHeight(110);
        coverImage.setPreserveRatio(true);

        String imageUrl = null;
        if (product != null && product.getGallery() != null && !product.getGallery().isEmpty()) {
            GalleryImage firstImg = product.getGallery().get(0);
            if (firstImg != null && firstImg.getImagePath() != null) {
                imageUrl = firstImg.getImagePath();
            }
        }

        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            String normalizedUrl = GOGApi.normalizeImageUrl(imageUrl);
            if (normalizedUrl != null) {
                try {
                    coverImage.setImage(new Image(normalizedUrl, true));
                } catch (Exception ignored) {}
            }
            imageHolder.getChildren().add(coverImage);
        } else {
            Label gameIcon = new Label("🎮\n" + gameTitle);
            gameIcon.setWrapText(true);
            gameIcon.setAlignment(Pos.CENTER);
            gameIcon.setStyle("-fx-text-fill: #4ecca3; -fx-font-weight: bold; -fx-font-size: 13px; -fx-text-alignment: center;");
            imageHolder.getChildren().add(gameIcon);
        }

        Label titleLabel = new Label(gameTitle);
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 13px;");
        titleLabel.setMaxWidth(190);

        // Path label with tooltip
        String pathStr = game.targetPath() != null ? game.targetPath().toString() : "Local";
        String displayPath = game.targetPath() != null ? ("📍 " + game.targetPath().getFileName().toString()) : "📍 Local";
        Label pathLabel = new Label(displayPath);
        pathLabel.setTooltip(new Tooltip(pathStr));
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8888aa;");
        pathLabel.setMaxWidth(190);

        String sizeText = product != null ? product.getFormattedSize() : "";
        String autoRunText = game.isAutoRunner() ? "⚡ Auto-Run" : "Manual";
        Label detailsLabel = new Label("💾 " + (sizeText.isEmpty() ? "Pronto" : sizeText) + " • " + autoRunText);
        detailsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4ecca3;");
        detailsLabel.setMaxWidth(190);

        // Buttons
        Button playBtn = new Button("▶️ Jogar");
        playBtn.setMaxWidth(Double.MAX_VALUE);
        playBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4;");
        playBtn.setOnAction(e -> handleLaunchGame(game));

        Button openFolderBtn = new Button("📂 Pasta");
        openFolderBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4;");
        openFolderBtn.setOnAction(e -> openGameFolder(game.targetPath()));

        Button removeBtn = new Button("🗑️");
        removeBtn.setTooltip(new Tooltip("Remover da lista de instalados"));
        removeBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4;");
        removeBtn.setOnAction(e -> handleRemoveGame(game));

        HBox actionRow = new HBox(6, openFolderBtn, removeBtn);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(openFolderBtn, Priority.ALWAYS);

        card.getChildren().addAll(imageHolder, titleLabel, pathLabel, detailsLabel, playBtn, actionRow);
        return card;
    }

    private void handleLaunchGame(InstalledGame game) {
        if (game == null || game.targetPath() == null) return;
        String title = game.product() != null ? game.product().getTitle() : game.targetPath().getFileName().toString();
        LOGGER.info(() -> "Iniciando lançamento manual do jogo: " + title);
        if (statusLabel != null) {
            statusLabel.setText("🚀 Iniciando " + title + "...");
        }

        new Thread(() -> {
            LaunchResult result = gameLauncher.launchGame(game.targetPath());
            Platform.runLater(() -> {
                if (result.success()) {
                    if (statusLabel != null) {
                        statusLabel.setText("✅ Jogo " + title + " iniciado com sucesso via " + result.runnerUsed() + "!");
                    }
                } else {
                    if (statusLabel != null) {
                        statusLabel.setText("❌ Falha ao iniciar " + title + ": " + result.errorMessage());
                    }
                }
            });
        }, "PlayAnywhere-ManualLauncher").start();
    }

    public static void openGameFolder(Path path) {
        if (path == null || !Files.exists(path)) return;
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(path.toFile());
                } else {
                    String os = System.getProperty("os.name", "").toLowerCase();
                    if (os.contains("win")) {
                        new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
                    } else if (os.contains("mac")) {
                        new ProcessBuilder("open", path.toAbsolutePath().toString()).start();
                    } else {
                        new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Não foi possível abrir a pasta " + path + ": " + e.getMessage());
            }
        }, "PlayAnywhere-OpenFolder").start();
    }

    private void handleRemoveGame(InstalledGame game) {
        if (game == null || game.targetPath() == null) return;
        String title = game.product() != null ? game.product().getTitle() : game.targetPath().getFileName().toString();
        LOGGER.info(() -> "Removendo jogo instalado do registro: " + title + " em " + game.targetPath());
        gameRegistry.unregisterGame(game.targetPath());
        loadInstalledGames();
        if (statusLabel != null) {
            statusLabel.setText("🗑️ Jogo '" + title + "' removido da lista de instalados.");
        }
    }

    public void handleAddManualGame() {
        LOGGER.info("Abrindo diálogo para adicionar jogo manualmente...");
        Window window = resolveCurrentWindow();
        AddManualGameDialog.open(window, addedGame -> {
            LOGGER.info(() -> "Novo jogo adicionado manualmente: " + (addedGame != null ? addedGame.product().getTitle() : ""));
            Platform.runLater(() -> {
                loadInstalledGames();
                switchTab(TabType.INSTALLED);
                if (statusLabel != null && addedGame != null) {
                    statusLabel.setText("✅ Jogo '" + addedGame.product().getTitle() + "' adicionado com sucesso!");
                }
            });
        });
    }

    private List<Product> createSampleProducts() {
        List<Product> list = new ArrayList<>();
        list.add(createSampleProduct(1, "The Witcher 3: Wild Hunt", "the-witcher-3-wild-hunt", "https://images-1.gog-statics.com/3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b_product_tile_256_2x.jpg", 35000));
        list.add(createSampleProduct(2, "Cyberpunk 2077", "cyberpunk-2077", "https://images-1.gog-statics.com/a976f625902095bb120c9305141ef3141f0212a4ee605d52bcfe2b9921f0054e_product_tile_256_2x.jpg", 65000));
        list.add(createSampleProduct(3, "Hollow Knight", "hollow-knight", "https://images-1.gog-statics.com/c6fca66e07c87c9dc0df25d978a3c9b139eb62f01901dd1a7337da8dfa64be4d_product_tile_256_2x.jpg", 1200));
        list.add(createSampleProduct(4, "Stardew Valley", "stardew-valley", "https://images-1.gog-statics.com/26ea724771daee0575d3c8c7f96dfa220268579d424b9015c928811d0a514d7c_product_tile_256_2x.jpg", 500));
        list.add(createSampleProduct(5, "Dead Cells", "dead-cells", "https://images-1.gog-statics.com/269145610e7eece3d0c6ca8018258fb4ce00ca25e24cb1c6253457a44fbc7591_product_tile_256_2x.jpg", 1500));
        return list;
    }

    private Product createSampleProduct(int id, String title, String slug, String imageUrl, long sizeMB) {
        Product p = new Product(id, title, slug, sizeMB);
        if (imageUrl != null) {
            List<GalleryImage> gallery = new ArrayList<>();
            gallery.add(new GalleryImage(imageUrl, "", "jpg"));
            p.setGallery(gallery);
        }
        return p;
    }

    public Parent getView() {
        BorderPane root = new BorderPane();
        this.rootNode = root;
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Top Header
        VBox topContainer = new VBox(0);

        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 25, 12, 25));
        topBar.setStyle("-fx-background-color: #16213e; -fx-border-color: #0f3460; -fx-border-width: 0 0 1 0;");

        Label title = new Label("🎮 PlayAnywhere");
        title.setStyle("-fx-font-size: 22px; -fx-text-fill: white; -fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Buscar jogo...");
        searchField.setPrefWidth(220);
        searchField.setStyle("-fx-background-color: #2d2d44; -fx-text-fill: white; -fx-prompt-text-fill: #8888aa; -fx-background-radius: 6;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchFilter = newVal;
            if (activeTab == TabType.CATALOG) {
                renderGames();
            } else {
                renderInstalledGames();
            }
        });

        addGameBtn = new Button("➕ Adicionar Jogo");
        addGameBtn.setStyle("-fx-background-color: #4ecca3; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6;");
        addGameBtn.setOnAction(e -> handleAddManualGame());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        usernameLabel = new Label();
        usernameLabel.setStyle("-fx-text-fill: #00adb5; -fx-font-size: 14px; -fx-font-weight: bold;");
        updateUserDisplay();

        Button refreshBtn = new Button("🔄 Atualizar");
        refreshBtn.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6;");
        refreshBtn.setOnAction(e -> {
            LOGGER.info("Botão de atualização de biblioteca acionado pelo usuário.");
            loadGames();
            loadInstalledGames();
        });

        Button logoutBtn = new Button("🚪 Sair");
        logoutBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold; -fx-background-radius: 6;");
        logoutBtn.setOnAction(e -> {
            LOGGER.info("Usuário solicitou logout. Limpando credenciais salvas...");
            new AuthenticateUseCase(GOGApi.getInstance()).logout();
            showLogin();
        });

        topBar.getChildren().addAll(title, searchField, addGameBtn, spacer, usernameLabel, refreshBtn, logoutBtn);

        // Tab Navigation Bar
        HBox tabNavBar = new HBox(10);
        tabNavBar.setAlignment(Pos.CENTER_LEFT);
        tabNavBar.setPadding(new Insets(8, 25, 0, 25));
        tabNavBar.setStyle("-fx-background-color: #121829; -fx-border-color: #0f3460; -fx-border-width: 0 0 1 0;");

        catalogTabBtn = new Button("🎮 Catálogo GOG (0)");
        catalogTabBtn.setOnAction(e -> switchTab(TabType.CATALOG));

        installedTabBtn = new Button("💾 Jogos Instalados (0)");
        installedTabBtn.setOnAction(e -> switchTab(TabType.INSTALLED));

        tabNavBar.getChildren().addAll(catalogTabBtn, installedTabBtn);
        topContainer.getChildren().addAll(topBar, tabNavBar);
        root.setTop(topContainer);

        // Center Containers
        gamesContainer = new FlowPane(15, 15);
        gamesContainer.setPadding(new Insets(20));
        gamesContainer.setStyle("-fx-background-color: transparent;");

        installedGamesContainer = new FlowPane(15, 15);
        installedGamesContainer.setPadding(new Insets(20));
        installedGamesContainer.setStyle("-fx-background-color: transparent;");

        centerScrollPane = new ScrollPane(gamesContainer);
        centerScrollPane.setFitToWidth(true);
        centerScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        root.setCenter(centerScrollPane);

        // Bottom - Status Bar
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8, 20, 8, 20));
        statusBar.setStyle("-fx-background-color: #16213e; -fx-border-color: #0f3460; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("🔘 Pronto. Monitorando pendrives e mídias removíveis...");
        statusLabel.setStyle("-fx-text-fill: #ffd700; -fx-font-size: 12px;");
        statusBar.getChildren().add(statusLabel);
        root.setBottom(statusBar);

        // Registro de listener no MountMonitor para atualizações em tempo real
        MountMonitor.getInstance().addMountListener(new MountListener() {
            @Override
            public void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("🎮 Pendrive conectado: " + metadata.title() + " detectado!");
                    }
                    loadInstalledGames();
                });
            }

            @Override
            public void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("🚀 Jogo iniciado: " + metadata.title() + " via " + result.runnerUsed() + "!");
                    }
                    loadInstalledGames();
                });
            }

            @Override
            public void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {
                Platform.runLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("⚠️ Falha ao abrir " + metadata.title() + ": " + error);
                    }
                    loadInstalledGames();
                });
            }

            @Override
            public void onMountsChanged() {
                Platform.runLater(() -> {
                    loadInstalledGames();
                });
            }
        });

        loadGames();
        loadInstalledGames();
        updateTabButtons();

        return root;
    }

    private Window resolveCurrentWindow() {
        if (rootNode != null && rootNode.getScene() != null && rootNode.getScene().getWindow() != null) {
            return rootNode.getScene().getWindow();
        }
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .findFirst()
                .orElse(null);
    }

    public void showLogin() {
        LoginController loginController = new LoginController();
        Parent loginRoot = loginController.getView();
        Window window = resolveCurrentWindow();
        if (window instanceof Stage stage) {
            Scene scene = new Scene(loginRoot, 1024, 768);
            stage.setScene(scene);
            LOGGER.info("Retornado para a tela de login após logout.");
        }
    }
}
