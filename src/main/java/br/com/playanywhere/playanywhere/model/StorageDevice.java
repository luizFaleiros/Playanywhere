package br.com.playanywhere.playanywhere.model;

import br.com.playanywhere.playanywhere.domain.model.StorageType;

public class StorageDevice extends br.com.playanywhere.playanywhere.domain.model.StorageDevice {
    public StorageDevice() { super(); }
    public StorageDevice(String name, StorageType type, long totalSpaceMB, long freeSpaceMB, String mountPoint) {
        super(name, type, totalSpaceMB, freeSpaceMB, mountPoint);
    }
}
