package br.com.playanywhere.playanywhere.launcher;

import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GameLauncherTest {

    @TempDir
    Path tempDir;

    private GameLauncher launcher;

    @BeforeEach
    void setUp() {
        launcher = new GameLauncher();
        GameLauncher.resetSimulatedPlatform();
        ProtonDetector.resetCustomProtonPath();
        WineDetector.resetCustomWinePath();
    }

    @AfterEach
    void tearDown() {
        GameLauncher.resetSimulatedPlatform();
        ProtonDetector.resetCustomProtonPath();
        WineDetector.resetCustomWinePath();
    }

    @Test
    void testReadMetadata() throws IOException {
        Path gameDir = tempDir.resolve("game1");
        Files.createDirectories(gameDir);

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            101, "The Witcher 3", "the-witcher-3", "witcher3.exe",
            35000, "34.18 GB", "Pen Drive USB 64GB+", "Pen Drive",
            true, "2026-10-13T12:00:00Z", List.of("-fullscreen"), "AUTO"
        );

        new ObjectMapper().writeValue(gameDir.resolve("playanywhere.json").toFile(), meta);

        Optional<PlayAnywhereMetadata> read = launcher.readMetadata(gameDir);
        assertTrue(read.isPresent());
        assertEquals("The Witcher 3", read.get().title());
        assertEquals("witcher3.exe", read.get().executable());
        assertTrue(read.get().autoRunner());
    }

    @Test
    void testFindExecutableFromMetadata() throws IOException {
        Path gameDir = tempDir.resolve("game2");
        Files.createDirectories(gameDir);
        Path exePath = gameDir.resolve("custom_runner.exe");
        Files.writeString(exePath, "fake exe content");

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            1, "Custom", "custom", "custom_runner.exe",
            100, "100 MB", "Pen Drive", "Pen Drive",
            true, null, null, null
        );

        Path found = launcher.findExecutable(gameDir, meta);
        assertNotNull(found);
        assertEquals(exePath, found);
    }

    @Test
    void testFindExecutableDefaultScans() throws IOException {
        Path gameDir = tempDir.resolve("game3");
        Files.createDirectories(gameDir);
        Path exePath = gameDir.resolve("game.exe");
        Files.writeString(exePath, "fake game content");

        Path found = launcher.findExecutable(gameDir, null);
        assertNotNull(found);
        assertEquals(exePath, found);
    }

    @Test
    void testLaunchOnLinuxWithNativeScript() throws IOException {
        GameLauncher.setSimulatedPlatform(PlatformType.LINUX);

        Path gameDir = tempDir.resolve("game_linux_native");
        Files.createDirectories(gameDir);
        Path scriptPath = gameDir.resolve("start_game.sh");
        Files.writeString(scriptPath, "#!/bin/bash\necho 'Running native linux game'\nexit 0\n");

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            2, "Linux Indie", "linux-indie", "start_game.sh",
            200, "200 MB", "CD", "CD", true, null, List.of(), "NATIVE"
        );

        LaunchResult result = launcher.launchGame(gameDir, meta);
        assertTrue(result.success());
        assertEquals("LINUX_NATIVE", result.runnerUsed());
        assertEquals("/bin/bash", result.command().get(0));
    }

    @Test
    void testLaunchOnLinuxWithProton() throws IOException {
        GameLauncher.setSimulatedPlatform(PlatformType.LINUX);

        // Cria falso executável do Proton
        Path fakeProton = tempDir.resolve("proton");
        Files.writeString(fakeProton, "#!/bin/bash\necho 'Proton running'\nexit 0\n");
        fakeProton.toFile().setExecutable(true);
        ProtonDetector.setCustomProtonPath(fakeProton);

        Path gameDir = tempDir.resolve("game_proton");
        Files.createDirectories(gameDir);
        Path exePath = gameDir.resolve("game.exe");
        Files.writeString(exePath, "fake exe content");

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            3, "Cyberpunk 2077", "cyberpunk-2077", "game.exe",
            65000, "63.48 GB", "Pen Drive", "Pen Drive", true, null, List.of("-no-intro"), "AUTO"
        );

        LaunchResult result = launcher.launchGame(gameDir, meta);
        assertTrue(result.success());
        assertEquals("PROTON", result.runnerUsed());
        assertEquals(fakeProton.toAbsolutePath().toString(), result.command().get(0));
        assertEquals("run", result.command().get(1));
        assertEquals(exePath.toAbsolutePath().toString(), result.command().get(2));
        assertTrue(result.command().contains("-no-intro"));
    }

    @Test
    void testLaunchOnLinuxWithWineFallbackWhenProtonNotAvailable() throws IOException {
        GameLauncher.setSimulatedPlatform(PlatformType.LINUX);

        // Proton desabilitado
        ProtonDetector.setForceDisabled(true);

        // Cria falso executável do Wine
        Path fakeWine = tempDir.resolve("wine");
        Files.writeString(fakeWine, "#!/bin/bash\necho 'Wine running'\nexit 0\n");
        fakeWine.toFile().setExecutable(true);
        WineDetector.setCustomWinePath(fakeWine);

        Path gameDir = tempDir.resolve("game_wine");
        Files.createDirectories(gameDir);
        Path exePath = gameDir.resolve("game.exe");
        Files.writeString(exePath, "fake exe content");

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            4, "Hollow Knight", "hollow-knight", "game.exe",
            1200, "1.17 GB", "DVD", "DVD", true, null, List.of(), "AUTO"
        );

        LaunchResult result = launcher.launchGame(gameDir, meta);
        assertTrue(result.success());
        assertEquals("WINE", result.runnerUsed());
        assertEquals(fakeWine.toAbsolutePath().toString(), result.command().get(0));
        assertEquals(exePath.toAbsolutePath().toString(), result.command().get(1));
    }

    @Test
    void testLaunchOnLinuxWithFallbackScriptWhenNoWineNorProton() throws IOException {
        GameLauncher.setSimulatedPlatform(PlatformType.LINUX);

        ProtonDetector.setForceDisabled(true);
        WineDetector.setForceDisabled(true);

        Path gameDir = tempDir.resolve("game_script_fallback");
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve("game.exe"), "fake exe");
        Path startScript = gameDir.resolve("start_game.sh");
        Files.writeString(startScript, "#!/bin/bash\necho 'Fallback'\n");

        LaunchResult result = launcher.launchGame(gameDir);
        assertTrue(result.success());
        assertEquals("LINUX_FALLBACK_SCRIPT", result.runnerUsed());
    }

    @Test
    void testLaunchOnWindowsNative() throws IOException {
        GameLauncher.setSimulatedPlatform(PlatformType.WINDOWS);

        Path gameDir = tempDir.resolve("game_windows");
        Files.createDirectories(gameDir);
        Path scriptPath = gameDir.resolve("game.exe");
        Files.writeString(scriptPath, "#!/bin/bash\necho 'Win process'\nexit 0\n");
        scriptPath.toFile().setExecutable(true);

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            5, "Witcher 3 Win", "witcher-win", "game.exe",
            35000, "34.18 GB", "Pen Drive", "Pen Drive", true, null, List.of("-fullscreen"), "AUTO"
        );

        LaunchResult result = launcher.launchGame(gameDir, meta);
        assertTrue(result.success());
        assertEquals("WINDOWS_NATIVE", result.runnerUsed());
        assertEquals(scriptPath.toAbsolutePath().toString(), result.command().get(0));
        assertTrue(result.command().contains("-fullscreen"));
    }

    @Test
    void testLaunchNonExistentDirectory() {
        LaunchResult result = launcher.launchGame(tempDir.resolve("does_not_exist"));
        assertFalse(result.success());
    }
}
