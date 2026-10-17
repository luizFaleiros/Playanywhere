package br.com.playanywhere.playanywhere.domain.repository;

import java.util.List;

/**
 * Interface de repositório para detecção e gerenciamento de unidades de armazenamento.
 */
public interface DriveRepository {
    List<String> getMountableDrives();
}
