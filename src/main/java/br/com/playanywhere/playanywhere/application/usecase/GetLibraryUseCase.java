package br.com.playanywhere.playanywhere.application.usecase;

import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.repository.GameRepository;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Caso de uso para obter a lista de jogos da biblioteca do usuário.
 */
public class GetLibraryUseCase {
    private static final Logger LOGGER = Logger.getLogger(GetLibraryUseCase.class.getName());

    private final GameRepository gameRepository;

    public GetLibraryUseCase(GameRepository gameRepository) {
        this.gameRepository = Objects.requireNonNull(gameRepository, "GameRepository não pode ser nulo.");
    }

    public List<Product> execute(int page, int pageSize) {
        LOGGER.info(() -> "GetLibraryUseCase: buscando produtos da biblioteca (page=" + page + ", pageSize=" + pageSize + ")");
        return gameRepository.getLibraryProducts(page, pageSize);
    }
}
