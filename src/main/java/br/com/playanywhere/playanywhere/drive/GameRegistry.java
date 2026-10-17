package br.com.playanywhere.playanywhere.drive;

import br.com.playanywhere.playanywhere.domain.model.InstalledGame;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.repository.GameRegistryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Repositório e registro de persistência local para jogos instalados.
 */
public class GameRegistry implements GameRegistryRepository {
    private static final Logger LOGGER = Logger.getLogger(GameRegistry.class.getName());
    private static final String REGISTRY_FILE = "registry.json";
    private static final Path REGISTRY_PATH = Paths.get(System.getProperty("user.home"), ".playanywhere", REGISTRY_FILE);
    private static GameRegistry instance;

    private List<InstalledGame> installedGames;
    private final ObjectMapper mapper;

    private GameRegistry() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.installedGames = loadRegistryInternal();
    }

    public static synchronized GameRegistry getInstance() {
        if (instance == null) {
            instance = new GameRegistry();
        }
        return instance;
    }

    @Override
    public void loadRegistry() {
        this.installedGames = loadRegistryInternal();
    }

    private List<InstalledGame> loadRegistryInternal() {
        File file = REGISTRY_PATH.toFile();
        if (!file.exists()) {
            LOGGER.fine(() -> "Arquivo de registro " + REGISTRY_PATH + " não existe ainda. Inicializando vazio.");
            return new ArrayList<>();
        }
        try {
            List<InstalledGame> games = mapper.readValue(file, new TypeReference<List<InstalledGame>>() {});
            games.removeIf(g -> g.targetPath() == null || !Files.exists(g.targetPath()));
            LOGGER.info(() -> "Registro de jogos carregado com sucesso: " + games.size() + " jogos registrados.");
            return games;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Erro ao carregar registro de jogos: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveRegistry() {
        try {
            File file = REGISTRY_PATH.toFile();
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            mapper.writeValue(file, installedGames);
            LOGGER.info(() -> "Registro de jogos salvo com sucesso (" + installedGames.size() + " jogos) em " + REGISTRY_PATH);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao salvar registro de jogos: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void registerGame(Product product, Path targetPath, boolean autoRunner) {
        LOGGER.info(() -> "Registrando jogo: " + (product != null ? product.getTitle() : "null") + " em " + targetPath + " (autoRunner=" + autoRunner + ")");
        if (targetPath != null) {
            if (!Files.exists(targetPath)) {
                try {
                    Files.createDirectories(targetPath);
                } catch (Exception ignored) {}
            }
            String norm = targetPath.toAbsolutePath().normalize().toString();
            installedGames.removeIf(g -> g.targetPath() != null && g.targetPath().toAbsolutePath().normalize().toString().equals(norm));
        }
        installedGames.add(new InstalledGame(product, targetPath, autoRunner));
        saveRegistry();
    }

    @Override
    public synchronized void unregisterGame(Path targetPath) {
        if (targetPath == null) return;
        String norm = targetPath.toAbsolutePath().normalize().toString();
        LOGGER.info(() -> "Removendo jogo registrado em: " + targetPath);
        installedGames.removeIf(g -> g.targetPath() != null && g.targetPath().toAbsolutePath().normalize().toString().equals(norm));
        saveRegistry();
    }

    @Override
    public synchronized List<InstalledGame> pruneMissingGames() {
        boolean removed = installedGames.removeIf(g -> g.targetPath() == null || !Files.exists(g.targetPath()));
        if (removed) {
            saveRegistry();
        }
        return new ArrayList<>(installedGames);
    }

    @Override
    public synchronized List<InstalledGame> getInstalledGames() {
        pruneMissingGames();
        return new ArrayList<>(installedGames);
    }
}
