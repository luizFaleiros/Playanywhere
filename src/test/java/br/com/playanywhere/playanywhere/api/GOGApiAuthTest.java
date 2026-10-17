package br.com.playanywhere.playanywhere.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GOGApiAuthTest {

    @Test
    void testBuildAuthorizationUrlDefault() {
        GOGApi api = GOGApi.getInstance();
        String url = api.buildAuthorizationUrl();

        assertNotNull(url);
        assertTrue(url.startsWith("https://auth.gog.com/auth"));
        assertTrue(url.contains("client_id=46899977096215655"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("layout=default"));
        assertTrue(url.contains("brand=gog"));
    }

    @Test
    void testBuildAuthorizationUrlCustom() {
        GOGApi api = GOGApi.getInstance();
        String url = api.buildAuthorizationUrl("custom_client_123", "https://localhost:8080/callback", "user library");

        assertNotNull(url);
        assertTrue(url.contains("client_id=custom_client_123"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Flocalhost%3A8080%2Fcallback"));
        assertTrue(url.contains("scope=user+library"));
    }

    @Test
    void testExtractCodeFromUrl() {
        GOGApi api = GOGApi.getInstance();

        // Standard GOG redirect url
        String url1 = "https://embed.gog.com/on_login_success?origin=client&code=sample_auth_code_123";
        assertEquals("sample_auth_code_123", api.extractCodeFromUrl(url1));

        // URL with multiple parameters
        String url2 = "https://www.gog.com/on_login_success?state=xyz&code=abc_def_456&extra=1";
        assertEquals("abc_def_456", api.extractCodeFromUrl(url2));

        // URL with encoded characters
        String url3 = "https://embed.gog.com/on_login_success?code=code%2Bwith%2Bspecial%2Bchars";
        assertEquals("code+with+special+chars", api.extractCodeFromUrl(url3));

        // Raw code string
        String rawCode = "raw_oauth_code_789";
        assertEquals("raw_oauth_code_789", api.extractCodeFromUrl(rawCode));

        // Fragment URL
        String fragmentUrl = "https://embed.gog.com/on_login_success#code=fragment_code_123";
        assertEquals("fragment_code_123", api.extractCodeFromUrl(fragmentUrl));

        // URL with error param (should return null)
        String errorUrl = "https://embed.gog.com/on_login_success?error=access_denied&error_description=User+denied+access";
        assertNull(api.extractCodeFromUrl(errorUrl));

        // Invalid or null
        assertNull(api.extractCodeFromUrl(null));
        assertNull(api.extractCodeFromUrl(""));
        assertNull(api.extractCodeFromUrl("   "));
    }

    @Test
    void testTokenResponse() {
        TokenResponse response = new TokenResponse("token_xyz", 3600, "refresh_xyz", "user_123", "bearer");

        assertEquals("token_xyz", response.accessToken());
        assertEquals("token_xyz", response.getAccessToken());
        assertEquals(3600, response.expiresIn());
        assertEquals(3600, response.getExpiresIn());
        assertEquals("refresh_xyz", response.refreshToken());
        assertEquals("refresh_xyz", response.getRefreshToken());
        assertEquals("user_123", response.userId());
        assertEquals("user_123", response.getUserId());
        assertEquals("bearer", response.tokenType());
        assertEquals("bearer", response.getTokenType());

        GOGApi api = GOGApi.getInstance();
        api.setTokenResponse(response);

        assertEquals("token_xyz", api.getAccessToken());
        assertEquals("refresh_xyz", api.getRefreshToken());
        assertEquals("user_123", api.getUserId());
        assertTrue(api.isValidToken());
    }

    @Test
    void testRefreshTokenWithoutToken() {
        GOGApi api = GOGApi.getInstance();
        api.setTokenResponse(new TokenResponse(null, 0, null, null, null));
        assertNull(api.refreshAccessToken());
        assertFalse(api.isValidToken());
    }
}
