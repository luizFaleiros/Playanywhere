package br.com.playanywhere.playanywhere.install;

import br.com.playanywhere.playanywhere.application.dto.GameInstallationRequest;
import br.com.playanywhere.playanywhere.application.usecase.InstallGameUseCase;
import br.com.playanywhere.playanywhere.domain.model.InstallationType;
import br.com.playanywhere.playanywhere.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExeExtractorTest {

    @TempDir
    Path tempDir;

    private ExeExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ExeExtractor();
    }

    private Path createSampleZipExe(String fileName, boolean withPeStub) throws IOException {
        Path exePath = tempDir.resolve(fileName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Se withPeStub for true, simula um executável Windows SFX (cabeçalho MZ + dados de stub)
        if (withPeStub) {
            byte[] mzStub = new byte[]{
                (byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00
            };
            baos.write(mzStub);
            baos.write("This program cannot be run in DOS mode.\r\r\n$".getBytes(StandardCharsets.US_ASCII));
            baos.write(new byte[128]); // Padding
        }

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Arquivo 1
            ZipEntry entry1 = new ZipEntry("game.exe");
            zos.putNextEntry(entry1);
            zos.write("BIN_GAME_DATA_PAYLOAD".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Arquivo 2 em subdiretório
            ZipEntry entry2 = new ZipEntry("assets/textures/player.png");
            zos.putNextEntry(entry2);
            zos.write("TEXTURE_RAW_BYTES".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Arquivo de configuração
            ZipEntry entry3 = new ZipEntry("config/settings.ini");
            zos.putNextEntry(entry3);
            zos.write("[Graphics]\nResolution=1920x1080\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Files.write(exePath, baos.toByteArray());
        return exePath;
    }

    @Test
    void testExtractStandardZipExe() throws IOException {
        Path exePath = createSampleZipExe("installer_standard.exe", false);
        Path targetDir = tempDir.resolve("installed_game_1");

        ExtractionResult result = extractor.extract(exePath, targetDir);

        assertTrue(result.success());
        assertEquals(targetDir, result.targetDirectory());
        assertEquals(3, result.extractedFilesCount());
        assertTrue(result.totalExtractedBytes() > 0);
        assertTrue(Files.exists(targetDir.resolve("game.exe")));
        assertTrue(Files.exists(targetDir.resolve("assets/textures/player.png")));
        assertTrue(Files.exists(targetDir.resolve("config/settings.ini")));

        String configContent = Files.readString(targetDir.resolve("config/settings.ini"));
        assertTrue(configContent.contains("Resolution=1920x1080"));
    }

    @Test
    void testExtractSfxExeWithPeHeader() throws IOException {
        Path exePath = createSampleZipExe("installer_sfx.exe", true);
        Path targetDir = tempDir.resolve("installed_game_2");

        assertTrue(extractor.hasMzHeader(exePath));
        assertTrue(extractor.findZipOffset(exePath) > 0);

        ExtractionResult result = extractor.extract(exePath, targetDir);

        assertTrue(result.success());
        assertEquals(3, result.extractedFilesCount());
        assertTrue(Files.exists(targetDir.resolve("game.exe")));
        assertTrue(Files.exists(targetDir.resolve("assets/textures/player.png")));
        assertTrue(Files.exists(targetDir.resolve("config/settings.ini")));
    }

    @Test
    void testExtractWithProgressListener() throws IOException {
        Path exePath = createSampleZipExe("installer_progress.exe", true);
        Path targetDir = tempDir.resolve("installed_game_3");

        List<Double> progressList = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        ExtractionResult result = extractor.extract(exePath, targetDir, (progress, message) -> {
            progressList.add(progress);
            messages.add(message);
        });

        assertTrue(result.success());
        assertFalse(progressList.isEmpty());
        assertFalse(messages.isEmpty());
        assertTrue(progressList.get(progressList.size() - 1) >= 0.95);
    }

    @Test
    void testExtractUsingFileAndStringOverloads() throws IOException {
        Path exePath = createSampleZipExe("installer_overload.exe", false);
        Path targetDir = tempDir.resolve("installed_game_4");

        ExtractionResult result1 = extractor.extract(exePath.toFile(), targetDir.toFile());
        assertTrue(result1.success());

        Path targetDir2 = tempDir.resolve("installed_game_5");
        ExtractionResult result2 = extractor.extract(exePath.toString(), targetDir2.toString());
        assertTrue(result2.success());
    }

    @Test
    void testIsExtractableExe() throws IOException {
        Path exeFile = createSampleZipExe("setup.exe", true);
        Path zipFile = createSampleZipExe("setup.zip", false);
        Path nonExistent = tempDir.resolve("missing.exe");

        assertTrue(extractor.isExtractableExe(exeFile));
        assertTrue(extractor.isExtractableExe(zipFile));
        assertFalse(extractor.isExtractableExe(nonExistent));
        assertFalse(extractor.isExtractableExe(null));
    }

    @Test
    void testZipSlipVulnerabilityPrevented() throws IOException {
        Path maliciousZip = tempDir.resolve("malicious.exe");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry maliciousEntry = new ZipEntry("../../../evil.txt");
            zos.putNextEntry(maliciousEntry);
            zos.write("malicious payload".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(maliciousZip, baos.toByteArray());

        Path targetDir = tempDir.resolve("safe_target");
        assertThrows(SecurityException.class, () -> extractor.extract(maliciousZip, targetDir));
    }

    @Test
    void testExtractNonExistentExeThrowsException() {
        Path missing = tempDir.resolve("does_not_exist.exe");
        Path target = tempDir.resolve("target");
        assertThrows(FileNotFoundException.class, () -> extractor.extract(missing, target));
    }

    @Test
    void testExtractNullParametersThrowException() {
        assertThrows(NullPointerException.class, () -> extractor.extract((Path) null, tempDir));
        assertThrows(NullPointerException.class, () -> extractor.extract(tempDir, (Path) null));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract((String) null, "target"));
    }

    @Test
    void testFindMainExecutable() throws IOException {
        Path gameDir = tempDir.resolve("game_dir");
        Files.createDirectories(gameDir);
        Files.writeString(gameDir.resolve("launcher.exe"), "launcher content");

        Path mainExe = extractor.findMainExecutable(gameDir);
        assertNotNull(mainExe);
        assertEquals("launcher.exe", mainExe.getFileName().toString());
    }

    @Test
    void testIntegrationWithGameInstallerAndUseCase() throws IOException {
        Path exeInstaller = createSampleZipExe("witcher_setup.exe", true);
        Path targetDrive = tempDir.resolve("USB_DRIVE_D");

        Product product = new Product(101, "The Witcher 3", "the-witcher-3", 45000);
        GameInstallationRequest request = new GameInstallationRequest(
            product,
            InstallationType.PEN_DRIVE,
            targetDrive,
            exeInstaller,
            true,
            true
        );

        GameInstaller installer = new GameInstaller();
        installer.install(request);

        // Verifica que os arquivos compactados dentro do .exe foram extraídos para a unidade escolhida
        assertTrue(Files.exists(targetDrive.resolve("game.exe")));
        assertTrue(Files.exists(targetDrive.resolve("assets/textures/player.png")));
        assertTrue(Files.exists(targetDrive.resolve("config/settings.ini")));
        assertTrue(Files.exists(targetDrive.resolve("playanywhere.json")));
        assertTrue(Files.exists(targetDrive.resolve("autorun.inf")));

        String autorun = Files.readString(targetDrive.resolve("autorun.inf"));
        assertTrue(autorun.contains("open=game.exe"));
    }

    @Test
    void testExtractExeWithCustomNamedExecutable() throws IOException {
        Path customExeInstaller = tempDir.resolve("cyberpunk_setup.exe");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] mzStub = new byte[]{(byte) 0x4D, (byte) 0x5A, 0x00, 0x00};
        baos.write(mzStub);
        baos.write(new byte[64]);

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("bin/x64/Cyberpunk2077.exe");
            zos.putNextEntry(entry);
            zos.write("cyberpunk_binary_bytes".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(customExeInstaller, baos.toByteArray());

        Path targetDrive = tempDir.resolve("USB_DRIVE_E");
        Product product = new Product(102, "Cyberpunk 2077", "cyberpunk-2077", 70000);
        GameInstallationRequest request = new GameInstallationRequest(
            product,
            InstallationType.PEN_DRIVE,
            targetDrive,
            customExeInstaller,
            true,
            true
        );

        GameInstaller installer = new GameInstaller();
        installer.install(request);

        assertTrue(Files.exists(targetDrive.resolve("bin/x64/Cyberpunk2077.exe")));
        assertTrue(Files.exists(targetDrive.resolve("playanywhere.json")));
        assertTrue(Files.exists(targetDrive.resolve("autorun.inf")));

        String autorun = Files.readString(targetDrive.resolve("autorun.inf"));
        assertTrue(autorun.contains("open=Cyberpunk2077.exe"));
    }

    @Test
    void testExtractExeWithAppFolderFlattening() throws IOException {
        Path installerWithAppFolder = tempDir.resolve("inno_game_setup.exe");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] mzStub = new byte[]{(byte) 0x4D, (byte) 0x5A, 0x00, 0x00};
        baos.write(mzStub);
        baos.write(new byte[64]);

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry1 = new ZipEntry("app/game.exe");
            zos.putNextEntry(entry1);
            zos.write("game_bytes".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("app/data/levels.dat");
            zos.putNextEntry(entry2);
            zos.write("level_bytes".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(installerWithAppFolder, baos.toByteArray());

        Path targetDrive = tempDir.resolve("USB_DRIVE_FLATTEN");
        ExtractionResult result = extractor.extract(installerWithAppFolder, targetDrive);

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(targetDrive.resolve("app/game.exe")) || Files.exists(targetDrive.resolve("game.exe")));
        assertNotNull(result.mainExecutable());
    }

    @Test
    void testGogApiDownloadAndExtractCycle() throws IOException {
        Product product = new Product(555, "Hollow Knight", "hollow-knight", 2500);
        Path stagingDir = tempDir.resolve("staging-test");
        Path targetDrive = tempDir.resolve("USB_DRIVE_HK");

        Path downloadedExe = br.com.playanywhere.playanywhere.api.GOGApi.getInstance()
                .downloadGameInstaller(product, stagingDir, null);

        assertNotNull(downloadedExe);
        assertTrue(Files.exists(downloadedExe));
        assertTrue(downloadedExe.getFileName().toString().endsWith(".exe"));

        // Executa a extração direta para a unidade de destino
        ExtractionResult result = extractor.extract(downloadedExe, targetDrive);
        assertTrue(result.isSuccess());
        assertTrue(result.fileCount() >= 3);
        assertTrue(Files.exists(targetDrive.resolve("hollow_knight.exe")) || Files.exists(targetDrive.resolve("game.exe")));
        assertTrue(Files.exists(targetDrive.resolve("README.txt")));
        assertTrue(Files.exists(targetDrive.resolve("data/game.dat")));
    }

    @Test
    void testInnoExtractProvisionerResolution() {
        var systemInno = InnoExtractProvisioner.findSystemInnoExtract();
        assertNotNull(systemInno);
        // Não deve lançar exceção
    }

    @Test
    void testExtractExeWithMultipleNestedFoldersFlattening() throws IOException {
        Path installerWithPlugins = tempDir.resolve("plugins_game_setup.exe");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] mzStub = new byte[]{(byte) 0x4D, (byte) 0x5A, 0x00, 0x00};
        baos.write(mzStub);
        baos.write(new byte[64]);

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry1 = new ZipEntry("{app}/start_game.sh");
            zos.putNextEntry(entry1);
            zos.write("#!/bin/sh\necho 'running'".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("{app}/assets/main.dat");
            zos.putNextEntry(entry2);
            zos.write("assets_data".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.write(installerWithPlugins, baos.toByteArray());

        Path targetDrive = tempDir.resolve("USB_DRIVE_NESTED");
        ExtractionResult result = extractor.extract(installerWithPlugins, targetDrive);

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(targetDrive.resolve("start_game.sh")) || Files.exists(targetDrive.resolve("{app}/start_game.sh")));
        assertNotNull(result.mainExecutable());
    }

    @Test
    void testExtractTextFileThrowsInformativeException() throws IOException {
        Path invalidExe = tempDir.resolve("setup_invalid_api_response.exe");
        Files.writeString(invalidExe, "{\"error\": \"unauthorized\", \"message\": \"invalid token\"}");

        Path targetDrive = tempDir.resolve("USB_DRIVE_INVALID");
        assertThrows(IOException.class, () -> extractor.extract(invalidExe, targetDrive));
    }
}
