package br.com.playanywhere.playanywhere.application.usecase;

import br.com.playanywhere.playanywhere.domain.model.AuthSession;
import br.com.playanywhere.playanywhere.domain.repository.AuthGateway;
import br.com.playanywhere.playanywhere.domain.repository.AuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticateUseCaseSessionTest {

    private FakeAuthGateway fakeGateway;
    private FakeAuthSessionRepository fakeSessionRepo;
    private AuthenticateUseCase useCase;

    @BeforeEach
    void setUp() {
        fakeGateway = new FakeAuthGateway();
        fakeSessionRepo = new FakeAuthSessionRepository();
        useCase = new AuthenticateUseCase(fakeGateway, fakeSessionRepo);
    }

    @Test
    void testRestoreValidSessionWithoutRefresh() {
        AuthSession validSession = new AuthSession(
                "token_123",
                "refresh_456",
                "user_789",
                "JohnGamer",
                "john@example.com",
                System.currentTimeMillis() + 3600_000L,
                System.currentTimeMillis()
        );
        fakeSessionRepo.saveSession(validSession);

        boolean restored = useCase.restoreSavedSession();
        assertTrue(restored);
        assertTrue(useCase.isAuthenticated());
        assertEquals("token_123", useCase.getAccessToken());
        assertEquals("user_789", useCase.getUserId());
        assertEquals("JohnGamer", useCase.getUsername());
        assertEquals(0, fakeGateway.refreshCallCount);
    }

    @Test
    void testRestoreExpiredSessionTriggersRefreshAndSavesNewToken() {
        AuthSession expiredSession = new AuthSession(
                "old_expired_token",
                "valid_refresh_token",
                "user_789",
                "JohnGamer",
                "john@example.com",
                System.currentTimeMillis() - 1000L,
                System.currentTimeMillis() - 1000L
        );
        fakeSessionRepo.saveSession(expiredSession);

        boolean restored = useCase.restoreSavedSession();
        assertTrue(restored);
        assertEquals(1, fakeGateway.refreshCallCount);
        assertEquals("refreshed_access_token_999", useCase.getAccessToken());

        // Verify that the updated session was saved back to the repository
        Optional<AuthSession> updated = fakeSessionRepo.getSession();
        assertTrue(updated.isPresent());
        assertEquals("refreshed_access_token_999", updated.get().accessToken());
    }

    @Test
    void testRestoreFailedRefreshDeletesSession() {
        AuthSession expiredSession = new AuthSession(
                "old_expired_token",
                "revoked_refresh_token",
                "user_789",
                "JohnGamer",
                "john@example.com",
                System.currentTimeMillis() - 1000L,
                System.currentTimeMillis() - 1000L
        );
        fakeSessionRepo.saveSession(expiredSession);
        fakeGateway.shouldFailRefresh = true;

        boolean restored = useCase.restoreSavedSession();
        assertFalse(restored);
        assertFalse(useCase.isAuthenticated());
        assertFalse(fakeSessionRepo.getSession().isPresent());
    }

    @Test
    void testLogoutClearsSessionInGatewayAndRepository() throws Exception {
        useCase.exchangeCodeForToken("valid_code");
        assertTrue(useCase.isAuthenticated());
        assertTrue(fakeSessionRepo.getSession().isPresent());

        useCase.logout();

        assertFalse(useCase.isAuthenticated());
        assertNull(useCase.getAccessToken());
        assertFalse(fakeSessionRepo.getSession().isPresent());
    }

    @Test
    void testRestoreSessionWithZeroExpirationTimeIsTreatedAsNonExpired() {
        AuthSession sessionWithZeroExp = new AuthSession(
                "token_zero_exp",
                "refresh_123",
                "user_999",
                "UserZero",
                "zero@example.com",
                0L,
                System.currentTimeMillis()
        );
        fakeSessionRepo.saveSession(sessionWithZeroExp);

        assertFalse(sessionWithZeroExp.isExpired());
        boolean restored = useCase.restoreSavedSession();
        assertTrue(restored);
        assertEquals("token_zero_exp", useCase.getAccessToken());
    }

    // Fakes
    static class FakeAuthGateway implements AuthGateway {
        String accessToken;
        String refreshToken = "initial_refresh_token";
        String userId = "user_789";
        String username = "JohnGamer";
        String email = "john@example.com";
        long expirationTime = 0L;
        int refreshCallCount = 0;
        boolean shouldFailRefresh = false;

        @Override
        public String buildAuthorizationUrl() { return "https://auth.gog.com"; }

        @Override
        public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) { return "https://auth.gog.com"; }

        @Override
        public String extractCodeFromUrl(String url) { return "code"; }

        @Override
        public String exchangeCodeForToken(String code) {
            this.accessToken = "exchanged_token_abc";
            this.expirationTime = System.currentTimeMillis() + 3600_000L;
            return accessToken;
        }

        @Override
        public String refreshAccessToken() {
            refreshCallCount++;
            if (shouldFailRefresh) {
                return null;
            }
            this.accessToken = "refreshed_access_token_999";
            this.expirationTime = System.currentTimeMillis() + 3600_000L;
            return accessToken;
        }

        @Override
        public boolean isValidToken() {
            if (accessToken == null || accessToken.trim().isEmpty()) return false;
            if (expirationTime <= 0) return true;
            return System.currentTimeMillis() < expirationTime;
        }

        @Override
        public String getAccessToken() { return accessToken; }

        @Override
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
            this.expirationTime = System.currentTimeMillis() + 3600_000L;
        }

        @Override
        public String getRefreshToken() { return refreshToken; }

        @Override
        public String getUserId() { return userId; }

        @Override
        public String getUsername() { return username; }

        @Override
        public String getEmail() { return email; }

        @Override
        public long getTokenExpirationTime() { return expirationTime; }

        @Override
        public void setSession(String accessToken, String refreshToken, String userId, String username, String email, long expirationTime) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.expirationTime = expirationTime;
        }

        @Override
        public void clearSession() {
            this.accessToken = null;
            this.refreshToken = null;
            this.userId = null;
            this.username = null;
            this.email = null;
            this.expirationTime = 0L;
        }
    }

    static class FakeAuthSessionRepository implements AuthSessionRepository {
        private AuthSession storedSession;

        @Override
        public void saveSession(AuthSession session) {
            this.storedSession = session;
        }

        @Override
        public Optional<AuthSession> getSession() {
            return Optional.ofNullable(storedSession);
        }

        @Override
        public void deleteSession() {
            this.storedSession = null;
        }
    }
}
