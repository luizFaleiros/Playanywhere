package br.com.playanywhere.playanywhere.presentation.controller;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.drive.GameRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AddManualGameDialogTest {

    @Test
    void testProcessManualAdditionDirectory(@TempDir Path tempDir) throws IOException {
        Path gameDir = tempDir.resolve("HollowKnight");
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve("game.exe"), "dummy exe");

        AtomicReference<InstalledGame> addedGameRef = new AtomicReference<>();
        AddManualGameDialog dialog = new AddManualGameDialog(null, addedGameRef::set);

        boolean success = dialog.processManualAddition(
                gameDir.toString(),
                "Hollow Knight",
                "game.exe",
                "1500",
                true
        );

        assertTrue(success);
        assertNotNull(addedGameRef.get());
        assertEquals("Hollow Knight", addedGameRef.get().product().getTitle());
        assertEquals(1500, addedGameRef.get().product().getSizeMB());
        assertTrue(addedGameRef.get().isAutoRunner());

        // Verify playanywhere.json created
        Path metaFile = gameDir.resolve("playanywhere.json");
        assertTrue(Files.exists(metaFile));

        PlayAnywhereMetadata meta = new ObjectMapper().readValue(metaFile.toFile(), PlayAnywhereMetadata.class);
        assertEquals("Hollow Knight", meta.title());
        assertEquals("game.exe", meta.executable());
        assertTrue(meta.autoRunner());

        // Cleanup
        GameRegistry.getInstance().unregisterGame(gameDir);
    }

    @Test
    void testProcessManualAdditionExecutableFile(@TempDir Path tempDir) throws IOException {
        Path gameDir = tempDir.resolve("Celeste");
        Files.createDirectories(gameDir);
        Path exeFile = gameDir.resolve("Celeste.exe");
        Files.writeString(exeFile, "dummy");

        AtomicReference<InstalledGame> addedGameRef = new AtomicReference<>();
        AddManualGameDialog dialog = new AddManualGameDialog(null, addedGameRef::set);

        boolean success = dialog.processManualAddition(
                exeFile.toString(),
                "Celeste",
                "Celeste.exe",
                "800",
                false
        );

        assertTrue(success);
        assertNotNull(addedGameRef.get());
        assertEquals("Celeste", addedGameRef.get().product().getTitle());
        assertFalse(addedGameRef.get().isAutoRunner());

        // Cleanup
        GameRegistry.getInstance().unregisterGame(gameDir);
    }

    @Test
    void testProcessManualAdditionNonExistentPath() {
        AddManualGameDialog dialog = new AddManualGameDialog(null, null);
        boolean success = dialog.processManualAddition(
                "/path/that/does/not/exist/at/all",
                "Ghost Game",
                "game.exe",
                "1000",
                true
        );

        assertFalse(success);
    }
}
