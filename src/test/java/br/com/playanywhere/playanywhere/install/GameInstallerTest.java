package br.com.playanywhere.playanywhere.install;

import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.model.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void testInstallSuccess() throws IOException {
        GameInstaller installer = new GameInstaller();
        
        Path sourceDir = tempDir.resolve("source");
        Path targetDir = tempDir.resolve("target");
        
        Files.createDirectory(sourceDir);
        Files.createFile(sourceDir.resolve("file1.txt"));
        Files.createDirectory(sourceDir.resolve("subdir"));
        Files.createFile(sourceDir.resolve("subdir/file2.txt"));

        Product product = new Product();
        product.setTitle("Test Game");
        
        GameInstallationRequest request = 
            new GameInstallationRequest(
                product, 
                InstallationType.LOCAL_DIRECTORY, 
                targetDir, 
                sourceDir, 
                true
            );

        installer.install(request);

        assertTrue(Files.exists(targetDir.resolve("file1.txt")));
        assertTrue(Files.exists(targetDir.resolve("subdir/file2.txt")));
        assertTrue(Files.exists(targetDir.resolve("playanywhere.json")));
        assertTrue(Files.exists(targetDir.resolve("autorun.inf")));
    }

    @Test
    void testInstallOnCDWithProgress() throws IOException {
        GameInstaller installer = new GameInstaller();
        Path sourceDir = tempDir.resolve("source-cd");
        Path targetDir = tempDir.resolve("target-cd");

        Files.createDirectory(sourceDir);
        Files.writeString(sourceDir.resolve("game.bin"), "CD-ROM Content");

        Product product = new Product(1, "Retro Classic", "retro-classic", 650);
        assertEquals(StorageType.CD_ROM, product.getRecommendedStorageType());

        List<Double> progressUpdates = new ArrayList<>();
        List<String> statusUpdates = new ArrayList<>();

        GameInstallationRequest request = new GameInstallationRequest(
            product,
            InstallationType.CD,
            targetDir,
            sourceDir,
            true
        );

        installer.install(request, (progress, message) -> {
            progressUpdates.add(progress);
            statusUpdates.add(message);
        });

        assertFalse(progressUpdates.isEmpty());
        assertFalse(statusUpdates.isEmpty());
        assertEquals(1.0, progressUpdates.get(progressUpdates.size() - 1));
        assertTrue(Files.exists(targetDir.resolve("DISC_LAYOUT.txt")));
        assertTrue(Files.exists(targetDir.resolve("autorun.inf")));
        assertTrue(Files.exists(targetDir.resolve("playanywhere.json")));
    }

    @Test
    void testInstallOnPenDrive() throws IOException {
        GameInstaller installer = new GameInstaller();
        Path sourceDir = tempDir.resolve("source-usb");
        Path targetDir = tempDir.resolve("target-usb");

        Files.createDirectory(sourceDir);
        Files.writeString(sourceDir.resolve("data.pak"), "Heavy Game Content");

        Product product = new Product(2, "Epic Adventure", "epic-adventure", 35000);
        assertEquals(StorageType.USB_DRIVE_64GB, product.getRecommendedStorageType());

        GameInstallationRequest request = new GameInstallationRequest(
            product,
            InstallationType.PEN_DRIVE,
            targetDir,
            sourceDir,
            true
        );

        installer.install(request);

        assertTrue(Files.exists(targetDir.resolve("playanywhere.json")));
        assertTrue(Files.exists(targetDir.resolve("autorun.inf")));
        assertTrue(Files.exists(targetDir.resolve("start_game.sh")));

        String metadataContent = Files.readString(targetDir.resolve("playanywhere.json"));
        assertTrue(metadataContent.contains("\"volumeLabel\" : \"Epic Adventure\""));

        String autorunContent = Files.readString(targetDir.resolve("autorun.inf"));
        assertTrue(autorunContent.contains("label=Epic Adventure"));
    }

    @Test
    void testStorageRecommendationBySize() {
        // Small game -> CD
        Product small = new Product(10, "Small Game", "small", 500);
        assertEquals(StorageType.CD_ROM, small.getRecommendedStorageType());
        assertEquals("500 MB", small.getFormattedSize());

        // Medium game -> DVD-5
        Product medium = new Product(20, "Medium Game", "medium", 3200);
        assertEquals(StorageType.DVD_5, medium.getRecommendedStorageType());
        assertEquals("3.1 GB", medium.getFormattedSize());

        // Large game -> DVD-9
        Product dvd9 = new Product(30, "DVD9 Game", "dvd9", 7500);
        assertEquals(StorageType.DVD_9, dvd9.getRecommendedStorageType());

        // Very large game -> Pen Drive USB
        Product large = new Product(40, "Large Game", "large", 15000);
        assertEquals(StorageType.USB_DRIVE_16GB, large.getRecommendedStorageType());

        // Huge game -> Pen Drive USB 64GB
        Product huge = new Product(50, "Huge Game", "huge", 65000);
        assertEquals(StorageType.USB_DRIVE_64GB, huge.getRecommendedStorageType());
        assertEquals("63.5 GB", huge.getFormattedSize());
    }

    @Test
    void testInstallSourceDoesNotExist() {
        GameInstaller installer = new GameInstaller();
        Path sourceDir = tempDir.resolve("nonexistent");
        Path targetDir = tempDir.resolve("target");

        Product product = new Product();
        product.setTitle("Test Game");

        GameInstallationRequest request = 
            new GameInstallationRequest(
                product, 
                InstallationType.LOCAL_DIRECTORY, 
                targetDir, 
                sourceDir, 
                true
            );

        assertThrows(IOException.class, () -> installer.install(request));
    }

    @Test
    void testInstallNullInputs() {
        GameInstaller installer = new GameInstaller();
        assertThrows(IllegalArgumentException.class, () -> installer.install(null));
    }
}
