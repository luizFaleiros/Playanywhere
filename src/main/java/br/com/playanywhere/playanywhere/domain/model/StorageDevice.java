package br.com.playanywhere.playanywhere.domain.model;

/**
 * Modelo para representar um dispositivo de armazenamento conectado.
 */
public class StorageDevice {
    private String name;
    private StorageType type;
    private long totalSpaceMB;
    private long freeSpaceMB;
    private String mountPoint;

    public StorageDevice() {}

    public StorageDevice(String name, StorageType type, long totalSpaceMB, long freeSpaceMB, String mountPoint) {
        this.name = name;
        this.type = type;
        this.totalSpaceMB = totalSpaceMB;
        this.freeSpaceMB = freeSpaceMB;
        this.mountPoint = mountPoint;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public StorageType getType() { return type; }
    public void setType(StorageType type) { this.type = type; }

    public long getTotalSpaceMB() { return totalSpaceMB; }
    public void setTotalSpaceMB(long totalSpaceMB) { this.totalSpaceMB = totalSpaceMB; }

    public long getFreeSpaceMB() { return freeSpaceMB; }
    public void setFreeSpaceMB(long freeSpaceMB) { this.freeSpaceMB = freeSpaceMB; }

    public String getMountPoint() { return mountPoint; }
    public void setMountPoint(String mountPoint) { this.mountPoint = mountPoint; }

    @Override
    public String toString() {
        return name + " (" + type.getDisplayName() + ") - " + freeSpaceMB + "MB livres";
    }
}
