package br.com.playanywhere.playanywhere.infrastructure.storage;

import br.com.playanywhere.playanywhere.domain.model.AuthSession;
import br.com.playanywhere.playanywhere.domain.repository.AuthSessionRepository;
import br.com.playanywhere.playanywhere.infrastructure.security.TokenEncryptionService;

import java.io.File;
import java.sql.*;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementação SQLite para persistência criptografada de tokens e sessão de autenticação.
 */
public class SqliteAuthSessionRepository implements AuthSessionRepository {
    private static final Logger LOGGER = Logger.getLogger(SqliteAuthSessionRepository.class.getName());

    private final String dbPath;
    private final TokenEncryptionService encryptionService;

    private static SqliteAuthSessionRepository instance;

    public static synchronized SqliteAuthSessionRepository getInstance() {
        if (instance == null) {
            String defaultPath = System.getProperty("user.home") + File.separator + ".playanywhere" + File.separator + "auth_session.db";
            instance = new SqliteAuthSessionRepository(defaultPath);
        }
        return instance;
    }

    public SqliteAuthSessionRepository(String dbPath) {
        this(dbPath, TokenEncryptionService.getInstance());
    }

    public SqliteAuthSessionRepository(String dbPath, TokenEncryptionService encryptionService) {
        this.dbPath = dbPath;
        this.encryptionService = encryptionService;
        initDatabase();
    }

    private void initDatabase() {
        try {
            File dbFile = new File(dbPath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                LOGGER.fine(() -> "Diretório de dados criado (" + parentDir.getAbsolutePath() + "): " + created);
            }

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                String createTableSql = """
                    CREATE TABLE IF NOT EXISTS auth_session (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        access_token_enc TEXT,
                        refresh_token_enc TEXT,
                        user_id TEXT,
                        username TEXT,
                        email TEXT,
                        expiration_time INTEGER,
                        updated_at INTEGER
                    );
                    """;
                stmt.execute(createTableSql);
                LOGGER.info("Banco de dados SQLite inicializado com sucesso em: " + dbPath);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao inicializar banco de dados SQLite (" + dbPath + "): " + e.getMessage(), e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @Override
    public synchronized void saveSession(AuthSession session) {
        if (session == null) {
            LOGGER.warning("Tentativa de salvar sessão nula no SQLite.");
            return;
        }

        LOGGER.info(() -> "Salvando sessão de autenticação no SQLite para o usuário: " + session.userId() + " (" + session.username() + ")");
        String encryptedAccessToken = encryptionService.encrypt(session.accessToken());
        String encryptedRefreshToken = encryptionService.encrypt(session.refreshToken());

        String sql = """
            INSERT OR REPLACE INTO auth_session (id, access_token_enc, refresh_token_enc, user_id, username, email, expiration_time, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?);
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, encryptedAccessToken);
            pstmt.setString(2, encryptedRefreshToken);
            pstmt.setString(3, session.userId());
            pstmt.setString(4, session.username());
            pstmt.setString(5, session.email());
            pstmt.setLong(6, session.expirationTime());
            pstmt.setLong(7, session.updatedAt() > 0 ? session.updatedAt() : System.currentTimeMillis());

            int affected = pstmt.executeUpdate();
            LOGGER.info("Sessão salva com sucesso no SQLite (" + affected + " linha afetada).");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha ao salvar sessão no SQLite: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized Optional<AuthSession> getSession() {
        String sql = "SELECT access_token_enc, refresh_token_enc, user_id, username, email, expiration_time, updated_at FROM auth_session WHERE id = 1 LIMIT 1";
        boolean shouldCleanCorrupted = false;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                String encAccessToken = rs.getString("access_token_enc");
                String encRefreshToken = rs.getString("refresh_token_enc");
                String userId = rs.getString("user_id");
                String username = rs.getString("username");
                String email = rs.getString("email");
                long expirationTime = rs.getLong("expiration_time");
                long updatedAt = rs.getLong("updated_at");

                String accessToken = null;
                String refreshToken = null;
                try {
                    accessToken = encryptionService.decrypt(encAccessToken);
                    refreshToken = encryptionService.decrypt(encRefreshToken);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Erro ao descriptografar tokens da sessão salva: " + ex.getMessage(), ex);
                    shouldCleanCorrupted = true;
                }

                if (!shouldCleanCorrupted && accessToken != null) {
                    LOGGER.info(() -> "Sessão recuperada do SQLite para userId=" + userId + ", username=" + username + ", expiraEm=" + expirationTime);
                    return Optional.of(new AuthSession(accessToken, refreshToken, userId, username, email, expirationTime, updatedAt));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao recuperar sessão do SQLite: " + e.getMessage(), e);
        }

        if (shouldCleanCorrupted) {
            deleteSession();
        }

        return Optional.empty();
    }

    @Override
    public synchronized void deleteSession() {
        String sql = "DELETE FROM auth_session WHERE id = 1";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int affected = pstmt.executeUpdate();
            LOGGER.info("Sessão removida do SQLite (" + affected + " linhas excluídas).");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao excluir sessão do SQLite: " + e.getMessage(), e);
        }
    }

    public String getDbPath() {
        return dbPath;
    }
}
