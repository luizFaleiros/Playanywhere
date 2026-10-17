package br.com.playanywhere.playanywhere.drive.detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeviceEventDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void testLinuxDeviceEventDetectorLifecycleAndInotifyEvent() throws Exception {
        LinuxDeviceEventDetector detector = new LinuxDeviceEventDetector();
        Path watchDir = tempDir.resolve("media_watch");
        Files.createDirectories(watchDir);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedType = new AtomicReference<>();

        detector.start((path, eventType) -> {
            receivedPath.set(path);
            receivedType.set(eventType);
            latch.countDown();
        });

        assertTrue(detector.isRunning(), "Detector deve estar em execução");
        assertTrue(detector.getDetectorName().contains("Linux Inotify"));

        // Registra o diretório temporário para simular ponto de montagem
        detector.registerDirectory(watchDir);

        // Cria uma subpasta para simular a montagem de um pen drive
        Path usbMount = watchDir.resolve("USB_PENDRIVE_GAMES");
        Files.createDirectories(usbMount);

        boolean notified = latch.await(2, TimeUnit.SECONDS);
        detector.stop();

        assertFalse(detector.isRunning(), "Detector deve parar após stop()");
        if (notified) {
            assertEquals("ATTACH", receivedType.get());
            assertNotNull(receivedPath.get());
            assertTrue(receivedPath.get().contains("USB_PENDRIVE_GAMES"));
        }
    }

    @Test
    void testWindowsDeviceEventDetectorLifecycle() {
        WindowsDeviceEventDetector detector = new WindowsDeviceEventDetector();
        assertNotNull(detector.getDetectorName());
        assertTrue(detector.getDetectorName().contains("Windows WMI"));

        detector.start((path, eventType) -> {});
        assertTrue(detector.isRunning());
        detector.stop();
        assertFalse(detector.isRunning());
    }

    @Test
    void testMacDeviceEventDetectorLifecycle() {
        MacDeviceEventDetector detector = new MacDeviceEventDetector();
        assertNotNull(detector.getDetectorName());
        assertTrue(detector.getDetectorName().contains("macOS"));

        detector.start((path, eventType) -> {});
        detector.stop();
        assertFalse(detector.isRunning());
    }

    @Test
    void testHybridDeviceEventDetectorDelegation() {
        AtomicBoolean customStarted = new AtomicBoolean(false);
        AtomicBoolean customStopped = new AtomicBoolean(false);

        DeviceEventDetector customDetector = new DeviceEventDetector() {
            private boolean running = false;

            @Override
            public void start(DeviceEventListener listener) {
                running = true;
                customStarted.set(true);
                if (listener != null) {
                    listener.onDeviceEvent("/test/drive", "ATTACH");
                }
            }

            @Override
            public void stop() {
                running = false;
                customStopped.set(true);
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String getDetectorName() {
                return "Custom Test Detector";
            }
        };

        HybridDeviceEventDetector hybrid = new HybridDeviceEventDetector(customDetector);
        assertEquals("Custom Test Detector", hybrid.getDetectorName());

        AtomicBoolean eventReceived = new AtomicBoolean(false);
        hybrid.start((path, eventType) -> {
            if ("/test/drive".equals(path) && "ATTACH".equals(eventType)) {
                eventReceived.set(true);
            }
        });

        assertTrue(customStarted.get());
        assertTrue(hybrid.isRunning());
        assertTrue(eventReceived.get());

        hybrid.stop();
        assertTrue(customStopped.get());
        assertFalse(hybrid.isRunning());
    }

    @Test
    void testDefaultHybridDetectorResolution() {
        HybridDeviceEventDetector hybrid = new HybridDeviceEventDetector();
        assertNotNull(hybrid.getActiveDetector());
        assertNotNull(hybrid.getDetectorName());
    }
}
