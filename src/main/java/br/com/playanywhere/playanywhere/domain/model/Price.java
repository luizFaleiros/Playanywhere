package br.com.playanywhere.playanywhere.domain.model;

/**
 * Modelo para representar informações de preço.
 */
public class Price {
    private int base;
    private int finalPrice;
    private int discount;

    public Price() {}

    public Price(int base, int finalPrice, int discount) {
        this.base = base;
        this.finalPrice = finalPrice;
        this.discount = discount;
    }

    public int getBase() { return base; }
    public void setBase(int base) { this.base = base; }

    public int getFinalPrice() { return finalPrice; }
    public void setFinalPrice(int finalPrice) { this.finalPrice = finalPrice; }

    public int getDiscount() { return discount; }
    public void setDiscount(int discount) { this.discount = discount; }
}
