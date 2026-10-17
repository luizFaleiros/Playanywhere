package br.com.playanywhere.playanywhere.domain.repository;

import br.com.playanywhere.playanywhere.domain.model.AuthSession;
import java.util.Optional;

/**
 * Interface do repositório para persistência segura da sessão de autenticação.
 */
public interface AuthSessionRepository {
    void saveSession(AuthSession session);
    Optional<AuthSession> getSession();
    void deleteSession();
}
