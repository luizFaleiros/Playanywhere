package br.com.playanywhere.playanywhere.domain.model;

/**
 * Modelo para representar uma imagem da galeria do jogo.
 */
public class GalleryImage {
    private String imagePath;
    private String imageHash;
    private String imageFormat;

    public GalleryImage() {}

    public GalleryImage(String imagePath, String imageHash, String imageFormat) {
        this.imagePath = imagePath;
        this.imageHash = imageHash;
        this.imageFormat = imageFormat;
    }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getImageHash() { return imageHash; }
    public void setImageHash(String imageHash) { this.imageHash = imageHash; }

    public String getImageFormat() { return imageFormat; }
    public void setImageFormat(String imageFormat) { this.imageFormat = imageFormat; }
}
