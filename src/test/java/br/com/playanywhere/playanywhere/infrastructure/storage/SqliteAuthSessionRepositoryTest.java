package br.com.playanywhere.playanywhere.infrastructure.storage;

import br.com.playanywhere.playanywhere.domain.model.AuthSession;
import br.com.playanywhere.playanywhere.infrastructure.security.TokenEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SqliteAuthSessionRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteAuthSessionRepository repository;
    private String dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test_session.db").toString();
        repository = new SqliteAuthSessionRepository(dbPath, new TokenEncryptionService());
    }

    @AfterEach
    void tearDown() {
        File dbFile = new File(dbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    void testSaveAndGetSession() {
        long expirationTime = System.currentTimeMillis() + 3600_000L;
        AuthSession session = new AuthSession(
                "access_token_123",
                "refresh_token_456",
                "user_789",
                "GamerPro",
                "gamer@example.com",
                expirationTime,
                System.currentTimeMillis()
        );

        repository.saveSession(session);

        Optional<AuthSession> retrievedOpt = repository.getSession();
        assertTrue(retrievedOpt.isPresent());

        AuthSession retrieved = retrievedOpt.get();
        assertEquals("access_token_123", retrieved.accessToken());
        assertEquals("refresh_token_456", retrieved.refreshToken());
        assertEquals("user_789", retrieved.userId());
        assertEquals("GamerPro", retrieved.username());
        assertEquals("gamer@example.com", retrieved.email());
        assertEquals(expirationTime, retrieved.expirationTime());
        assertFalse(retrieved.isExpired());
    }

    @Test
    void testTokensAreEncryptedInDatabaseFile() throws Exception {
        String plainAccessToken = "raw_super_secret_access_token";
        String plainRefreshToken = "raw_super_secret_refresh_token";

        AuthSession session = new AuthSession(
                plainAccessToken,
                plainRefreshToken,
                "user_999",
                "PlayerOne",
                "player1@gog.com",
                System.currentTimeMillis() + 3600_000L,
                System.currentTimeMillis()
        );

        repository.saveSession(session);

        // Verify the raw stored data in SQLite directly via JDBC without decryption
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT access_token_enc, refresh_token_enc FROM auth_session WHERE id = 1")) {

            assertTrue(rs.next());
            String rawEncAccessToken = rs.getString("access_token_enc");
            String rawEncRefreshToken = rs.getString("refresh_token_enc");

            assertNotNull(rawEncAccessToken);
            assertNotNull(rawEncRefreshToken);

            // Raw tokens in the database must NOT match the plaintext tokens
            assertNotEquals(plainAccessToken, rawEncAccessToken);
            assertNotEquals(plainRefreshToken, rawEncRefreshToken);
            assertFalse(rawEncAccessToken.contains(plainAccessToken));
            assertFalse(rawEncRefreshToken.contains(plainRefreshToken));
        }
    }

    @Test
    void testUpdateSessionOverwritesPrevious() {
        AuthSession initial = new AuthSession(
                "token_1",
                "refresh_1",
                "user_1",
                "User1",
                "user1@gog.com",
                System.currentTimeMillis() + 1000L,
                System.currentTimeMillis()
        );
        repository.saveSession(initial);

        AuthSession updated = new AuthSession(
                "token_2_refreshed",
                "refresh_2_new",
                "user_1",
                "User1Updated",
                "user1@gog.com",
                System.currentTimeMillis() + 7200_000L,
                System.currentTimeMillis()
        );
        repository.saveSession(updated);

        Optional<AuthSession> retrievedOpt = repository.getSession();
        assertTrue(retrievedOpt.isPresent());
        AuthSession retrieved = retrievedOpt.get();

        assertEquals("token_2_refreshed", retrieved.accessToken());
        assertEquals("refresh_2_new", retrieved.refreshToken());
        assertEquals("User1Updated", retrieved.username());
    }

    @Test
    void testDeleteSession() {
        AuthSession session = new AuthSession(
                "token_to_delete",
                "refresh_to_delete",
                "user_del",
                "UserDel",
                "del@example.com",
                System.currentTimeMillis() + 3600_000L,
                System.currentTimeMillis()
        );
        repository.saveSession(session);
        assertTrue(repository.getSession().isPresent());

        repository.deleteSession();
        assertFalse(repository.getSession().isPresent());
    }

    @Test
    void testCorruptedEncryptedDataReturnsEmptyAndCleansUp() throws Exception {
        // Manually write corrupted data directly into SQLite
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT OR REPLACE INTO auth_session (id, access_token_enc, refresh_token_enc, user_id, username, email, expiration_time, updated_at) " +
                    "VALUES (1, 'NOT_VALID_BASE64_CIPHER', 'INVALID', 'u1', 'user1', 'u@g.com', 9999999999, 12345)");
        }

        Optional<AuthSession> sessionOpt = repository.getSession();
        assertFalse(sessionOpt.isPresent());
        // Verify that the corrupted row was deleted
        assertFalse(repository.getSession().isPresent());
    }
}
