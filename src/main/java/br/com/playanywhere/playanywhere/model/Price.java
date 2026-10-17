package br.com.playanywhere.playanywhere.model;

public class Price extends br.com.playanywhere.playanywhere.domain.model.Price {
    public Price() { super(); }
    public Price(int base, int finalPrice, int discount) {
        super(base, finalPrice, discount);
    }
}
