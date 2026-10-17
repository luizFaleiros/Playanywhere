package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO Record para informações de produto na resposta de biblioteca da API GOG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductData(
    @JsonProperty("id") int id,
    @JsonProperty("title") String title,
    @JsonProperty("slug") String slug,
    @JsonProperty("image") String image,
    @JsonProperty("category") String category,
    @JsonProperty("gallery") List<GalleryImageWrapper> gallery,
    @JsonProperty("price") PriceWrapper price,
    @JsonProperty("isInStock") boolean isInStock,
    @JsonProperty("rating") double rating,
    @JsonProperty("developer") List<String> developer,
    @JsonProperty("publisher") List<String> publisher
) {
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getImage() { return image; }
    public String getCategory() { return category; }
    public List<GalleryImageWrapper> getGallery() { return gallery; }
    public PriceWrapper getPrice() { return price; }
    public boolean isInStock() { return isInStock; }
    public double getRating() { return rating; }
    public List<String> getDeveloper() { return developer; }
    public List<String> getPublisher() { return publisher; }
}
