package br.com.playanywhere.playanywhere.infrastructure.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serviço de criptografia AES-256-GCM para proteção de tokens e credenciais confidenciais.
 */
public class TokenEncryptionService {
    private static final Logger LOGGER = Logger.getLogger(TokenEncryptionService.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH_BIT = 256;

    private static final byte[] SALT = new byte[] {
        (byte) 0x50, (byte) 0x6c, (byte) 0x61, (byte) 0x79,
        (byte) 0x41, (byte) 0x6e, (byte) 0x79, (byte) 0x77,
        (byte) 0x68, (byte) 0x65, (byte) 0x72, (byte) 0x65,
        (byte) 0x53, (byte) 0x65, (byte) 0x63, (byte) 0x75
    };

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    private static TokenEncryptionService instance;

    public static synchronized TokenEncryptionService getInstance() {
        if (instance == null) {
            instance = new TokenEncryptionService();
        }
        return instance;
    }

    public TokenEncryptionService() {
        this.secureRandom = new SecureRandom();
        this.secretKey = deriveKey();
    }

    private SecretKey deriveKey() {
        try {
            String machineFingerprint = System.getProperty("user.name", "default_user") + ":" +
                    System.getProperty("os.name", "generic_os") + ":" +
                    System.getProperty("user.home", "/home") + ":PlayAnywhere-AES256";

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(machineFingerprint.toCharArray(), SALT, ITERATION_COUNT, KEY_LENGTH_BIT);
            SecretKey tmp = factory.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao derivar chave criptográfica: " + e.getMessage(), e);
            throw new IllegalStateException("Falha ao inicializar chave criptográfica de tokens", e);
        }
    }

    /**
     * Criptografa uma string usando AES-GCM-256 e retorna o resultado em Base64 com IV embutido.
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao criptografar token: " + e.getMessage(), e);
            throw new RuntimeException("Falha na criptografia de token", e);
        }
    }

    /**
     * Descriptografa uma string Base64 encriptada com AES-GCM-256 e IV embutido.
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            if (decoded.length < IV_LENGTH_BYTE) {
                throw new IllegalArgumentException("Payload encriptado corrompido ou muito curto.");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao descriptografar token: " + e.getMessage(), e);
            throw new RuntimeException("Falha na descriptografia de token", e);
        }
    }
}
