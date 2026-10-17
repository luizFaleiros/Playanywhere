package br.com.playanywhere.playanywhere.domain.model;

/**
 * Enumerador para tipos de dispositivos e mídias de armazenamento.
 */
public enum StorageType {
    CD_ROM("CD-ROM (até 1 GB)", 1024, "CD"),              // ~700 MB - 1 GB
    DVD_5("DVD-5 (até 4.7 GB)", 4700, "DVD"),            // ~4.7 GB
    DVD_9("DVD-9 (até 8.5 GB)", 8500, "DVD Dual Layer"), // ~8.5 GB
    USB_DRIVE_8GB("Pen Drive USB 8GB", 8000, "Pen Drive"),
    USB_DRIVE_16GB("Pen Drive USB 16GB", 16000, "Pen Drive"),
    USB_DRIVE_32GB("Pen Drive USB 32GB", 32000, "Pen Drive"),
    USB_DRIVE_64GB("Pen Drive USB 64GB+", 64000, "Pen Drive");

    private final String displayName;
    private final long capacityMB;
    private final String category;

    StorageType(String name, long capacityMB, String category) {
        this.displayName = name;
        this.capacityMB = capacityMB;
        this.category = category;
    }

    public String getDisplayName() { return displayName; }
    public long getCapacityMB() { return capacityMB; }
    public String getCategory() { return category; }

    /**
     * Recomenda o tipo de mídia ideal com base no tamanho do jogo em MB:
     * - Jogos até 1024 MB (1 GB) -> CD-ROM
     * - Jogos de 1 GB até 4.7 GB -> DVD-5
     * - Jogos de 4.7 GB até 8.5 GB -> DVD-9
     * - Jogos acima de 8.5 GB até 16 GB -> Pen Drive USB 16GB
     * - Jogos acima de 16 GB até 32 GB -> Pen Drive USB 32GB
     * - Jogos acima de 32 GB -> Pen Drive USB 64GB+
     */
    public static StorageType recommendForGameSize(long sizeMB) {
        if (sizeMB <= 1024) {
            return CD_ROM;
        } else if (sizeMB <= 4700) {
            return DVD_5;
        } else if (sizeMB <= 8500) {
            return DVD_9;
        } else if (sizeMB <= 8000) {
            return USB_DRIVE_8GB;
        } else if (sizeMB <= 16000) {
            return USB_DRIVE_16GB;
        } else if (sizeMB <= 32000) {
            return USB_DRIVE_32GB;
        } else {
            return USB_DRIVE_64GB;
        }
    }

    /**
     * Converte de InstallationType para o StorageType representativo.
     */
    public static StorageType fromInstallationType(InstallationType installationType) {
        if (installationType == null) return USB_DRIVE_16GB;
        return switch (installationType) {
            case CD -> CD_ROM;
            case DVD -> DVD_5;
            case PEN_DRIVE -> USB_DRIVE_16GB;
            case EXTERNAL_HD, LOCAL_DIRECTORY -> USB_DRIVE_64GB;
        };
    }
}
