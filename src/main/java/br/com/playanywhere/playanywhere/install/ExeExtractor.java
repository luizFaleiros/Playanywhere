package br.com.playanywhere.playanywhere.install;

import br.com.playanywhere.playanywhere.application.usecase.InstallationProgressListener;
import br.com.playanywhere.playanywhere.launcher.WineDetector;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Classe responsável por inspecionar, descomprimir e extrair arquivos compactados contidos
 * em instaladores executáveis (.exe, SFX, Inno Setup, NSIS, ZIP executável) e distribuí-los
 * diretamente na unidade/driver de armazenamento de destino selecionado.
 */
public class ExeExtractor {
    private static final Logger LOGGER = Logger.getLogger(ExeExtractor.class.getName());

    // Assinatura mágica de cabeçalho ZIP: PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};

    // Assinatura mágica de executável DOS / PE: 'MZ' (0x4D, 0x5A)
    private static final byte[] DOS_MZ_MAGIC = new byte[]{0x4D, 0x5A};

    public ExeExtractor() {
    }

    /**
     * Extrai o conteúdo do instalador executável para o caminho de destino informado em String.
     */
    public ExtractionResult extract(String exePath, String targetDriveOrDir) throws IOException {
        if (exePath == null || targetDriveOrDir == null) {
            throw new IllegalArgumentException("Caminho do executável e diretório de destino não podem ser nulos.");
        }
        return extract(Paths.get(exePath), Paths.get(targetDriveOrDir), null);
    }

    /**
     * Extrai o conteúdo do instalador executável para o caminho de destino informado em File.
     */
    public ExtractionResult extract(File exeFile, File targetDriveOrDir) throws IOException {
        if (exeFile == null || targetDriveOrDir == null) {
            throw new IllegalArgumentException("Arquivo executável e diretório de destino não podem ser nulos.");
        }
        return extract(exeFile.toPath(), targetDriveOrDir.toPath(), null);
    }

    /**
     * Extrai o conteúdo do executável para o diretório de destino informado em Path.
     */
    public ExtractionResult extract(Path exePath, Path targetDriveOrDir) throws IOException {
        return extract(exePath, targetDriveOrDir, null);
    }

