package br.com.playanywhere.playanywhere.drive;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class DriveManagerTest {
    
    @Test
    public void testGetMountableDrives() {
        DriveManager manager = new DriveManager();
        List<String> drives = manager.getMountableDrives();
        
        assertNotNull(drives, "The list of drives should not be null");
        assertFalse(drives.isEmpty(), "The list of drives should not be empty");
        
        // Just check that we got at least one drive
        System.out.println("Detected drives: " + drives);
    }
}
