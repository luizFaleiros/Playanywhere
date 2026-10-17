package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO Record para informações de preço na API GOG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceWrapper(
    @JsonProperty("base") int base,
    @JsonProperty("final") int finalPrice,
    @JsonProperty("discount") int discount
) {
    public int getBase() { return base; }
    public int getFinal() { return finalPrice; }
    public int getDiscount() { return discount; }
}
