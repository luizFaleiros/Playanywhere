package br.com.playanywhere.playanywhere.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;

/**
 * Record para representar um jogo instalado no registro local/portátil.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledGame(
    @JsonProperty("product") Product product,
    @JsonProperty("targetPath") Path targetPath,
    @JsonProperty("autoRunner") boolean autoRunner
) {
    public Product getProduct() { return product; }
    public Path getTargetPath() { return targetPath; }
    public boolean isAutoRunner() { return autoRunner; }
}
