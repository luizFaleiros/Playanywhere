package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO Record para encapsular ProductData em respostas da API GOG,
 * suportando tanto o formato aninhado {"product": {...}} quanto o formato plano {...}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductWrapper(
    @JsonProperty("product") ProductData product,
    @JsonProperty("id") Integer id,
    @JsonProperty("title") String title,
    @JsonProperty("slug") String slug,
    @JsonProperty("image") String image,
    @JsonProperty("category") String category,
    @JsonProperty("gallery") List<GalleryImageWrapper> gallery,
    @JsonProperty("price") PriceWrapper price,
    @JsonProperty("isInStock") Boolean isInStock,
    @JsonProperty("rating") Double rating,
    @JsonProperty("developer") List<String> developer,
    @JsonProperty("publisher") List<String> publisher
) {
    public ProductData getProduct() {
        if (product != null) {
            return product;
        }
        if (id != null || title != null) {
            return new ProductData(
                id != null ? id : 0,
                title != null ? title : "",
                slug != null ? slug : "",
                image,
                category,
                gallery,
                price,
                isInStock != null ? isInStock : true,
                rating != null ? rating : 0.0,
                developer,
                publisher
            );
        }
        return null;
    }
}
