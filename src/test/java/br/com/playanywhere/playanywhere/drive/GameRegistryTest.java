package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRegistryTest {

    private GameRegistry registry;

    @BeforeEach
    void setUp() {
        registry = GameRegistry.getInstance();
    }

    @Test
    void testRegisterAndUnregisterGame(@TempDir Path tempDir) {
        Path gameDir = tempDir.resolve("Witcher3");
        Product product = new Product(101, "The Witcher 3", "witcher-3", 35000);

        registry.registerGame(product, gameDir, true);

        List<InstalledGame> games = registry.getInstalledGames();
        assertTrue(games.stream().anyMatch(g -> g.targetPath().equals(gameDir) && g.product().getTitle().equals("The Witcher 3")));

        // Unregister
        registry.unregisterGame(gameDir);

        List<InstalledGame> gamesAfter = registry.getInstalledGames();
        assertFalse(gamesAfter.stream().anyMatch(g -> g.targetPath().equals(gameDir)));
    }

    @Test
    void testRegisterReplacesDuplicateTargetPath(@TempDir Path tempDir) {
        Path gameDir = tempDir.resolve("Cyberpunk2077");
        Product product1 = new Product(102, "Cyberpunk Old", "cyberpunk", 50000);
        Product product2 = new Product(102, "Cyberpunk 2077", "cyberpunk-2077", 65000);

        registry.registerGame(product1, gameDir, false);
        registry.registerGame(product2, gameDir, true);

        List<InstalledGame> games = registry.getInstalledGames();
        long count = games.stream().filter(g -> g.targetPath().equals(gameDir)).count();
        assertEquals(1, count);

        InstalledGame game = games.stream().filter(g -> g.targetPath().equals(gameDir)).findFirst().orElseThrow();
        assertEquals("Cyberpunk 2077", game.product().getTitle());
        assertTrue(game.isAutoRunner());

        // Cleanup
        registry.unregisterGame(gameDir);
    }

    @Test
    void testPruneMissingGamesWhenDirectoryDeleted(@TempDir Path tempDir) throws Exception {
        Path gameDir = tempDir.resolve("TempGame");
        Files.createDirectories(gameDir);
        Product product = new Product(103, "Temporary Game", "temp-game", 1000);

        registry.registerGame(product, gameDir, false);
        assertTrue(registry.getInstalledGames().stream().anyMatch(g -> g.targetPath().equals(gameDir)));

        // Simula deleção do diretório / desconexão de pendrive
        Files.delete(gameDir);

        List<InstalledGame> games = registry.getInstalledGames();
        assertFalse(games.stream().anyMatch(g -> g.targetPath().equals(gameDir)));
    }
}
