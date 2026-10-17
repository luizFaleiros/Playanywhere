package br.com.playanywhere.playanywhere.domain.repository;

import br.com.playanywhere.playanywhere.domain.model.Product;
import java.util.List;

/**
 * Interface de repositório para acesso a catálogo e biblioteca de jogos.
 */
public interface GameRepository {
    List<Product> getLibraryProducts(int page, int pageSize);
    Product getProductDetails(int productId);
}
