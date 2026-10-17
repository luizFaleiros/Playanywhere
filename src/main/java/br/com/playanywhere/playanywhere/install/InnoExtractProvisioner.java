package br.com.playanywhere.playanywhere.install;

import br.com.playanywhere.playanywhere.application.usecase.InstallationProgressListener;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Gerenciador e provisionador autônomo da ferramenta innoextract.
 * Responsável por localizar no sistema operacional ou baixar e configurar automaticamente
 * o binário estático e portátil do innoextract para Linux, Windows e macOS.
 */
public class InnoExtractProvisioner {
    private static final Logger LOGGER = Logger.getLogger(InnoExtractProvisioner.class.getName());

    private static final String INNOEXTRACT_LINUX_URL = "https://constexpr.org/innoextract/files/innoextract-1.9/innoextract-1.9-linux.tar.xz";
    private static final String INNOEXTRACT_WINDOWS_URL = "https://constexpr.org/innoextract/files/innoextract-1.9/innoextract-1.9-windows.zip";
    private static final String INNOEXTRACT_MAC_URL = "https://constexpr.org/innoextract/files/innoextract-1.9/innoextract-1.9-mac.tar.xz";

    private static Path cachedExecutablePath = null;

    /**
     * Obtém o caminho do executável innoextract pronto para uso.
     * Procura no PATH e em diretórios padrão. Se não encontrar, tenta provisionar o binário portátil.
     */
    public static synchronized Optional<Path> getOrProvisionInnoExtract(InstallationProgressListener progressListener) {
        if (cachedExecutablePath != null && Files.exists(cachedExecutablePath) && Files.isExecutable(cachedExecutablePath)) {
            return Optional.of(cachedExecutablePath);
        }

        // 1. Procurar em executáveis já instalados no sistema / diretórios locais
        Optional<Path> found = findSystemInnoExtract();
        if (found.isPresent()) {
            cachedExecutablePath = found.get();
            return found;
        }

        // 2. Se não estiver instalado, provisionar automaticamente o binário standalone
        Optional<Path> provisioned = provisionPortableInnoExtract(progressListener);
        if (provisioned.isPresent()) {
            cachedExecutablePath = provisioned.get();
            return provisioned;
        }

        return Optional.empty();
    }

