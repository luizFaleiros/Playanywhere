package br.com.playanywhere.playanywhere.domain.model;

/**
 * Representa uma sessão de autenticação ativa e persistível.
 */
public record AuthSession(
    String accessToken,
    String refreshToken,
    String userId,
    String username,
    String email,
    long expirationTime,
    long updatedAt
) {
    public boolean isExpired() {
        if (expirationTime <= 0) {
            return false;
        }
        // Considera expirado se faltar menos de 60 segundos para vencer
        return System.currentTimeMillis() >= (expirationTime - 60_000L);
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.trim().isEmpty();
    }
}
