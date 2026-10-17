package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.StorageDevice;
import br.com.playanywhere.playanywhere.domain.model.StorageType;
import br.com.playanywhere.playanywhere.domain.repository.DriveRepository;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Gerenciador e detector de unidades de armazenamento montáveis e dispositivos externos.
 */
public class DriveManager implements DriveRepository {
    private static final Logger LOGGER = Logger.getLogger(DriveManager.class.getName());

    /**
     * Retorna a lista de pontos de montagem disponíveis.
     * No Linux, verifica /media, /mnt e /run/media.
     */
    @Override
    public List<String> getMountableDrives() {
        Set<String> drives = new LinkedHashSet<>();
        String os = System.getProperty("os.name", "").toLowerCase();

        // No Windows, listar unidades de disco disponíveis (C:\, D:\, E:\, etc.)
        if (os.contains("win")) {
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    if (root.exists()) {
                        drives.add(root.getAbsolutePath());
                    }
                }
            }
            return new ArrayList<>(drives);
        }

        // No macOS, verificar /Volumes
        if (os.contains("mac") || os.contains("darwin")) {
            Path volumes = Paths.get("/Volumes");
            if (Files.exists(volumes) && Files.isDirectory(volumes)) {
                try (Stream<Path> stream = Files.list(volumes)) {
                    stream.filter(Files::isDirectory).map(Path::toString).forEach(drives::add);
                } catch (IOException ignored) {}
            }
        }

        String user = System.getProperty("user.name");
        Set<String> searchBases = new LinkedHashSet<>();
        if (user != null && !user.trim().isEmpty()) {
            searchBases.add("/run/media/" + user);
            searchBases.add("/media/" + user);
        }
        searchBases.add("/run/media");
        searchBases.add("/media");
        searchBases.add("/mnt");

        for (String basePath : searchBases) {
            if (basePath == null || basePath.trim().isEmpty()) continue;
            Path path = Paths.get(basePath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                LOGGER.fine(() -> "Verificando diretório de montagem: " + basePath);
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(Files::isDirectory)
                          .forEach(p -> {
                              // Evita adicionar a pasta base do usuário (ex: /media/luiz) como se fosse um drive
                              if (user != null && p.getFileName().toString().equals(user) && (basePath.equals("/media") || basePath.equals("/run/media"))) {
                                  return;
                              }
                              try {
                                  drives.add(p.toRealPath().toString());
                              } catch (Exception e) {
                                  drives.add(p.toAbsolutePath().normalize().toString());
                              }
                          });
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Erro ao ler diretório de montagem " + basePath + ": " + e.getMessage(), e);
                }
            }
        }

        // Se nenhum ponto de montagem específico foi encontrado, inclui diretórios padrão ou raízes do SO
        if (drives.isEmpty()) {
            for (String fallbackPath : new String[]{"/media", "/mnt"}) {
                Path p = Paths.get(fallbackPath);
                if (Files.exists(p)) {
                    drives.add(fallbackPath);
                }
            }
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    if (root.exists()) {
                        drives.add(root.getAbsolutePath());
                    }
                }
            }
        }

        return new ArrayList<>(drives);
    }

    /**
     * Retorna lista detalhada de dispositivos de armazenamento conectados com espaço livre e capacidade.
     */
    public List<StorageDevice> getConnectedStorageDevices() {
        List<StorageDevice> devices = new ArrayList<>();
        List<String> mountPaths = getMountableDrives();

        for (String mountPath : mountPaths) {
            try {
                File file = new File(mountPath);
                long totalBytes = file.getTotalSpace();
                long freeBytes = file.getUsableSpace();
                long totalMB = totalBytes / (1024 * 1024);
                long freeMB = freeBytes / (1024 * 1024);

                String name = file.getName();
                if (name == null || name.isEmpty() || name.equals("/") || name.equals("\\")) {
                    name = "Unidade " + mountPath;
                }

                StorageType type = StorageType.recommendForGameSize(totalMB > 0 ? totalMB : 16000);
                devices.add(new StorageDevice(name, type, totalMB, freeMB, mountPath));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao inspecionar dispositivo de armazenamento em " + mountPath + ": " + e.getMessage(), e);
            }
        }

        return devices;
    }

    /**
     * Retorna o espaço disponível em MB para o caminho informado.
     */
    public long getAvailableSpaceMB(String path) {
        if (path == null || path.trim().isEmpty()) return 0;
        try {
            File file = new File(path);
            while (file != null && !file.exists()) {
                file = file.getParentFile();
            }
            if (file != null && file.exists()) {
                return file.getUsableSpace() / (1024 * 1024);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao obter espaço em disco para " + path + ": " + e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Verifica se o caminho possui espaço suficiente para o jogo.
     */
    public boolean hasEnoughSpace(String path, long requiredMB) {
        long available = getAvailableSpaceMB(path);
        return available >= requiredMB;
    }

    /**
     * Renomeia o rótulo do volume / unidade associada ao caminho para o nome do jogo.
     */
    public VolumeLabelManager.VolumeLabelResult renameDrive(String path, String gameTitle) {
        VolumeLabelManager manager = new VolumeLabelManager();
        return manager.renameVolume(path, gameTitle);
    }
}