    /**
     * Extrai o conteúdo do instalador executável para o diretório/unidade de destino
     * com acompanhamento de progresso em tempo real.
     */
    public ExtractionResult extract(Path exePath, Path targetDriveOrDir, InstallationProgressListener progressListener) throws IOException {
        Objects.requireNonNull(exePath, "O arquivo executável do instalador não pode ser nulo.");
        Objects.requireNonNull(targetDriveOrDir, "O diretório/drive de destino não pode ser nulo.");

        if (!Files.exists(exePath) || !Files.isRegularFile(exePath)) {
            throw new FileNotFoundException("Arquivo instalador não encontrado: " + exePath.toAbsolutePath());
        }

        if (!Files.exists(targetDriveOrDir)) {
            Files.createDirectories(targetDriveOrDir);
        }

        LOGGER.info(() -> "Iniciando descompressão do executável " + exePath.getFileName() + " para a unidade " + targetDriveOrDir.toAbsolutePath());
        reportProgress(progressListener, 0.05, "🔍 Analisando formato do instalador executável (" + exePath.getFileName() + ")...");

        // 1. Tentar descompressão nativa ZIP / SFX (Java puro - rápido e multiplataforma)
        long zipOffset = findZipOffset(exePath);
        if (zipOffset >= 0) {
            try {
                LOGGER.info(() -> "Estrutura ZIP/SFX identificada no offset " + zipOffset + ". Descomprimindo via Java puro...");
                reportProgress(progressListener, 0.15, "📦 Extraindo arquivos compactados do instalador...");
                return extractZipSfx(exePath, zipOffset, targetDriveOrDir, progressListener);
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na extração ZIP pura (" + e.getMessage() + "), tentando métodos alternativos...", e);
            }
        }

        // 2. Se innoextract estiver disponível no sistema ou puder ser provisionado automaticamente
        Optional<Path> innoExtractPath = InnoExtractProvisioner.getOrProvisionInnoExtract(progressListener);
        if (innoExtractPath.isPresent()) {
            try {
                LOGGER.info(() -> "Tentando descompressão via innoextract (" + innoExtractPath.get() + ")...");
                reportProgress(progressListener, 0.20, "📦 Extraindo instalador Inno Setup via innoextract...");
                return extractWithInnoExtract(innoExtractPath.get().toAbsolutePath().toString(), exePath, targetDriveOrDir, progressListener);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na extração via innoextract: " + e.getMessage(), e);
            }
        }

        // 3. Se unar (The Universal Archive unpacker) estiver disponível no sistema
        String unarBin = findAvailableTool("unar");
        if (unarBin != null) {
            try {
                LOGGER.info(() -> "Tentando descompressão via unar (" + unarBin + ")...");
                reportProgress(progressListener, 0.20, "📦 Descompactando arquivos via unar...");
                return extractWithUnar(unarBin, exePath, targetDriveOrDir, progressListener);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na extração via unar: " + e.getMessage(), e);
            }
        }

        // 4. Se 7-Zip (7z / 7za / 7zr) estiver disponível no sistema
        String sevenZipBin = findAvailableTool("7z", "7za", "7zr", "7z.exe");
        if (sevenZipBin != null) {
            try {
                LOGGER.info(() -> "Tentando descompressão via " + sevenZipBin + "...");
                reportProgress(progressListener, 0.20, "📦 Descompactando arquivos via 7-Zip...");
                return extractWithSevenZip(sevenZipBin, exePath, targetDriveOrDir, progressListener);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na extração via 7-Zip: " + e.getMessage(), e);
            }
        }

        // 5. Se cabextract estiver disponível no sistema
        String cabExtractBin = findAvailableTool("cabextract");
        if (cabExtractBin != null) {
            try {
                LOGGER.info(() -> "Tentando descompressão via cabextract (" + cabExtractBin + ")...");
                reportProgress(progressListener, 0.20, "📦 Extraindo arquivos via cabextract...");
                return extractWithCabExtract(cabExtractBin, exePath, targetDriveOrDir, progressListener);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na extração via cabextract: " + e.getMessage(), e);
            }
        }

        // 6. Se estiver em ambiente Windows ou Wine disponível, tentar extração silenciosa do instalador
        if (isWindows() || WineDetector.isWineAvailable()) {
            try {
                LOGGER.info("Tentando execução silenciosa do instalador para o diretório de destino...");
                reportProgress(progressListener, 0.25, "⚙️ Executando instalador em modo silencioso para a unidade...");
                return extractViaSilentExecution(exePath, targetDriveOrDir, progressListener);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Falha na execução silenciosa do instalador: " + e.getMessage(), e);
            }
        }

        // Se nenhum método foi capaz de descomprimir
        ExtractionDependencyChecker.ExtractionReport report = ExtractionDependencyChecker.checkDependencies();
        StringBuilder errorSb = new StringBuilder();
        errorSb.append("Não foi possível descomprimir o arquivo ").append(exePath.getFileName())
                .append(". Formato não suportado diretamente ou ferramentas de extração ausentes (innoextract / 7z).");
        if (report.suggestedCommand() != null && !report.suggestedCommand().isBlank()) {
            errorSb.append(" Instale com: ").append(report.suggestedCommand());
        }
        String errorMsg = errorSb.toString();
        LOGGER.severe(errorMsg);
        throw new IOException(errorMsg);
    }

