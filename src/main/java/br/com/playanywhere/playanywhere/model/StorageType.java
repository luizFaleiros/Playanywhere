package br.com.playanywhere.playanywhere.model;

/**
 * Enumerador para tipos de dispositivos de armazenamento.
 */
public enum StorageType {
    CD_ROM("CD", 1024),              // ~1 GB
    DVD_5("DVD-5", 4700),            // ~4.7 GB
    DVD_9("DVD-9", 8500),            // ~8.5 GB
    USB_DRIVE_8GB("Pen Drive 8GB", 8000),
    USB_DRIVE_16GB("Pen Drive 16GB", 16000),
    USB_DRIVE_32GB("Pen Drive 32GB", 32000),
    USB_DRIVE_64GB("Pen Drive 64GB", 64000);
    
    private final String displayName;
    private final long capacityMB;
    
    StorageType(String name, long capacityMB) {
        this.displayName = name;
        this.capacityMB = capacityMB;
    }
    
    public String getDisplayName() { return displayName; }
    public long getCapacityMB() { return capacityMB; }
}