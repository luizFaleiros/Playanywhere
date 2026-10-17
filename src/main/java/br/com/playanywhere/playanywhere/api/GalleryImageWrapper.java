package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO Record para informações de imagens de galeria na API GOG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GalleryImageWrapper(
    @JsonProperty("imagePath") String imagePath,
    @JsonProperty("imageHash") String imageHash,
    @JsonProperty("imageFormat") String imageFormat
) {
    public String getImagePath() { return imagePath; }
    public String getImageHash() { return imageHash; }
    public String getImageFormat() { return imageFormat; }
}
