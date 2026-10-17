package br.com.playanywhere.playanywhere.domain.repository;

/**
 * Interface de Gateway para autenticação OAuth2 (GOG / Google / SSO).
 */
public interface AuthGateway {
    String buildAuthorizationUrl();
    String buildAuthorizationUrl(String clientId, String redirectUri, String scope);
    String extractCodeFromUrl(String url);
    String exchangeCodeForToken(String code) throws Exception;
    default String refreshAccessToken() {
        return null;
    }
    boolean isValidToken();
    String getAccessToken();
    void setAccessToken(String accessToken);
    String getRefreshToken();
    String getUserId();
    default String getUsername() {
        return null;
    }
    default String getEmail() {
        return null;
    }
    default long getTokenExpirationTime() {
        return 0L;
    }
    default void setSession(String accessToken, String refreshToken, String userId, String username, String email, long expirationTime) {
    }
    default void clearSession() {
    }
}
