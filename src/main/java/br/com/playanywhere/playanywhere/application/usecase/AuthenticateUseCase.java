package br.com.playanywhere.playanywhere.application.usecase;

import br.com.playanywhere.playanywhere.domain.model.AuthSession;
import br.com.playanywhere.playanywhere.domain.repository.AuthGateway;
import br.com.playanywhere.playanywhere.domain.repository.AuthSessionRepository;
import br.com.playanywhere.playanywhere.infrastructure.storage.SqliteAuthSessionRepository;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Caso de uso para gerenciar o fluxo de autenticação OAuth2 e persistência segura de sessão.
 */
public class AuthenticateUseCase {
    private static final Logger LOGGER = Logger.getLogger(AuthenticateUseCase.class.getName());

    private final AuthGateway authGateway;
    private final AuthSessionRepository authSessionRepository;

    public AuthenticateUseCase(AuthGateway authGateway) {
        this(authGateway, SqliteAuthSessionRepository.getInstance());
    }

    public AuthenticateUseCase(AuthGateway authGateway, AuthSessionRepository authSessionRepository) {
        this.authGateway = Objects.requireNonNull(authGateway, "AuthGateway não pode ser nulo.");
        this.authSessionRepository = Objects.requireNonNull(authSessionRepository, "AuthSessionRepository não pode ser nulo.");
    }

    public String buildAuthorizationUrl() {
        LOGGER.info("AuthenticateUseCase: gerando URL de autorização padrão.");
        return authGateway.buildAuthorizationUrl();
    }

    public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) {
        LOGGER.info(() -> "AuthenticateUseCase: gerando URL de autorização customizada (clientId=" + clientId + ")");
        return authGateway.buildAuthorizationUrl(clientId, redirectUri, scope);
    }

    public String extractCodeFromUrl(String url) {
        LOGGER.fine("AuthenticateUseCase: extraindo código de URL.");
        return authGateway.extractCodeFromUrl(url);
    }

    public String exchangeCodeForToken(String code) throws Exception {
        LOGGER.info("AuthenticateUseCase: solicitando troca de código por token ao gateway de autenticação.");
        String token = authGateway.exchangeCodeForToken(code);
        saveCurrentSession();
        return token;
    }

    public String refreshAccessToken() {
        LOGGER.info("AuthenticateUseCase: solicitando renovação de access_token ao gateway de autenticação.");
        String token = authGateway.refreshAccessToken();
        if (token != null && !token.trim().isEmpty()) {
            saveCurrentSession();
        }
        return token;
    }

    /**
     * Tenta restaurar e revalidar a sessão salva no banco SQLite local.
     * Retorna true se a sessão for válida ou renovada com sucesso.
     */
    public boolean restoreSavedSession() {
        LOGGER.info("AuthenticateUseCase: verificando existência de sessão salva no repositório...");
        var optionalSession = authSessionRepository.getSession();
        if (optionalSession.isEmpty()) {
            LOGGER.info("AuthenticateUseCase: nenhuma sessão salva encontrada.");
            return false;
        }

        AuthSession session = optionalSession.get();
        if (session.accessToken() == null || session.accessToken().trim().isEmpty()) {
            LOGGER.warning("AuthenticateUseCase: sessão salva encontrada mas com accessToken inválido.");
            authSessionRepository.deleteSession();
            return false;
        }

        // Configura os dados da sessão no gateway
        authGateway.setSession(
            session.accessToken(),
            session.refreshToken(),
            session.userId(),
            session.username(),
            session.email(),
            session.expirationTime()
        );

        // Se a sessão expirou ou não está válida, tenta renovar via refresh_token
        if (session.isExpired() || !authGateway.isValidToken()) {
            LOGGER.info("AuthenticateUseCase: token da sessão salva expirado ou próximo de expirar. Tentando renovação automática...");
            if (session.hasRefreshToken()) {
                String newAccessToken = authGateway.refreshAccessToken();
                if (newAccessToken != null && !newAccessToken.trim().isEmpty()) {
                    LOGGER.info("AuthenticateUseCase: sessão renovada com sucesso a partir do refresh_token.");
                    saveCurrentSession();
                    return true;
                } else {
                    LOGGER.warning("AuthenticateUseCase: falha ao renovar token com refresh_token. Removendo sessão salva.");
                    authGateway.clearSession();
                    authSessionRepository.deleteSession();
                    return false;
                }
            } else {
                LOGGER.warning("AuthenticateUseCase: token expirado e sem refresh_token disponível.");
                authGateway.clearSession();
                authSessionRepository.deleteSession();
                return false;
            }
        }

        LOGGER.info("AuthenticateUseCase: sessão salva é válida e foi restaurada com sucesso.");
        return true;
    }

    public void saveCurrentSession() {
        String accessToken = authGateway.getAccessToken();
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            AuthSession session = new AuthSession(
                accessToken,
                authGateway.getRefreshToken(),
                authGateway.getUserId(),
                authGateway.getUsername(),
                authGateway.getEmail(),
                authGateway.getTokenExpirationTime(),
                System.currentTimeMillis()
            );
            authSessionRepository.saveSession(session);
        }
    }

    public void logout() {
        LOGGER.info("AuthenticateUseCase: efetuando logout e limpando credenciais salvas.");
        authGateway.clearSession();
        authSessionRepository.deleteSession();
    }

    public boolean isAuthenticated() {
        return authGateway.isValidToken();
    }

    public String getAccessToken() {
        return authGateway.getAccessToken();
    }

    public String getUserId() {
        return authGateway.getUserId();
    }

    public String getUsername() {
        return authGateway.getUsername();
    }

    public String getEmail() {
        return authGateway.getEmail();
    }
}
