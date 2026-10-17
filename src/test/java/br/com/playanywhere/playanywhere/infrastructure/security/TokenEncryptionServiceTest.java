package br.com.playanywhere.playanywhere.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new TokenEncryptionService();
    }

    @Test
    void testEncryptAndDecrypt() {
        String originalToken = "gog_access_token_secret_123456789_xyz";

        String encrypted = encryptionService.encrypt(originalToken);
        assertNotNull(encrypted);
        assertNotEquals(originalToken, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalToken, decrypted);
    }

    @Test
    void testUniqueIVPerEncryption() {
        String token = "same_secret_token";

        String enc1 = encryptionService.encrypt(token);
        String enc2 = encryptionService.encrypt(token);

        // Due to randomized IVs, two encryptions of the same string must yield different ciphertext
        assertNotEquals(enc1, enc2);

        // But both must decrypt to the original value
        assertEquals(token, encryptionService.decrypt(enc1));
        assertEquals(token, encryptionService.decrypt(enc2));
    }

    @Test
    void testNullAndEmptyHandling() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));

        assertEquals("", encryptionService.encrypt(""));
        assertEquals("", encryptionService.decrypt(""));
    }
}
