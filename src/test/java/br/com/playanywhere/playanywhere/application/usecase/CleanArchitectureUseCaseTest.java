package br.com.playanywhere.playanywhere.application.usecase;

import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.repository.AuthGateway;
import br.com.playanywhere.playanywhere.domain.repository.GameRegistryRepository;
import br.com.playanywhere.playanywhere.domain.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CleanArchitectureUseCaseTest {

    @TempDir
    Path tempDir;

    @Test
    void testInstallGameUseCase() throws IOException {
        List<InstalledGame> registryList = new ArrayList<>();
        GameRegistryRepository mockRegistry = new GameRegistryRepository() {
            @Override
            public void registerGame(Product product, Path targetPath, boolean autoRunner) {
                registryList.add(new InstalledGame(product, targetPath, autoRunner));
            }

            @Override
            public List<InstalledGame> getInstalledGames() {
                return registryList;
            }

            @Override
            public void saveRegistry() {}

            @Override
            public void loadRegistry() {}
        };

        InstallGameUseCase useCase = new InstallGameUseCase(mockRegistry);

        Path source = tempDir.resolve("src-game");
        Path target = tempDir.resolve("dst-game");
        Files.createDirectories(source);
        Files.writeString(source.resolve("game.exe"), "executable-content");

        Product product = new Product(101, "Witcher", "witcher");
        GameInstallationRequest request = new GameInstallationRequest(
            product,
            InstallationType.LOCAL_DIRECTORY,
            target,
            source,
            true
        );

        useCase.execute(request);

        assertTrue(Files.exists(target.resolve("game.exe")));
        assertEquals(1, registryList.size());
        assertEquals("Witcher", registryList.get(0).product().getTitle());
        assertTrue(registryList.get(0).autoRunner());
    }

    @Test
    void testGetLibraryUseCase() {
        GameRepository mockRepo = new GameRepository() {
            @Override
            public List<Product> getLibraryProducts(int page, int pageSize) {
                return List.of(new Product(1, "Game 1", "game-1"), new Product(2, "Game 2", "game-2"));
            }

            @Override
            public Product getProductDetails(int productId) {
                return new Product(productId, "Game " + productId, "game-" + productId);
            }
        };

        GetLibraryUseCase useCase = new GetLibraryUseCase(mockRepo);
        List<Product> products = useCase.execute(1, 10);

        assertNotNull(products);
        assertEquals(2, products.size());
        assertEquals("Game 1", products.get(0).getTitle());
    }

    @Test
    void testAuthenticateUseCase() throws Exception {
        AuthGateway mockAuth = new AuthGateway() {
            private String token = null;

            @Override
            public String buildAuthorizationUrl() {
                return "https://auth.gog.com/auth?client_id=123";
            }

            @Override
            public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) {
                return "https://auth.gog.com/auth?client_id=" + clientId;
            }

            @Override
            public String extractCodeFromUrl(String url) {
                return "auth-code-123";
            }

            @Override
            public String exchangeCodeForToken(String code) {
                this.token = "bearer-token-abc";
                return this.token;
            }

            @Override
            public boolean isValidToken() {
                return token != null;
            }

            @Override
            public String getAccessToken() {
                return token;
            }

            @Override
            public void setAccessToken(String accessToken) {
                this.token = accessToken;
            }

            @Override
            public String getRefreshToken() {
                return "refresh-123";
            }

            @Override
            public String getUserId() {
                return "user-999";
            }
        };

        AuthenticateUseCase useCase = new AuthenticateUseCase(mockAuth);

        assertEquals("https://auth.gog.com/auth?client_id=123", useCase.buildAuthorizationUrl());
        assertEquals("auth-code-123", useCase.extractCodeFromUrl("https://example.com?code=auth-code-123"));
        
        assertFalse(useCase.isAuthenticated());
        String token = useCase.exchangeCodeForToken("auth-code-123");
        assertEquals("bearer-token-abc", token);
        assertTrue(useCase.isAuthenticated());
        assertEquals("user-999", useCase.getUserId());
    }
}
