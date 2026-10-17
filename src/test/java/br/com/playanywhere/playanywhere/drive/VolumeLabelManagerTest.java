package br.com.playanywhere.playanywhere.drive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VolumeLabelManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void testSanitizeLabelForFat32() {
        VolumeLabelManager manager = new VolumeLabelManager();

        // 11 characters limit for FAT/FAT32
        String sanitized = manager.sanitizeLabel("Cyberpunk 2077: Phantom Liberty", 11);
        assertEquals("Cyberpunk 2", sanitized);
        assertTrue(sanitized.length() <= 11);

        // Special characters removed
        String sanitizedSpecial = manager.sanitizeLabel("Game: The Witcher / 3 * <Wild> ? Hunt |", 11);
        assertFalse(sanitizedSpecial.contains(":"));
        assertFalse(sanitizedSpecial.contains("/"));
        assertFalse(sanitizedSpecial.contains("*"));
        assertFalse(sanitizedSpecial.contains("<"));
        assertFalse(sanitizedSpecial.contains(">"));
        assertFalse(sanitizedSpecial.contains("?"));
        assertFalse(sanitizedSpecial.contains("|"));
    }

    @Test
    void testSanitizeLabelForNtfsExfat() {
        VolumeLabelManager manager = new VolumeLabelManager();

        String raw = "The Witcher 3: Wild Hunt - Complete Edition";
        String sanitized = manager.sanitizeLabel(raw, 32);

        assertEquals("The Witcher 3 Wild Hunt - Comple", sanitized);
        assertEquals(32, sanitized.length());
    }

    @Test
    void testSanitizeEmptyOrNullLabel() {
        VolumeLabelManager manager = new VolumeLabelManager();

        assertEquals("GAME", manager.sanitizeLabel(null, 11));
        assertEquals("GAME", manager.sanitizeLabel("", 11));
        assertEquals("GAME", manager.sanitizeLabel("   ", 11));
        assertEquals("GAME", manager.sanitizeLabel(" :/*?\"<>| ", 11));
        assertEquals("PLAYANYWHERE", manager.sanitizeLabel(" :/*?\"<>| ", 32));
    }

    @Test
    void testRenameVolumeWithTempDir() {
        VolumeLabelManager manager = new VolumeLabelManager();

        VolumeLabelManager.VolumeLabelResult result = manager.renameVolume(tempDir, "Hollow Knight");
        assertNotNull(result);
        assertEquals("Hollow Knight", result.label());
        assertNotNull(result.message());
    }

    @Test
    void testRenameVolumeNullInputs() {
        VolumeLabelManager manager = new VolumeLabelManager();

        VolumeLabelManager.VolumeLabelResult res1 = manager.renameVolume((Path) null, "Game");
        assertFalse(res1.success());

        VolumeLabelManager.VolumeLabelResult res2 = manager.renameVolume(tempDir, null);
        assertFalse(res2.success());

        VolumeLabelManager.VolumeLabelResult res3 = manager.renameVolume((String) null, "Game");
        assertFalse(res3.success());
    }

    @Test
    void testDriveManagerRenameDrive() {
        DriveManager driveManager = new DriveManager();
        VolumeLabelManager.VolumeLabelResult result = driveManager.renameDrive(tempDir.toString(), "Dead Cells");
        assertNotNull(result);
        assertEquals("Dead Cells", result.label());
    }
}
