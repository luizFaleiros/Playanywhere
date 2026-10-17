package br.com.playanywhere.playanywhere.model;

/**
 * Modelo no pacote model para compatibilidade, estendendo o domínio.
 */
public class Product extends br.com.playanywhere.playanywhere.domain.model.Product {
    public Product() {
        super();
    }

    public Product(int id, String title, String slug) {
        super(id, title, slug);
    }

    public Product(int id, String title, String slug, long sizeMB) {
        super(id, title, slug, sizeMB);
    }
}
