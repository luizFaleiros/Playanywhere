package br.com.playanywhere.playanywhere.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Modelo para representar os metadados contidos no arquivo playanywhere.json
 * de jogos instalados em pendrives ou mídias portáteis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayAnywhereMetadata(
    @JsonProperty("id") int id,
    @JsonProperty("title") String title,
    @JsonProperty("slug") String slug,
    @JsonProperty("executable") String executable,
    @JsonProperty("sizeMB") long sizeMB,
    @JsonProperty("formattedSize") String formattedSize,
    @JsonProperty("storageType") String storageType,
    @JsonProperty("mediaCategory") String mediaCategory,
    @JsonProperty("autoRunner") boolean autoRunner,
    @JsonProperty("installedAt") String installedAt,
    @JsonProperty("launchArguments") List<String> launchArguments,
    @JsonProperty("runnerType") String runnerType
) {
    public PlayAnywhereMetadata {
        if (executable == null || executable.trim().isEmpty()) {
            executable = "game.exe";
        }
    }

    public static PlayAnywhereMetadata createDefault(Product product, String storageType, String mediaCategory, boolean autoRunner) {
        return new PlayAnywhereMetadata(
            product != null ? product.getId() : 0,
            product != null ? product.getTitle() : "Desconhecido",
            product != null ? product.getSlug() : "game",
            "game.exe",
            product != null ? product.getSizeMB() : 0,
            product != null ? product.getFormattedSize() : "0 MB",
            storageType != null ? storageType : "Pen Drive",
            mediaCategory != null ? mediaCategory : "Pen Drive",
            autoRunner,
            java.time.Instant.now().toString(),
            List.of(),
            "AUTO"
        );
    }

    public Product toProduct() {
        return new Product(id, title, slug, sizeMB);
    }
}
