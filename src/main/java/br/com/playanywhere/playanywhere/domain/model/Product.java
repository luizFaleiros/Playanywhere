package br.com.playanywhere.playanywhere.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Modelo de domínio para representar um produto (jogo) da biblioteca.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {
    private int id;
    private String title;
    private String slug;
    private List<GalleryImage> gallery;
    private List<Screenshot> screenshots;
    private Price price;
    private boolean isInStock;
    private double rating;
    private String genres;
    private List<String> developer;
    private List<String> publisher;
    private String description;
    private long sizeMB;

    public Product() {}

    public Product(int id, String title, String slug) {
        this.id = id;
        this.title = title;
        this.slug = slug;
    }

    public Product(int id, String title, String slug, long sizeMB) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.sizeMB = sizeMB;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public List<GalleryImage> getGallery() { return gallery; }
    public void setGallery(List<GalleryImage> gallery) { this.gallery = gallery; }

    public List<Screenshot> getScreenshots() { return screenshots; }
    public void setScreenshots(List<Screenshot> screenshots) { this.screenshots = screenshots; }

    public Price getPrice() { return price; }
    public void setPrice(Price price) { this.price = price; }

    public boolean isInStock() { return isInStock; }
    public boolean isIsInStock() { return isInStock; }
    public void setInStock(boolean inStock) { this.isInStock = inStock; }
    public void setIsInStock(boolean isInStock) { this.isInStock = isInStock; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getGenres() { return genres; }
    public void setGenres(String genres) { this.genres = genres; }

    public List<String> getDeveloper() { return developer; }
    public void setDeveloper(List<String> developer) { this.developer = developer; }

    public List<String> getPublisher() { return publisher; }
    public void setPublisher(List<String> publisher) { this.publisher = publisher; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getSizeMB() {
        if (sizeMB > 0) {
            return sizeMB;
        }
        return calculateEstimatedSizeMB();
    }

    public void setSizeMB(long sizeMB) {
        this.sizeMB = sizeMB;
    }

    @JsonIgnore
    public String getFormattedSize() {
        long mb = getSizeMB();
        if (mb < 1024) {
            return mb + " MB";
        } else {
            double gb = mb / 1024.0;
            return String.format(java.util.Locale.US, "%.1f GB", gb);
        }
    }

    @JsonIgnore
    public StorageType getRecommendedStorageType() {
        return StorageType.recommendForGameSize(getSizeMB());
    }

    private long calculateEstimatedSizeMB() {
        if (title != null) {
            String lower = title.toLowerCase();
            if (lower.contains("witcher") || lower.contains("cyberpunk") || lower.contains("red dead") || lower.contains("gta")) {
                return 35000;
            }
            if (lower.contains("knight") || lower.contains("dead cells") || lower.contains("hades") || lower.contains("celeste")) {
                return 1500;
            }
            if (lower.contains("stardew") || lower.contains("terraria") || lower.contains("undertale") || lower.contains("doom")) {
                return 500;
            }
        }
        if (id > 0) {
            return 800 + (Math.abs(id) % 3500);
        }
        return 1200;
    }

    @Override
    public String toString() {
        return title != null ? title : ("Product #" + id);
    }
}
