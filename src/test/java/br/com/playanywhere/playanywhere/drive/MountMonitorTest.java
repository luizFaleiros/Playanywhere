package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.PlayAnywhereMetadata;
import br.com.playanywhere.playanywhere.launcher.GameLauncher;
import br.com.playanywhere.playanywhere.launcher.LaunchResult;
import br.com.playanywhere.playanywhere.launcher.PlatformType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MountMonitorTest {

    @TempDir
    Path tempDir;

    private MountMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = MountMonitor.getInstance();
        GameLauncher.resetSimulatedPlatform();
    }

    @AfterEach
    void tearDown() {
        monitor.stop();
        GameLauncher.resetSimulatedPlatform();
    }

    @Test
    void testDetectAndLaunchGameOnMount() throws IOException {
        Path pendriveRoot = tempDir.resolve("usb_drive_1");
        Files.createDirectories(pendriveRoot);

        // Cria playanywhere.json na raiz do pendrive
        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            55, "Dead Cells", "dead-cells", "start_game.sh",
            1500, "1.46 GB", "Pen Drive", "Pen Drive",
            true, "2026-10-13T12:00:00Z", List.of(), "NATIVE"
        );
        new ObjectMapper().writeValue(pendriveRoot.resolve("playanywhere.json").toFile(), meta);
        Files.writeString(pendriveRoot.resolve("start_game.sh"), "#!/bin/bash\necho 'Dead Cells running'\nexit 0\n");

        AtomicBoolean detected = new AtomicBoolean(false);
        AtomicBoolean launched = new AtomicBoolean(false);
        List<String> runnerHolder = new ArrayList<>();

        MountListener listener = new MountListener() {
            @Override
            public void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
                detected.set(true);
                assertEquals("Dead Cells", metadata.title());
            }

            @Override
            public void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {
                launched.set(true);
                runnerHolder.add(result.runnerUsed());
            }

            @Override
            public void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {}
        };

        monitor.addMountListener(listener);

        try {
            monitor.handleMount(pendriveRoot.toString());

            assertTrue(detected.get(), "O jogo no pendrive deve ser detectado");
            assertTrue(launched.get(), "O jogo com autoRunner ativado deve ser lançado");
            assertFalse(runnerHolder.isEmpty());
        } finally {
            monitor.removeMountListener(listener);
        }
    }

    @Test
    void testDetectGameInsideSubdirectory() throws IOException {
        Path pendriveRoot = tempDir.resolve("usb_drive_2");
        Path gameSubdir = pendriveRoot.resolve("PlayAnywhere");
        Files.createDirectories(gameSubdir);

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            99, "Stardew Valley", "stardew-valley", "start_game.sh",
            500, "500 MB", "CD", "CD",
            true, "2026-10-13T12:00:00Z", List.of(), "NATIVE"
        );
        new ObjectMapper().writeValue(gameSubdir.resolve("playanywhere.json").toFile(), meta);
        Files.writeString(gameSubdir.resolve("start_game.sh"), "#!/bin/bash\necho 'Stardew Valley'\nexit 0\n");

        AtomicBoolean detected = new AtomicBoolean(false);

        MountListener listener = new MountListener() {
            @Override
            public void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
                detected.set(true);
                assertEquals("Stardew Valley", metadata.title());
            }

            @Override
            public void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {}

            @Override
            public void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {}
        };

        monitor.addMountListener(listener);

        try {
            monitor.handleMount(pendriveRoot.toString());
            assertTrue(detected.get(), "Jogo em subpasta PlayAnywhere deve ser detectado");
        } finally {
            monitor.removeMountListener(listener);
        }
    }

    @Test
    void testHybridModelStartAndStrategy() {
        monitor.start();
        assertTrue(monitor.isEventDrivenActive() || monitor.getActiveStrategyName().contains("Híbrido") || monitor.getActiveStrategyName().contains("Polling"));
        assertNotNull(monitor.getActiveStrategyName());
        monitor.stop();
    }

    @Test
    void testHybridEventTriggerImmediateDetection() throws IOException {
        Path pendriveRoot = tempDir.resolve("usb_drive_hybrid");
        Files.createDirectories(pendriveRoot);

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            77, "Hollow Knight", "hollow-knight", "start_game.sh",
            1200, "1.2 GB", "Pen Drive", "Pen Drive",
            true, "2026-10-13T12:00:00Z", List.of(), "NATIVE"
        );
        new ObjectMapper().writeValue(pendriveRoot.resolve("playanywhere.json").toFile(), meta);
        Files.writeString(pendriveRoot.resolve("start_game.sh"), "#!/bin/bash\necho 'Hollow Knight'\nexit 0\n");

        AtomicBoolean detected = new AtomicBoolean(false);

        MountListener listener = new MountListener() {
            @Override
            public void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
                detected.set(true);
                assertEquals("Hollow Knight", metadata.title());
            }

            @Override
            public void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {}

            @Override
            public void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {}
        };

        monitor.addMountListener(listener);
        monitor.start();

        try {
            // Simula disparo instantâneo por sinal/evento nativo
            monitor.handleMount(pendriveRoot.toString());
            assertTrue(detected.get(), "Disparo de montagem por evento nativo deve detectar o jogo imediatamente");
        } finally {
            monitor.removeMountListener(listener);
            monitor.stop();
        }
    }

    @Test
    void testDeepNestedCaseInsensitiveMetadataDetection() throws IOException {
        Path pendriveRoot = tempDir.resolve("usb_drive_deep");
        Path nestedGameDir = pendriveRoot.resolve("Games").resolve("CustomGame");
        Files.createDirectories(nestedGameDir);

        PlayAnywhereMetadata meta = new PlayAnywhereMetadata(
            88, "Celeste", "celeste", "start_game.sh",
            1000, "1.0 GB", "Pen Drive", "Pen Drive",
            true, "2026-10-13T12:00:00Z", List.of(), "NATIVE"
        );
        // Arquivo em letras maiúsculas
        new ObjectMapper().writeValue(nestedGameDir.resolve("PLAYANYWHERE.JSON").toFile(), meta);
        Files.writeString(nestedGameDir.resolve("start_game.sh"), "#!/bin/bash\necho 'Celeste'\nexit 0\n");

        AtomicBoolean detected = new AtomicBoolean(false);
        MountListener listener = new MountListener() {
            @Override
            public void onGameDetected(Path drivePath, Path gameDir, PlayAnywhereMetadata metadata) {
                detected.set(true);
                assertEquals("Celeste", metadata.title());
            }

            @Override
            public void onGameLaunched(PlayAnywhereMetadata metadata, LaunchResult result) {}

            @Override
            public void onGameLaunchFailed(PlayAnywhereMetadata metadata, String error) {}
        };

        monitor.addMountListener(listener);
        try {
            monitor.handleMount(pendriveRoot.toString());
            assertTrue(detected.get(), "Deve detectar jogos em subpastas aninhadas com metadata case-insensitive");
        } finally {
            monitor.removeMountListener(listener);
        }
    }
}
