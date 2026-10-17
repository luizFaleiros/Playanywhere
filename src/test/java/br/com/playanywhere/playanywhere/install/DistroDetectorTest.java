package br.com.playanywhere.playanywhere.install;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistroDetectorTest {

    @Test
    @DisplayName("Deve detectar a família do sistema operacional corrente")
    void shouldDetectOsFamily() {
        DistroDetector.OsFamily osFamily = DistroDetector.detectOsFamily();
        assertNotNull(osFamily);
        assertNotEquals(DistroDetector.OsFamily.UNKNOWN, osFamily);

        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            assertEquals(DistroDetector.OsFamily.LINUX, osFamily);
            assertTrue(DistroDetector.isLinux());
            assertFalse(DistroDetector.isWindows());
            assertFalse(DistroDetector.isMac());
        } else if (osName.contains("win")) {
            assertEquals(DistroDetector.OsFamily.WINDOWS, osFamily);
            assertTrue(DistroDetector.isWindows());
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            assertEquals(DistroDetector.OsFamily.MACOS, osFamily);
            assertTrue(DistroDetector.isMac());
        }
    }

    @Test
    @DisplayName("Deve detectar informações e comandos de instalação da distribuição Linux")
    void shouldDetectLinuxDistroInfo() {
        if (!DistroDetector.isLinux()) {
            return;
        }

        DistroDetector.DistroInfo info = DistroDetector.detectLinuxDistro();
        assertNotNull(info);
        assertNotNull(info.id());
        assertNotNull(info.name());
        assertNotNull(info.packageManager());
        assertNotNull(info.installCommand());
        assertTrue(info.installCommand().contains("innoextract") || info.installCommand().contains("7z"));
    }
}
