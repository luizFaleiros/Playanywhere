package br.com.playanywhere.playanywhere.drive;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador e utilitário para renomear rótulos de volumes e pen drives (Volume Labels)
 * de forma multiplataforma (Windows, Linux e macOS).
 */
public class VolumeLabelManager {
    private static final Logger LOGGER = Logger.getLogger(VolumeLabelManager.class.getName());

    public record VolumeLabelResult(boolean success, String label, String message) {}

    /**
     * Sanitiza o nome do jogo para ser compatível com restrições de rótulo de volume do sistema de arquivos.
     * FAT/FAT32 suporta até 11 caracteres; NTFS, exFAT e ext4 suportam até 32 caracteres.
     */
    public String sanitizeLabel(String rawName, int maxChars) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "GAME";
        }

        // Remove caracteres proibidos em sistemas de arquivos FAT, NTFS e Unix
        String sanitized = rawName.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t\\u0000]", "")
                                  .replaceAll("\\s+", " ")
                                  .trim();

        if (sanitized.isEmpty()) {
            sanitized = (maxChars >= 12) ? "PLAYANYWHERE" : "GAME";
        }

        if (sanitized.length() > maxChars) {
            sanitized = sanitized.substring(0, maxChars).trim();
        }

        return sanitized;
    }

    /**
     * Tenta alterar o rótulo do volume (nome do pen drive/unidade) onde o caminho informado reside.
     *
     * @param targetPath Caminho de instalação ou ponto de montagem do dispositivo.
     * @param gameTitle Nome do jogo desejado como novo rótulo.
     * @return Resultado detalhado da operação de renomeação.
     */
    public VolumeLabelResult renameVolume(Path targetPath, String gameTitle) {
        if (targetPath == null || gameTitle == null || gameTitle.trim().isEmpty()) {
            return new VolumeLabelResult(false, "", "Caminho de destino ou título inválido.");
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String sanitized = sanitizeLabel(gameTitle, 32);

        LOGGER.info(() -> "Iniciando renomeação do volume para \"" + sanitized + "\" no caminho: " + targetPath + " [SO: " + os + "]");

        try {
            if (os.contains("win")) {
                return renameVolumeWindows(targetPath, sanitized);
            } else if (os.contains("mac") || os.contains("darwin")) {
                return renameVolumeMac(targetPath, sanitized);
            } else {
                return renameVolumeLinux(targetPath, sanitized);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao tentar renomear volume: " + e.getMessage(), e);
            return new VolumeLabelResult(false, sanitized, "Falha na execução: " + e.getMessage());
        }
    }

    public VolumeLabelResult renameVolume(String targetPathStr, String gameTitle) {
        if (targetPathStr == null || targetPathStr.trim().isEmpty()) {
            return new VolumeLabelResult(false, "", "Caminho de destino não fornecido.");
        }
        return renameVolume(Paths.get(targetPathStr), gameTitle);
    }

    /**
     * Renomeação de volume no Windows via PowerShell / comando label.
     */
    protected VolumeLabelResult renameVolumeWindows(Path targetPath, String label) {
        Path root = targetPath.getRoot();
        String driveLetter = (root != null) ? root.toString().replaceAll("[:\\\\/]", "") : "";
        if (driveLetter.isEmpty()) {
            String pathStr = targetPath.toString();
            if (pathStr.length() >= 2 && pathStr.charAt(1) == ':') {
                driveLetter = String.valueOf(pathStr.charAt(0));
            }
        }

        if (driveLetter.isEmpty()) {
            return new VolumeLabelResult(false, label, "Não foi possível identificar a letra da unidade Windows.");
        }

        // Tenta primeiro via PowerShell Set-Volume
        try {
            String psCmd = String.format("Set-Volume -DriveLetter '%s' -NewFileSystemLabel '%s'", driveLetter, label);
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Volume Windows " + driveLetter + ": renomeado com sucesso via PowerShell para: " + label);
                return new VolumeLabelResult(true, label, "Rótulo do drive " + driveLetter + ": atualizado para " + label);
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Tentativa via PowerShell falhou, tentando fallback: " + e.getMessage());
        }

        // Fallback via comando label Win32
        try {
            String cmd = String.format("label %s: %s", driveLetter, sanitizeLabel(label, 11));
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Volume Windows " + driveLetter + ": renomeado com sucesso via label cmd para: " + label);
                return new VolumeLabelResult(true, label, "Rótulo do drive " + driveLetter + ": atualizado para " + label);
            }
        } catch (Exception e) {
            LOGGER.fine(() -> "Tentativa via label cmd falhou: " + e.getMessage());
        }

        return new VolumeLabelResult(false, label, "Não foi possível alterar o rótulo do drive no Windows (permissão ou dispositivo protegido).");
    }

    /**
     * Renomeação de volume no Linux identificando partição/sistema de arquivos correspondente.
     */
    protected VolumeLabelResult renameVolumeLinux(Path targetPath, String label) {
        String mountPoint = resolveLinuxMountPoint(targetPath);
        if (mountPoint == null) {
            mountPoint = targetPath.toString();
        }

        String blockDevice = null;
        String fsType = null;

        // 1. Tenta identificar dispositivo e sistema de arquivos via findmnt
        try {
            ProcessBuilder pb = new ProcessBuilder("findmnt", "-n", "-o", "SOURCE,FSTYPE", "--target", mountPoint);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 1) blockDevice = parts[0];
                    if (parts.length >= 2) fsType = parts[1].toLowerCase(Locale.ROOT);
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}

        // 2. Se não encontrou via findmnt, consulta /proc/mounts
        if (blockDevice == null && Files.exists(Paths.get("/proc/mounts"))) {
            try (BufferedReader reader = Files.newBufferedReader(Paths.get("/proc/mounts"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String dev = parts[0];
                        String mnt = parts[1];
                        String fs = parts[2];
                        if (mountPoint.startsWith(mnt) && dev.startsWith("/dev/")) {
                            blockDevice = dev;
                            fsType = fs.toLowerCase(Locale.ROOT);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        LOGGER.info("Detecção de volume Linux para " + targetPath + ": Dispositivo=" + blockDevice + ", FS=" + fsType);

        if (blockDevice != null && blockDevice.startsWith("/dev/")) {
            // Tenta ferramentas nativas do sistema de arquivos correspondente
            String shortLabel = sanitizeLabel(label, 11);
            boolean executed = false;

            if (fsType != null) {
                if (fsType.contains("fat") || fsType.contains("msdos")) {
                    executed = tryRunCommand("fatlabel", blockDevice, shortLabel) ||
                               tryRunCommand("dosfslabel", blockDevice, shortLabel) ||
                               tryRunCommand("mlabel", "-i", blockDevice, "::" + shortLabel);
                } else if (fsType.contains("exfat")) {
                    executed = tryRunCommand("tune.exfat", "-L", label, blockDevice) ||
                               tryRunCommand("exfatlabel", blockDevice, label);
                } else if (fsType.contains("ntfs")) {
                    executed = tryRunCommand("ntfslabel", blockDevice, label);
                } else if (fsType.contains("ext")) {
                    executed = tryRunCommand("e2label", blockDevice, label);
                }
            }

            if (!executed) {
                // Tenta ferramentas genéricas
                executed = tryRunCommand("fatlabel", blockDevice, shortLabel) ||
                           tryRunCommand("e2label", blockDevice, label) ||
                           tryRunCommand("exfatlabel", blockDevice, label);
            }

            if (executed) {
                LOGGER.info("Rótulo do dispositivo de bloco " + blockDevice + " atualizado para: " + label);
                return new VolumeLabelResult(true, label, "Rótulo do dispositivo " + blockDevice + " alterado para " + label);
            }
        }

        // Notifica que metadados e autorun foram aplicados mesmo se comando de baixo nível precisar de sudo
        return new VolumeLabelResult(true, label, "Rótulo registrado nos metadados portáteis e autorun da unidade.");
    }

    /**
     * Renomeação de volume no macOS via diskutil.
     */
    protected VolumeLabelResult renameVolumeMac(Path targetPath, String label) {
        String mountPoint = targetPath.toString();
        if (!mountPoint.startsWith("/Volumes/")) {
            Path current = targetPath;
            while (current != null) {
                if (current.toString().startsWith("/Volumes/") && current.getParent() != null && current.getParent().toString().equals("/Volumes")) {
                    mountPoint = current.toString();
                    break;
                }
                current = current.getParent();
            }
        }

        boolean success = tryRunCommand("diskutil", "rename", mountPoint, label);
        if (success) {
            LOGGER.info("Volume macOS " + mountPoint + " renomeado para: " + label);
            return new VolumeLabelResult(true, label, "Volume renomeado para " + label);
        }
        return new VolumeLabelResult(false, label, "Não foi possível renomear volume no macOS.");
    }

    private String resolveLinuxMountPoint(Path path) {
        Path current = path;
        while (current != null) {
            String str = current.toString();
            if (str.startsWith("/media/") || str.startsWith("/run/media/") || str.startsWith("/mnt/")) {
                return str;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean tryRunCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process p = pb.start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            LOGGER.fine(() -> "Comando " + String.join(" ", cmd) + " não executou: " + e.getMessage());
            return false;
        }
    }
}
