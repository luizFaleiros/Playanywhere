package br.com.playanywhere.playanywhere.domain.repository;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.Product;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface de repositório para registro e persistência de jogos instalados.
 */
public interface GameRegistryRepository {
    void registerGame(Product product, Path targetPath, boolean autoRunner);
    List<InstalledGame> getInstalledGames();
    void saveRegistry();
    void loadRegistry();
    default void unregisterGame(Path targetPath) {}
    default List<InstalledGame> pruneMissingGames() { return getInstalledGames(); }
}