    /**
     * Procura o executável innoextract no PATH e em diretórios padrão do sistema.
     */
    public static Optional<Path> findSystemInnoExtract() {
        String exeName = isWindows() ? "innoextract.exe" : "innoextract";

        // Diretório padrão do PlayAnywhere (~/.playanywhere/bin)
        Path localBin = getPlayAnywhereBinDir().resolve(exeName);
        if (Files.exists(localBin) && (isWindows() || Files.isExecutable(localBin))) {
            return Optional.of(localBin);
        }

        // Caminhos de sistema comuns
        List<Path> commonPaths = List.of(
            Paths.get(System.getProperty("user.home"), ".local", "bin", exeName),
            Paths.get("/usr/local/bin", exeName),
            Paths.get("/usr/bin", exeName),
            Paths.get("/opt/homebrew/bin", exeName),
            Paths.get("/home/linuxbrew/.linuxbrew/bin", exeName)
        );

        for (Path p : commonPaths) {
            if (Files.exists(p) && (isWindows() || Files.isExecutable(p))) {
                return Optional.of(p);
            }
        }

        // Verificação no PATH do sistema
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (dir == null || dir.isBlank()) continue;
                Path p = Paths.get(dir, exeName);
                if (Files.exists(p) && (isWindows() || Files.isExecutable(p))) {
                    return Optional.of(p);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Baixa e configura o binário portátil do innoextract correspondente ao sistema operacional.
     */
    private static Optional<Path> provisionPortableInnoExtract(InstallationProgressListener progressListener) {
        Path targetBinDir = getPlayAnywhereBinDir();
        String exeName = isWindows() ? "innoextract.exe" : "innoextract";
        Path targetExe = targetBinDir.resolve(exeName);

        try {
            Files.createDirectories(targetBinDir);
            reportProgress(progressListener, 0.12, "📦 Provisionando ferramenta de extração Inno Setup (innoextract)...");
            LOGGER.info("Iniciando download e provisionamento automático do innoextract para " + targetExe.toAbsolutePath());

            if (isWindows()) {
                provisionWindows(targetExe);
            } else if (isMac()) {
                provisionMac(targetExe);
            } else {
                provisionLinux(targetExe);
            }

            if (Files.exists(targetExe)) {
                makeExecutable(targetExe);
                LOGGER.info("innoextract provisionado com sucesso em: " + targetExe.toAbsolutePath());
                return Optional.of(targetExe);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao provisionar innoextract automaticamente: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    private static void provisionWindows(Path targetExe) throws IOException {
        Path tempZip = Files.createTempFile("innoextract-win-", ".zip");
        try {
            downloadFile(INNOEXTRACT_WINDOWS_URL, tempZip);
            try (InputStream is = Files.newInputStream(tempZip);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith("innoextract.exe") || entry.getName().equalsIgnoreCase("innoextract.exe")) {
                        try (OutputStream os = Files.newOutputStream(targetExe, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            zis.transferTo(os);
                        }
                        break;
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    private static void provisionLinux(Path targetExe) throws IOException, InterruptedException {
        Path tempArchive = Files.createTempFile("innoextract-linux-", ".tar.xz");
        Path tempExtractDir = Files.createTempDirectory("innoextract-extract-");
        try {
            downloadFile(INNOEXTRACT_LINUX_URL, tempArchive);

            // Descompactar arquivo tar.xz usando utilitário tar do sistema
            ProcessBuilder pb = new ProcessBuilder("tar", "-xf", tempArchive.toAbsolutePath().toString(), "-C", tempExtractDir.toAbsolutePath().toString());
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new IOException("Falha ao descompactar innoextract tar.xz, código: " + exitCode);
            }

            // Selecionar binário adequado para a arquitetura
            String arch = System.getProperty("os.arch", "").toLowerCase();
            Path sourceBinary;
            if (arch.contains("arm") || arch.contains("aarch64")) {
                sourceBinary = tempExtractDir.resolve("innoextract-1.9-linux/bin/armv6j-hardfloat/innoextract");
                if (!Files.exists(sourceBinary)) {
                    sourceBinary = tempExtractDir.resolve("innoextract-1.9-linux/bin/amd64/innoextract");
                }
            } else if (arch.contains("86") && !arch.contains("64")) {
                sourceBinary = tempExtractDir.resolve("innoextract-1.9-linux/bin/i686/innoextract");
            } else {
                sourceBinary = tempExtractDir.resolve("innoextract-1.9-linux/bin/amd64/innoextract");
            }

            if (!Files.exists(sourceBinary)) {
                // Tenta busca recursiva pelo binário extraído
                try (var stream = Files.walk(tempExtractDir)) {
                    sourceBinary = stream.filter(f -> f.getFileName().toString().equals("innoextract") && Files.isRegularFile(f))
                            .findFirst()
                            .orElseThrow(() -> new FileNotFoundException("Binário innoextract não encontrado no pacote extraído."));
                }
            }

            Files.copy(sourceBinary, targetExe, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempArchive);
            deleteDirectoryRecursively(tempExtractDir);
        }
    }

    private static void provisionMac(Path targetExe) throws IOException, InterruptedException {
        Path tempArchive = Files.createTempFile("innoextract-mac-", ".tar.xz");
        Path tempExtractDir = Files.createTempDirectory("innoextract-extract-");
        try {
            downloadFile(INNOEXTRACT_MAC_URL, tempArchive);
            ProcessBuilder pb = new ProcessBuilder("tar", "-xf", tempArchive.toAbsolutePath().toString(), "-C", tempExtractDir.toAbsolutePath().toString());
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                throw new IOException("Falha ao descompactar innoextract mac tar.xz, código: " + exitCode);
            }

            Path sourceBinary = tempExtractDir.resolve("innoextract-1.9-mac/bin/innoextract");
            if (!Files.exists(sourceBinary)) {
                try (var stream = Files.walk(tempExtractDir)) {
                    sourceBinary = stream.filter(f -> f.getFileName().toString().equals("innoextract") && Files.isRegularFile(f))
                            .findFirst()
                            .orElseThrow(() -> new FileNotFoundException("Binário innoextract não encontrado no pacote Mac."));
                }
            }
            Files.copy(sourceBinary, targetExe, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tempArchive);
            deleteDirectoryRecursively(tempExtractDir);
        }
    }

    private static void downloadFile(String urlString, Path targetPath) throws IOException {
        URI uri = URI.create(urlString);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 PlayAnywhere/1.0");
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " ao baixar: " + urlString);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[32768];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
    }

    private static void makeExecutable(Path file) {
        if (file == null || !Files.exists(file) || isWindows()) return;
        try {
            file.toFile().setExecutable(true, false);
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            Set<PosixFilePermission> updated = new HashSet<>(perms);
            updated.add(PosixFilePermission.OWNER_EXECUTE);
            updated.add(PosixFilePermission.GROUP_EXECUTE);
            updated.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, updated);
        } catch (Exception ignored) {
        }
    }

    private static Path getPlayAnywhereBinDir() {
        return Paths.get(System.getProperty("user.home"), ".playanywhere", "bin");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static void reportProgress(InstallationProgressListener listener, double progress, String message) {
        if (listener != null) {
            listener.onProgress(progress, message);
        }
    }

    private static void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {
        }
    }
}