    /**
     * Verifica se o arquivo é um executável ou arquivo compactado reconhecido.
     */
    public boolean isExtractableExe(Path file) {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return false;
        }

        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".exe") || name.endsWith(".zip") || name.endsWith(".bin") || name.endsWith(".sfx")) {
            return true;
        }

        try {
            return findZipOffset(file) >= 0 || hasMzHeader(file);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Descomprime um arquivo ZIP ou Executável SFX (com offset) diretamente via Java NIO.
     */
    private ExtractionResult extractZipSfx(Path exePath, long zipOffset, Path targetDir, InstallationProgressListener progressListener) throws IOException {
        int fileCount = 0;
        long totalBytes = 0;

        // Se o offset for 0, tenta primeiro via ZipFile para melhor performance
        if (zipOffset == 0) {
            try (ZipFile zipFile = new ZipFile(exePath.toFile())) {
                int totalEntries = zipFile.size();
                int currentEntry = 0;

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    currentEntry++;

                    Path destPath = resolveAndValidatePath(targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath);
                    } else {
                        if (destPath.getParent() != null) {
                            Files.createDirectories(destPath.getParent());
                        }
                        try (InputStream in = zipFile.getInputStream(entry);
                             OutputStream out = Files.newOutputStream(destPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            long bytes = in.transferTo(out);
                            totalBytes += bytes;
                            fileCount++;
                        }
                        ensureFileExecutableIfApplicable(destPath);
                    }

                    if (totalEntries > 0) {
                        double progress = 0.15 + (0.75 * ((double) currentEntry / totalEntries));
                        reportProgress(progressListener, progress, "📦 Descomprimindo: " + entry.getName());
                    }
                }

                Path mainExe = findMainExecutable(targetDir);
                reportProgress(progressListener, 0.95, "✅ Descompressão de arquivos concluída com sucesso!");
                return ExtractionResult.success(targetDir, fileCount, totalBytes, "JAVA_ZIP_FILE", mainExe);
            } catch (SecurityException se) {
                throw se;
            } catch (Exception e) {
                LOGGER.fine(() -> "Tentativa via ZipFile falhou (" + e.getMessage() + "), alternando para ZipInputStream.");
            }
        }

        // Descompressão baseada em Stream pulando os bytes de cabeçalho do executável
        try (InputStream fis = Files.newInputStream(exePath);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            if (zipOffset > 0) {
                long skipped = 0;
                while (skipped < zipOffset) {
                    long currentSkip = bis.skip(zipOffset - skipped);
                    if (currentSkip <= 0) break;
                    skipped += currentSkip;
                }
            }

            try (ZipInputStream zis = new ZipInputStream(bis)) {
                ZipEntry entry;
                byte[] buffer = new byte[65536];

                while ((entry = zis.getNextEntry()) != null) {
                    Path destPath = resolveAndValidatePath(targetDir, entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath);
                    } else {
                        if (destPath.getParent() != null) {
                            Files.createDirectories(destPath.getParent());
                        }
                        try (OutputStream out = Files.newOutputStream(destPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            int read;
                            while ((read = zis.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                totalBytes += read;
                            }
                            fileCount++;
                        }
                        ensureFileExecutableIfApplicable(destPath);
                    }

                    zis.closeEntry();
                    reportProgress(progressListener, 0.50, "📦 Descomprimindo: " + entry.getName());
                }
            }
        }

        Path mainExe = findMainExecutable(targetDir);
        reportProgress(progressListener, 0.95, "✅ Descompressão de arquivos concluída com sucesso!");
        return ExtractionResult.success(targetDir, fileCount, totalBytes, "JAVA_ZIP_STREAM", mainExe);
    }

    /**
     * Extrai arquivos utilizando innoextract.
     */
    private ExtractionResult extractWithInnoExtract(String innoExtractBin, Path exePath, Path targetDir, InstallationProgressListener progressListener) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            innoExtractBin,
            "--output-dir", targetDir.toAbsolutePath().toString(),
            "--extract",
            "--gog",
            "--collisions", "overwrite",
            "--default-language", "english",
            exePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        StringBuilder outputLog = new StringBuilder();
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLog.append(line).append("\n");
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("[innoextract] " + line);
                }
                if (line.contains("Extracting")) {
                    reportProgress(progressListener, 0.60, "📦 " + line.trim());
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorDetail = outputLog.toString().trim();
            if (errorDetail.length() > 300) {
                errorDetail = errorDetail.substring(errorDetail.length() - 300);
            }
            throw new IOException("innoextract finalizou com código de erro: " + exitCode + (errorDetail.isBlank() ? "" : (" (" + errorDetail + ")")));
        }

        // Ajusta se os arquivos foram colocados em subpastas comuns geradas por instaladores
        flattenCommonDirectories(targetDir);

        int count = countFiles(targetDir);
        long bytes = calculateDirectorySize(targetDir);
        Path mainExe = findMainExecutable(targetDir);
        return ExtractionResult.success(targetDir, count, bytes, "INNOEXTRACT", mainExe);
    }

    /**
     * Extrai arquivos utilizando unar (Universal Archive unpacker).
     */
    private ExtractionResult extractWithUnar(String unarBin, Path exePath, Path targetDir, InstallationProgressListener progressListener) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            unarBin,
            "-o", targetDir.toAbsolutePath().toString(),
            "-f",
            "-D",
            exePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("[unar] " + line);
                }
                reportProgress(progressListener, 0.60, "📦 " + line.trim());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("unar finalizou com código de erro: " + exitCode);
        }

        flattenCommonDirectories(targetDir);

        int count = countFiles(targetDir);
        long bytes = calculateDirectorySize(targetDir);
        Path mainExe = findMainExecutable(targetDir);
        return ExtractionResult.success(targetDir, count, bytes, "UNAR", mainExe);
    }

    /**
     * Extrai arquivos utilizando cabextract.
     */
    private ExtractionResult extractWithCabExtract(String cabExtractBin, Path exePath, Path targetDir, InstallationProgressListener progressListener) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            cabExtractBin,
            "-d", targetDir.toAbsolutePath().toString(),
            exePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("[cabextract] " + line);
                }
                reportProgress(progressListener, 0.60, "📦 " + line.trim());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("cabextract finalizou com código de erro: " + exitCode);
        }

        flattenCommonDirectories(targetDir);

        int count = countFiles(targetDir);
        long bytes = calculateDirectorySize(targetDir);
        Path mainExe = findMainExecutable(targetDir);
        return ExtractionResult.success(targetDir, count, bytes, "CABEXTRACT", mainExe);
    }

    /**
     * Extrai arquivos utilizando 7-Zip.
     */
    private ExtractionResult extractWithSevenZip(String sevenZipBin, Path exePath, Path targetDir, InstallationProgressListener progressListener) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            sevenZipBin,
            "x",
            "-y",
            "-o" + targetDir.toAbsolutePath().toString(),
            exePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("[7z] " + line);
                }
                if (line.startsWith("Extracting")) {
                    reportProgress(progressListener, 0.60, "📦 " + line.trim());
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(sevenZipBin + " finalizou com código de erro: " + exitCode);
        }

        flattenCommonDirectories(targetDir);

        int count = countFiles(targetDir);
        long bytes = calculateDirectorySize(targetDir);
        Path mainExe = findMainExecutable(targetDir);
        return ExtractionResult.success(targetDir, count, bytes, "7ZIP", mainExe);
    }

    /**
     * Executa o instalador em modo silencioso direcionando a instalação para a unidade de destino.
     */
    private ExtractionResult extractViaSilentExecution(Path exePath, Path targetDir, InstallationProgressListener progressListener) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        String targetStr = targetDir.toAbsolutePath().toString();

        if (isWindows()) {
            command.add(exePath.toAbsolutePath().toString());
            command.add("/DIR=" + targetStr);
            command.add("/SILENT");
            command.add("/VERYSILENT");
            command.add("/SP-");
            command.add("/NORESTART");
            command.add("/SUPPRESSMSGBOXES");
        } else {
            Optional<Path> wine = WineDetector.findWineExecutable();
            if (wine.isEmpty()) {
                throw new IOException("Wine não encontrado para executar o instalador no Linux.");
            }
            command.add(wine.get().toAbsolutePath().toString());
            command.add(exePath.toAbsolutePath().toString());
            String winPath = targetStr.startsWith("/") ? ("Z:" + targetStr.replace('/', '\\')) : targetStr;
            command.add("/DIR=" + winPath);
            command.add("/SILENT");
            command.add("/VERYSILENT");
            command.add("/SP-");
            command.add("/NORESTART");
            command.add("/SUPPRESSMSGBOXES");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (!isWindows()) {
            pb.environment().put("WINEPREFIX", WineDetector.getWinePrefixDirectory().toAbsolutePath().toString());
            pb.environment().put("WINEDEBUG", "-all");
        }

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("O instalador silencioso finalizou com código de erro: " + exitCode);
        }

        int count = countFiles(targetDir);
        long bytes = calculateDirectorySize(targetDir);
        Path mainExe = findMainExecutable(targetDir);
        return ExtractionResult.success(targetDir, count, bytes, "SILENT_INSTALLER", mainExe);
    }

    /**
     * Localiza o offset inicial do arquivo ZIP dentro do executável (.exe / SFX).
     * Retorna -1 se não houver assinatura ZIP válida.
     */
    public long findZipOffset(Path file) throws IOException {
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return -1;
        }

        long fileSize = Files.size(file);
        if (fileSize < 4) {
            return -1;
        }

        try (InputStream in = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(in)) {

            byte[] buffer = new byte[65536];
            long currentOffset = 0;
            int bytesRead;

            int matchIdx = 0;
            while ((bytesRead = bis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buffer[i] == ZIP_MAGIC[matchIdx]) {
                        matchIdx++;
                        if (matchIdx == ZIP_MAGIC.length) {
                            return currentOffset + i - (ZIP_MAGIC.length - 1);
                        }
                    } else if (buffer[i] == ZIP_MAGIC[0]) {
                        matchIdx = 1;
                    } else {
                        matchIdx = 0;
                    }
                }
                currentOffset += bytesRead;
            }
        }
        return -1;
    }

    /**
     * Verifica se o arquivo inicia com o cabeçalho executável DOS MZ.
     */
    public boolean hasMzHeader(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = new byte[2];
            int read = in.read(header);
            return read == 2 && header[0] == DOS_MZ_MAGIC[0] && header[1] == DOS_MZ_MAGIC[1];
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Valida e resolve o caminho evitando vulnerabilidade Zip Slip (Path Traversal).
     */
    private Path resolveAndValidatePath(Path targetDir, String entryName) throws IOException {
        Path resolved = targetDir.resolve(entryName).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            throw new SecurityException("Entrada ZIP inválida ou com caminho de escape detectada: " + entryName);
        }
        return resolved;
    }

    /**
     * Localiza o executável principal do jogo dentro do diretório extraído.
     */
    public Path findMainExecutable(Path targetDir) {
        if (targetDir == null || !Files.exists(targetDir)) {
            return null;
        }

        // 1. Nomes padrões de executáveis de jogos
        List<String> commonNames = List.of("game.exe", "launcher.exe", "start.exe", "play.exe", "start_game.sh", "game.sh");
        for (String name : commonNames) {
            Path p = targetDir.resolve(name);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return p;
            }
        }

        // 2. Busca recursiva por .exe ou .sh
        try (Stream<Path> stream = Files.walk(targetDir, 3)) {
            return stream.filter(Files::isRegularFile)
                         .filter(p -> {
                             String lower = p.getFileName().toString().toLowerCase();
                             return lower.endsWith(".exe") || lower.endsWith(".sh") || lower.endsWith(".bat");
                         })
                         .filter(p -> !p.getFileName().toString().toLowerCase().contains("unins"))
                         .findFirst()
                         .orElse(null);
        } catch (IOException e) {
            LOGGER.warning("Erro ao localizar executável principal: " + e.getMessage());
        }

        return null;
    }

    private void ensureFileExecutableIfApplicable(Path path) {
        if (isWindows() || path == null || !Files.exists(path)) return;
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".exe") || name.endsWith(".sh") || name.endsWith(".bin") || name.endsWith(".py")) {
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(path);
                Set<PosixFilePermission> newPerms = new HashSet<>(perms);
                newPerms.add(PosixFilePermission.OWNER_EXECUTE);
                newPerms.add(PosixFilePermission.GROUP_EXECUTE);
                newPerms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(path, newPerms);
            } catch (Exception ignored) {
                // Sistema de arquivos pode não suportar POSIX permissions (ex: FAT32, NTFS)
            }
        }
    }

    private void flattenCommonDirectories(Path targetDir) {
        flattenDirectoryIfSingleSubfolder(targetDir, "{app}");
        flattenDirectoryIfSingleSubfolder(targetDir, "app");
        flattenDirectoryIfSingleSubfolder(targetDir, "$_OUTDIR");
        flattenDirectoryIfSingleSubfolder(targetDir, "$PLUGINSDIR");
        flattenDirectoryIfSingleSubfolder(targetDir, "{tmp}");
        flattenDirectoryIfSingleSubfolder(targetDir, "game");
    }

    private void flattenDirectoryIfSingleSubfolder(Path targetDir, String folderName) {
        try {
            Path sub = targetDir.resolve(folderName);
            if (Files.exists(sub) && Files.isDirectory(sub)) {
                try (Stream<Path> stream = Files.list(sub)) {
                    for (Path item : stream.toList()) {
                        Path dest = targetDir.resolve(item.getFileName());
                        Files.move(item, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.deleteIfExists(sub);
            }
        } catch (Exception e) {
            LOGGER.warning("Aviso ao nivelar diretório extraído: " + e.getMessage());
        }
    }

    private int countFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private long calculateDirectorySize(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                         .mapToLong(p -> {
                             try {
                                 return Files.size(p);
                             } catch (IOException e) {
                                 return 0L;
                             }
                         }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean isToolAvailable(String toolName) {
        return findAvailableTool(toolName) != null;
    }

    private String findAvailableTool(String... candidates) {
        for (String tool : candidates) {
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(File.pathSeparator)) {
                    if (dir.isBlank()) continue;
                    Path p = Paths.get(dir, tool);
                    if (Files.exists(p) && Files.isExecutable(p)) {
                        return p.toAbsolutePath().toString();
                    }
                }
            }
            List<Path> commonDirs = List.of(
                Paths.get(System.getProperty("user.home"), ".playanywhere", "bin"),
                Paths.get(System.getProperty("user.home"), ".local", "bin"),
                Paths.get("/usr/local/bin"),
                Paths.get("/usr/bin"),
                Paths.get("/bin"),
                Paths.get("/opt/homebrew/bin"),
                Paths.get("/home/linuxbrew/.linuxbrew/bin")
            );
            for (Path dir : commonDirs) {
                Path p = dir.resolve(tool);
                if (Files.exists(p) && Files.isExecutable(p)) {
                    return p.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void reportProgress(InstallationProgressListener listener, double progress, String message) {
        if (listener != null) {
            listener.onProgress(progress, message);
        }
    }
}
