package br.com.playanywhere.playanywhere.install;

import java.nio.file.Path;

/**
 * Resultado do processo de descompressão/extração de um arquivo executável instalador.
 */
public record ExtractionResult(
    boolean success,
    Path targetDirectory,
    int extractedFilesCount,
    long totalExtractedBytes,
    String extractionMethod,
    Path mainExecutable,
    String message
) {
    public boolean isSuccess() {
        return success;
    }

    public int fileCount() {
        return extractedFilesCount;
    }

    public static ExtractionResult failure(Path targetDirectory, String message) {
        return new ExtractionResult(false, targetDirectory, 0, 0L, "NONE", null, message);
    }

    public static ExtractionResult success(Path targetDirectory, int count, long bytes, String method, Path executable) {
        return new ExtractionResult(true, targetDirectory, count, bytes, method, executable, "Extração concluída com sucesso.");
    }
}
