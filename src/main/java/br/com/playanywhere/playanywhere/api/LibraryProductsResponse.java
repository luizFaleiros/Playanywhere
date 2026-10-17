package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO Record para a resposta da lista de produtos da biblioteca GOG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LibraryProductsResponse(
    @JsonProperty("products") List<ProductWrapper> products,
    @JsonProperty("page") Integer page,
    @JsonProperty("totalPages") Integer totalPages,
    @JsonProperty("totalProducts") Integer totalProducts,
    @JsonProperty("totalGames") Integer totalGames,
    @JsonProperty("totalMovies") Integer totalMovies
) {
    public List<ProductWrapper> getProducts() { return products; }
    public Integer getPage() { return page; }
    public Integer getTotalPages() { return totalPages; }
    public Integer getTotalProducts() { return totalProducts; }
}
