package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO Record para resposta de autenticação OAuth2 da GOG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") int expiresIn,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("user_id") String userId,
    @JsonProperty("token_type") String tokenType
) {
    public TokenResponse(String accessToken, int expiresIn) {
        this(accessToken, expiresIn, null, null, null);
    }

    public String getAccessToken() { return accessToken; }
    public int getExpiresIn() { return expiresIn; }
    public String getRefreshToken() { return refreshToken; }
    public String getUserId() { return userId; }
    public String getTokenType() { return tokenType; }
}
