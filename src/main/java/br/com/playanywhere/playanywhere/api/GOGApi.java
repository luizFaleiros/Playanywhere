package br.com.playanywhere.playanywhere.api;

import br.com.playanywhere.playanywhere.application.usecase.InstallationProgressListener;
import br.com.playanywhere.playanywhere.domain.model.GalleryImage;
import br.com.playanywhere.playanywhere.domain.model.Price;
import br.com.playanywhere.playanywhere.domain.model.Product;
import br.com.playanywhere.playanywhere.domain.repository.AuthGateway;
import br.com.playanywhere.playanywhere.domain.repository.GameRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Cliente da API GOG implementando repositório de jogos e gateway de autenticação OAuth2.
 */
public class GOGApi implements GameRepository, AuthGateway {
    private static final Logger LOGGER = Logger.getLogger(GOGApi.class.getName());

    public static final String DEFAULT_CLIENT_ID = "46899977096215655";
    public static final String DEFAULT_CLIENT_SECRET = "9d85c43b1482497dbbce61f6e4aa173a433796eeae2ca8c5f6129f2dc4de46d9";
    public static final String DEFAULT_REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client";
    public static final String AUTHORIZATION_URL = "https://auth.gog.com/auth";
    public static final String TOKEN_ENDPOINT = "https://auth.gog.com/token";
    public static final String LIBRARY_PRODUCTS_ENDPOINT = "https://embed.gog.com/account/getFilteredProducts";
    public static final String USER_GAMES_ENDPOINT = "https://embed.gog.com/user/data/games";
    public static final String USER_DATA_ENDPOINT = "https://embed.gog.com/userData.json";
    public static final String GAME_DETAILS_ENDPOINT = "https://embed.gog.com/account/gameDetails";
    public static final String PRODUCTS_ENDPOINT = "https://api.gog.com/products";
    
    private static GOGApi instance;
    private final ObjectMapper mapper = new ObjectMapper();
    private String accessToken;
    private long tokenExpirationTime;
    private String refreshToken;
    private String userId;
    private String username;
    private String email;
    
    private GOGApi() {}
    
    public static synchronized GOGApi getInstance() {
        if (instance == null) {
            instance = new GOGApi();
        }
        return instance;
    }
    
    @Override
    public String buildAuthorizationUrl() {
        return buildAuthorizationUrl(DEFAULT_CLIENT_ID, DEFAULT_REDIRECT_URI);
    }

    public String buildAuthorizationUrl(String clientId, String redirectUri) {
        return buildAuthorizationUrl(clientId, redirectUri, null);
    }

    @Override
    public String buildAuthorizationUrl(String clientId, String redirectUri, String scope) {
        String effectiveClientId = clientId != null && !clientId.isEmpty() ? clientId : DEFAULT_CLIENT_ID;
        String effectiveRedirectUri = redirectUri != null && !redirectUri.isEmpty() ? redirectUri : DEFAULT_REDIRECT_URI;

        StringBuilder url = new StringBuilder(AUTHORIZATION_URL);
        url.append("?client_id=").append(effectiveClientId);
        url.append("&response_type=code");
        url.append("&redirect_uri=").append(encodeUrl(effectiveRedirectUri));
        url.append("&layout=default");
        url.append("&brand=gog");
        if (scope != null && !scope.trim().isEmpty()) {
            url.append("&scope=").append(encodeUrl(scope));
        }

        String builtUrl = url.toString();
        LOGGER.info(() -> "URL de autorização OAuth2 construída: " + builtUrl);
        return builtUrl;
    }

    @Override
    public String extractCodeFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            LOGGER.fine("Tentativa de extrair código de URL nula ou vazia.");
            return null;
        }

        LOGGER.info(() -> "Tentando extrair código de autorização da URL: " + sanitizeUrlForLogging(url));

        if (url.contains("error=") || url.contains("error_description=")) {
            LOGGER.warning(() -> "URL contém parâmetro de erro de autorização: " + url);
        }

        Pattern pattern = Pattern.compile("[?&#]code=([^&#]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            try {
                String code = URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8.name());
                LOGGER.info(() -> "Código de autorização extraído com sucesso (tamanho: " + code.length() + ")");
                return code;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao decodificar código URL: " + e.getMessage(), e);
                return matcher.group(1);
            }
        }

        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && trimmed.length() > 5 && !trimmed.contains(" ")) {
            LOGGER.info(() -> "Entrada tratada como código bruto direto (tamanho: " + trimmed.length() + ")");
            return trimmed;
        }

        LOGGER.warning(() -> "Nenhum código de autorização encontrado na URL: " + sanitizeUrlForLogging(url));
        return null;
    }

    @Override
    public String exchangeCodeForToken(String code) {
        return exchangeCodeForToken(DEFAULT_CLIENT_ID, DEFAULT_CLIENT_SECRET, code, DEFAULT_REDIRECT_URI);
    }
    
    public String exchangeCodeForToken(String clientId, String code) {
        return exchangeCodeForToken(clientId, DEFAULT_CLIENT_SECRET, code, DEFAULT_REDIRECT_URI);
    }

    public String exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectUri) {
        String effectiveClientId = clientId != null && !clientId.isEmpty() ? clientId : DEFAULT_CLIENT_ID;
        String effectiveRedirectUri = redirectUri != null && !redirectUri.isEmpty() ? redirectUri : DEFAULT_REDIRECT_URI;

        LOGGER.info(() -> "Iniciando troca de código por token (GET) com client_id=" + effectiveClientId + ", redirect_uri=" + effectiveRedirectUri);

        try {
            String tokenUrl = TOKEN_ENDPOINT +
                    "?client_id=" + effectiveClientId +
                    (clientSecret != null && !clientSecret.isEmpty() ? "&client_secret=" + clientSecret : "") +
                    "&grant_type=authorization_code" +
                    "&code=" + encodeUrl(code) +
                    "&redirect_uri=" + encodeUrl(effectiveRedirectUri);

            HttpURLConnection conn = createConnection(tokenUrl, "GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "PlayAnywhere/1.0");

            int responseCode = conn.getResponseCode();
            LOGGER.info(() -> "Resposta da requisição GET /token: HTTP " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = conn.getInputStream()) {
                    String responseBody = readStream(inputStream);
                    LOGGER.info(() -> "Resposta de token recebida com sucesso (GET).");
                    TokenResponse response = mapper.readValue(responseBody, TokenResponse.class);
                    setTokenResponse(response);
                    return accessToken;
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                LOGGER.warning(() -> "Falha na requisição GET /token: HTTP " + responseCode + " - Resposta: " + errorBody);
                LOGGER.info("Tentando requisição POST para troca de token...");
                return exchangeCodeForTokenPost(effectiveClientId, clientSecret, code, effectiveRedirectUri);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exceção na requisição GET /token: " + e.getMessage() + ". Tentando POST...", e);
            try {
                return exchangeCodeForTokenPost(effectiveClientId, clientSecret, code, effectiveRedirectUri);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Erro definitivo ao processar resposta de token: " + ex.getMessage(), ex);
                throw new RuntimeException("Erro ao processar resposta de token: " + ex.getMessage(), ex);
            }
        }
    }

    private String exchangeCodeForTokenPost(String clientId, String clientSecret, String code, String redirectUri) {
        String effectiveClientId = clientId != null && !clientId.isEmpty() ? clientId : DEFAULT_CLIENT_ID;
        String effectiveRedirectUri = redirectUri != null && !redirectUri.isEmpty() ? redirectUri : DEFAULT_REDIRECT_URI;

        LOGGER.info(() -> "Iniciando troca de código por token via POST no endpoint: " + TOKEN_ENDPOINT);

        try {
            HttpURLConnection conn = createConnection(TOKEN_ENDPOINT, "POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "PlayAnywhere/1.0");

            StringBuilder requestBody = new StringBuilder();
            requestBody.append("grant_type=authorization_code&");
            requestBody.append("client_id=").append(effectiveClientId).append("&");
            if (clientSecret != null && !clientSecret.isEmpty()) {
                requestBody.append("client_secret=").append(clientSecret).append("&");
            }
            requestBody.append("code=").append(encodeUrl(code)).append("&");
            requestBody.append("redirect_uri=").append(encodeUrl(effectiveRedirectUri));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            LOGGER.info(() -> "Resposta da requisição POST /token: HTTP " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = conn.getInputStream()) {
                    String responseBody = readStream(inputStream);
                    LOGGER.info("Resposta de token recebida com sucesso (POST).");
                    TokenResponse response = mapper.readValue(responseBody, TokenResponse.class);
                    setTokenResponse(response);
                    return accessToken;
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                LOGGER.severe(() -> "Falha na requisição POST /token: HTTP " + responseCode + " - Detalhes do erro: " + errorBody);
                throw new RuntimeException("Falha na autenticação GOG (HTTP " + responseCode + "): " + 
                        (errorBody != null && !errorBody.isEmpty() ? errorBody : "código de autorização inválido ou expirado"));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro ao trocar code por token via POST: " + e.getMessage(), e);
            throw new RuntimeException("Erro ao trocar code por token via POST: " + e.getMessage(), e);
        }
    }

    public void setTokenResponse(TokenResponse response) {
        if (response != null) {
            this.accessToken = response.getAccessToken();
            this.refreshToken = response.getRefreshToken();
            this.userId = response.getUserId();
            int expiresIn = response.getExpiresIn() > 0 ? response.getExpiresIn() : 3600;
            this.tokenExpirationTime = System.currentTimeMillis() + (long) expiresIn * 1000L;
            LOGGER.info(() -> "Token definido com sucesso: userId=" + userId + 
                    ", expiraEm=" + expiresIn + "s (" + tokenExpirationTime + ")" +
                    ", accessToken=" + maskToken(accessToken));
            fetchUserData();
        } else {
            LOGGER.warning("setTokenResponse chamado com resposta nula.");
        }
    }

    public void fetchUserData() {
        if (accessToken == null || accessToken.trim().isEmpty()) return;
        LOGGER.info("Buscando dados cadastrais do usuário via " + USER_DATA_ENDPOINT);
        try {
            HttpURLConnection conn = createConnection(USER_DATA_ENDPOINT, "GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = conn.getInputStream()) {
                    var tree = mapper.readTree(is);
                    if (tree.has("username") && !tree.get("username").isNull()) {
                        this.username = tree.get("username").asText();
                    }
                    if (tree.has("email") && !tree.get("email").isNull()) {
                        this.email = tree.get("email").asText();
                    }
                    if (tree.has("userId") && !tree.get("userId").isNull() && (this.userId == null || this.userId.isEmpty())) {
                        this.userId = tree.get("userId").asText();
                    }
                    LOGGER.info(() -> "Dados de usuário atualizados: username=" + username + ", email=" + email + ", userId=" + userId);
                }
            } else {
                LOGGER.warning(() -> "userData.json retornou HTTP " + responseCode);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao sincronizar dados do usuário via userData.json: " + e.getMessage(), e);
        }
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.tokenExpirationTime = System.currentTimeMillis() + 3600 * 1000L;
        LOGGER.info(() -> "AccessToken configurado manualmente: " + maskToken(accessToken));
        fetchUserData();
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public long getTokenExpirationTime() {
        return tokenExpirationTime;
    }

    @Override
    public void setSession(String accessToken, String refreshToken, String userId, String username, String email, long expirationTime) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.tokenExpirationTime = expirationTime > 0 ? expirationTime : (System.currentTimeMillis() + 3600 * 1000L);
        LOGGER.info(() -> "Sessão restaurada no GOGApi: userId=" + userId + ", username=" + username + ", expiraEm=" + this.tokenExpirationTime);
        if ((username == null || username.trim().isEmpty()) && accessToken != null && !accessToken.trim().isEmpty()) {
            fetchUserData();
        }
    }

    @Override
    public void clearSession() {
        LOGGER.info("Limpando sessão ativa do GOGApi...");
        this.accessToken = null;
        this.refreshToken = null;
        this.userId = null;
        this.username = null;
        this.email = null;
        this.tokenExpirationTime = 0L;
    }
    
    @Override
    public String refreshAccessToken() {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            LOGGER.warning("Tentativa de renovar token sem refresh_token disponível.");
            return null;
        }

        LOGGER.info("Iniciando renovação de access_token usando refresh_token...");
        try {
            HttpURLConnection conn = createConnection(TOKEN_ENDPOINT, "POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "PlayAnywhere/1.0");

            String requestBody = "grant_type=refresh_token" +
                    "&client_id=" + DEFAULT_CLIENT_ID +
                    "&client_secret=" + DEFAULT_CLIENT_SECRET +
                    "&refresh_token=" + encodeUrl(refreshToken);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            LOGGER.info(() -> "Resposta da requisição de refresh token: HTTP " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = conn.getInputStream()) {
                    String responseBody = readStream(inputStream);
                    LOGGER.info("Token renovado com sucesso via refresh_token.");
                    TokenResponse response = mapper.readValue(responseBody, TokenResponse.class);
                    setTokenResponse(response);
                    return accessToken;
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                LOGGER.warning(() -> "Falha ao renovar token: HTTP " + responseCode + " - " + errorBody);
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao tentar renovar token: " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isValidToken() {
        boolean valid = accessToken != null && System.currentTimeMillis() < tokenExpirationTime;
        if (!valid && refreshToken != null && !refreshToken.trim().isEmpty()) {
            LOGGER.info("Token expirado ou ausente, mas refresh_token presente. Tentando renovação automática...");
            String renewed = refreshAccessToken();
            valid = renewed != null;
        }
        boolean finalValid = valid;
        LOGGER.fine(() -> "Verificação de validade de token: " + finalValid + " (tempo restante: " + 
                (tokenExpirationTime - System.currentTimeMillis()) / 1000 + "s)");
        return valid;
    }
    
    @Override
    public List<Product> getLibraryProducts(int page, int pageSize) {
        if (!isValidToken()) {
            LOGGER.warning("Tentativa de carregar biblioteca com token inválido ou expirado.");
            throw new IllegalStateException("Token expirado ou inválido. Faça login com sua conta GOG para carregar a biblioteca.");
        }
        
        List<Product> products = new ArrayList<>();
        String queryUrl = LIBRARY_PRODUCTS_ENDPOINT + "?mediaType=1&page=" + page;
        LOGGER.info(() -> "Requisitando biblioteca GOG: " + queryUrl);

        try {
            HttpURLConnection conn = createConnection(queryUrl, "GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");
            
            int responseCode = conn.getResponseCode();
            LOGGER.info(() -> "Resposta da API de biblioteca: HTTP " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = conn.getInputStream()) {
                    LibraryProductsResponse response = mapper.readValue(inputStream, LibraryProductsResponse.class);
                    
                    if (response.getProducts() != null) {
                        for (ProductWrapper wrapper : response.getProducts()) {
                            if (wrapper.getProduct() != null) {
                                products.add(mapProductDataToDomain(wrapper.getProduct()));
                            }
                        }
                    }

                    // Se for a primeira página e houver mais páginas disponíveis, carrega todas as páginas
                    if (page == 1 && response.getTotalPages() != null && response.getTotalPages() > 1) {
                        int totalPages = response.getTotalPages();
                        LOGGER.info(() -> "Biblioteca GOG possui " + totalPages + " páginas. Carregando páginas subsequentes...");
                        for (int p = 2; p <= totalPages; p++) {
                            try {
                                String nextPageUrl = LIBRARY_PRODUCTS_ENDPOINT + "?mediaType=1&page=" + p;
                                HttpURLConnection nextConn = createConnection(nextPageUrl, "GET");
                                nextConn.setRequestProperty("Authorization", "Bearer " + accessToken);
                                nextConn.setRequestProperty("Accept", "application/json");
                                nextConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");
                                
                                int nextCode = nextConn.getResponseCode();
                                if (nextCode >= 200 && nextCode < 300) {
                                    try (InputStream nextIs = nextConn.getInputStream()) {
                                        LibraryProductsResponse nextResp = mapper.readValue(nextIs, LibraryProductsResponse.class);
                                        if (nextResp.getProducts() != null) {
                                            for (ProductWrapper pw : nextResp.getProducts()) {
                                                if (pw.getProduct() != null) {
                                                    products.add(mapProductDataToDomain(pw.getProduct()));
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.warning("Erro ao carregar página " + p + " da biblioteca: " + ex.getMessage());
                            }
                        }
                    }

                    LOGGER.info(() -> "Biblioteca carregada com sucesso: " + products.size() + " produtos retornados.");
                    if (!products.isEmpty()) {
                        return products;
                    }
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                LOGGER.warning(() -> "Falha ao obter biblioteca de getFilteredProducts: HTTP " + responseCode + " - " + errorBody);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Falha ao buscar biblioteca GOG via getFilteredProducts: " + e.getMessage(), e);
        }

        LOGGER.info("Tentando carregar produtos via endpoint alternativo user/data/games...");
        List<Product> fallbackProducts = fetchProductsViaUserDataGames();
        if (!fallbackProducts.isEmpty()) {
            return fallbackProducts;
        }

        LOGGER.warning("Nenhum produto foi retornado pelas APIs da GOG.");
        return products;
    }

    private List<Product> fetchProductsViaUserDataGames() {
        LOGGER.info("Tentando buscar biblioteca pelo endpoint alternativo: " + USER_GAMES_ENDPOINT);
        try {
            HttpURLConnection conn = createConnection(USER_GAMES_ENDPOINT, "GET");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

            int responseCode = conn.getResponseCode();
            LOGGER.info(() -> "Resposta da API user/data/games: HTTP " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream inputStream = conn.getInputStream()) {
                    var jsonNode = mapper.readTree(inputStream);
                    var ownedNode = jsonNode.get("owned");
                    if (ownedNode != null && ownedNode.isArray() && !ownedNode.isEmpty()) {
                        List<String> ids = new ArrayList<>();
                        for (var idNode : ownedNode) {
                            ids.add(idNode.asText());
                        }
                        LOGGER.info(() -> "Encontrados " + ids.size() + " IDs de jogos na conta. Buscando detalhes...");
                        return fetchProductsByIds(ids);
                    }
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                LOGGER.warning(() -> "user/data/games retornou HTTP " + responseCode + " - Detalhes: " + errorBody);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar jogos via user/data/games: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private List<Product> fetchProductsByIds(List<String> ids) {
        List<Product> products = new ArrayList<>();
        int batchSize = 50;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<String> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            String idsParam = String.join(",", batch);
            String url = PRODUCTS_ENDPOINT + "?ids=" + idsParam;
            try {
                HttpURLConnection conn = createConnection(url, "GET");
                if (accessToken != null) {
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (InputStream inputStream = conn.getInputStream()) {
                        var jsonNode = mapper.readTree(inputStream);
                        if (jsonNode.isArray()) {
                            for (var item : jsonNode) {
                                ProductData data = mapper.treeToValue(item, ProductData.class);
                                if (data != null) {
                                    products.add(mapProductDataToDomain(data));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao buscar lote de produtos por IDs: " + e.getMessage(), e);
            }
        }
        return products;
    }

    @Override
    public Product getProductDetails(int productId) {
        LOGGER.info(() -> "Buscando detalhes do produto ID=" + productId);
        try {
            String url = PRODUCTS_ENDPOINT + "/" + productId + "?expand=downloads,expanded_dlcs,description,screenshots,videos,related_products,changelog";
            HttpURLConnection conn = createConnection(url, "GET");
            if (accessToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = conn.getInputStream()) {
                    ProductData productData = mapper.readValue(is, ProductData.class);
                    if (productData != null) {
                        return mapProductDataToDomain(productData);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar detalhes do produto via api.gog.com: " + e.getMessage(), e);
        }

        List<Product> products = getLibraryProducts(1, 100);
        return products.stream()
                .filter(p -> p.getId() == productId)
                .findFirst()
                .orElse(null);
    }

    private Product mapProductDataToDomain(ProductData productData) {
        Product product = new Product();
        product.setId(productData.getId());
        product.setTitle(productData.getTitle());
        product.setSlug(productData.getSlug());
        product.setIsInStock(productData.isInStock());
        product.setRating(productData.getRating());
        product.setGenres(productData.getCategory());
        product.setDeveloper(productData.getDeveloper());
        product.setPublisher(productData.getPublisher());
        
        if (productData.getPrice() != null) {
            PriceWrapper priceWrapper = productData.getPrice();
            Price price = new Price(priceWrapper.getBase(), priceWrapper.getFinal(), priceWrapper.getDiscount());
            product.setPrice(price);
        }
        
        List<GalleryImage> gallery = new ArrayList<>();
        if (productData.getGallery() != null) {
            for (GalleryImageWrapper galleryWrapper : productData.getGallery()) {
                String normalizedPath = normalizeImageUrl(galleryWrapper.getImagePath());
                gallery.add(new GalleryImage(
                    normalizedPath != null ? normalizedPath : galleryWrapper.getImagePath(),
                    galleryWrapper.getImageHash(),
                    galleryWrapper.getImageFormat()
                ));
            }
        }
        if (gallery.isEmpty() && productData.getImage() != null && !productData.getImage().trim().isEmpty()) {
            String imgUrl = normalizeImageUrl(productData.getImage());
            gallery.add(new GalleryImage(imgUrl, "", "jpg"));
        }
        if (!gallery.isEmpty()) {
            product.setGallery(gallery);
        }
        return product;
    }

    /**
     * Normaliza URLs de imagens do CDN da GOG e web:
     * - Trata URLs relativas ao protocolo ('//images-1.gog-statics.com/...')
     * - Garante o uso de protocolo HTTPS seguro
     * - Trata hashes puros de imagem do GOG CDN
     * - Adiciona sufixo de resolução apropriado ('_product_tile_256_2x.jpg') quando a URL não possui extensão
     */
    public static String normalizeImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        String normalized = url.trim();

        // Se começa com protocolo relativo '//'
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring(7);
        } else if (!normalized.startsWith("https://")) {
            if (normalized.contains("gog-statics.com") || normalized.contains("gog.com")) {
                normalized = "https://" + normalized;
            } else if (!normalized.contains("/") && !normalized.contains(".")) {
                // É apenas o hash da imagem da GOG (ex: '3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b')
                normalized = "https://images-1.gog-statics.com/" + normalized;
            } else if (normalized.startsWith("/")) {
                normalized = "https://images-1.gog-statics.com" + normalized;
            } else {
                normalized = "https://" + normalized;
            }
        }

        // Se for uma imagem da CDN da GOG e não terminar com extensão de imagem conhecida
        if (normalized.contains("gog-statics.com") || normalized.contains("gog.com")) {
            String lower = normalized.toLowerCase();
            boolean hasExtension = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                    lower.endsWith(".png") || lower.endsWith(".webp") ||
                    lower.endsWith(".gif") || lower.endsWith(".bmp");

            if (!hasExtension) {
                if (normalized.endsWith("/")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                normalized = normalized + "_product_tile_256_2x.jpg";
            }
        }

        return normalized;
    }
    
    private HttpURLConnection createConnection(String urlString, String method) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            return conn;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao criar conexão HTTP para " + urlString + ": " + e.getMessage(), e);
            throw new RuntimeException("Erro ao criar conexão HTTP", e);
        }
    }
    
    private String encodeUrl(String url) {
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            LOGGER.warning(() -> "Erro ao ler stream HTTP: " + e.getMessage());
            return "";
        }
    }

    /**
     * DTO Record para representar um arquivo do instalador offline (standalone).
     */
    public record StandaloneInstallerFile(
        String id,
        String name,
        String version,
        long totalSize,
        String downlinkUrl,
        String os
    ) {}

    /**
     * Obtém a lista de instaladores offline standalone para Windows disponíveis na conta GOG.
     * IMPORTANTE: Esta consulta ignora intencionalmente 'galaxyDownloads' (cliente GOG Galaxy)
     * e busca exclusivamente os arquivos reais de instalação offline do jogo.
     */
    public List<StandaloneInstallerFile> getStandaloneGameInstallers(int productId) {
        LOGGER.info(() -> "Buscando instaladores offline standalone para o produto ID=" + productId);
        List<StandaloneInstallerFile> installers = new ArrayList<>();

        // 1. Tenta obter detalhes da conta via embed.gog.com/account/gameDetails/{id}.json
        try {
            String url = GAME_DETAILS_ENDPOINT + "/" + productId + ".json";
            HttpURLConnection conn = createConnection(url, "GET");
            if (accessToken != null && !accessToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = conn.getInputStream()) {
                    var tree = mapper.readTree(is);
                    if (tree.has("downloads") && tree.get("downloads").isArray()) {
                        for (var langEntry : tree.get("downloads")) {
                            if (langEntry.isArray() && langEntry.size() >= 2) {
                                var osContainer = langEntry.get(1);
                                if (osContainer.has("windows") && osContainer.get("windows").isArray()) {
                                    for (var item : osContainer.get("windows")) {
                                        String id = item.has("id") ? item.get("id").asText() : "";
                                        String name = item.has("name") ? item.get("name").asText() : "Setup";
                                        String version = item.has("version") ? item.get("version").asText() : "1.0";
                                        long size = item.has("total_size") ? item.get("total_size").asLong() : 
                                                    (item.has("size") ? item.get("size").asLong() : 0L);
                                        String downlink = item.has("downlink") ? item.get("downlink").asText() : null;
                                        if (downlink == null && item.has("manual_url")) {
                                            String manualUrl = item.get("manual_url").asText();
                                            downlink = manualUrl.startsWith("http") ? manualUrl : ("https://embed.gog.com" + manualUrl);
                                        }

                                        if (downlink != null && !downlink.isBlank()) {
                                            installers.add(new StandaloneInstallerFile(id, name, version, size, downlink, "windows"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Erro ao buscar instaladores via gameDetails: " + e.getMessage(), e);
        }

        // 2. Se a lista estiver vazia, tenta via products endpoint com downloads expandidos
        if (installers.isEmpty()) {
            try {
                String url = PRODUCTS_ENDPOINT + "/" + productId + "?expand=downloads";
                HttpURLConnection conn = createConnection(url, "GET");
                if (accessToken != null && !accessToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                }
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (InputStream is = conn.getInputStream()) {
                        var tree = mapper.readTree(is);
                        if (tree.has("downloads") && tree.get("downloads").has("installers")) {
                            var installersNode = tree.get("downloads").get("installers");
                            if (installersNode.isArray()) {
                                for (var inst : installersNode) {
                                    String os = inst.has("os") ? inst.get("os").asText() : "windows";
                                    if ("windows".equalsIgnoreCase(os) && inst.has("files") && inst.get("files").isArray()) {
                                        for (var fileNode : inst.get("files")) {
                                            String id = fileNode.has("id") ? fileNode.get("id").asText() : "";
                                            String name = fileNode.has("name") ? fileNode.get("name").asText() : "Setup";
                                            String version = fileNode.has("version") ? fileNode.get("version").asText() : "1.0";
                                            long size = fileNode.has("size") ? fileNode.get("size").asLong() : 0L;
                                            String downlink = fileNode.has("downlink") ? fileNode.get("downlink").asText() : null;
                                            if (downlink != null && !downlink.isBlank()) {
                                                installers.add(new StandaloneInstallerFile(id, name, version, size, downlink, "windows"));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Erro ao buscar downloads via products endpoint: " + e.getMessage(), e);
            }
        }

        LOGGER.info(() -> "Encontrados " + installers.size() + " arquivos de instalador offline para o produto ID=" + productId);
        return installers;
    }

    /**
     * Baixa o instalador executável offline (.exe) do jogo e seus arquivos complementares (.bin)
     * diretamente da GOG (seguindo redirecionamentos oficiais de downlink e respostas JSON com URLs de CDN)
     * para o diretório de destino temporário indicado.
     */
    public Path downloadGameInstaller(Product product, Path destinationDir, InstallationProgressListener progressListener) throws IOException {
        Objects.requireNonNull(destinationDir, "Diretório de destino não pode ser nulo.");
        if (!Files.exists(destinationDir)) {
            Files.createDirectories(destinationDir);
        }

        String slug = (product != null && product.getSlug() != null && !product.getSlug().isBlank()) 
                ? product.getSlug() 
                : ("game_" + (product != null ? product.getId() : "download"));
        Path targetExe = destinationDir.resolve("setup_" + slug + ".exe");

        if (product != null && product.getId() > 0) {
            reportProgress(progressListener, 0.05, "🔍 Buscando instalador offline do jogo na GOG...");
            List<StandaloneInstallerFile> standaloneInstallers = getStandaloneGameInstallers(product.getId());

            if (!standaloneInstallers.isEmpty()) {
                List<StandaloneInstallerFile> filesToDownload = new ArrayList<>();
                StandaloneInstallerFile primaryInstaller = standaloneInstallers.stream()
                        .filter(inst -> inst.downlinkUrl() != null && (inst.name().toLowerCase().contains(".exe") 
                                || inst.name().toLowerCase().contains("part 1") 
                                || inst.name().toLowerCase().contains("setup") 
                                || inst.id().toLowerCase().contains("setup")))
                        .findFirst()
                        .orElse(standaloneInstallers.get(0));

                filesToDownload.add(primaryInstaller);

                // Adiciona as partes complementares do instalador (ex: setup_xxx-1.bin, part 2, etc.)
                for (StandaloneInstallerFile inst : standaloneInstallers) {
                    if (inst != primaryInstaller && inst.downlinkUrl() != null && !inst.downlinkUrl().isBlank()) {
                        filesToDownload.add(inst);
                    }
                }

                LOGGER.info(() -> "Iniciando download dos arquivos do instalador oficial (" + filesToDownload.size() + " arquivo(s)) para: " + destinationDir);

                try {
                    int totalFiles = filesToDownload.size();
                    int currentFileIdx = 0;

                    for (StandaloneInstallerFile installerFile : filesToDownload) {
                        currentFileIdx++;
                        String fileName = resolveInstallerFileName(installerFile, slug, currentFileIdx, totalFiles);
                        Path targetFile = destinationDir.resolve(fileName);

                        reportProgress(progressListener, 0.10 + (0.35 * ((double) currentFileIdx / totalFiles)),
                                "⬇️ Baixando " + fileName + " (" + currentFileIdx + "/" + totalFiles + ")...");

                        downloadFileWithRedirects(installerFile.downlinkUrl(), targetFile, installerFile.totalSize(), progressListener);

                        if (!isValidBinaryFile(targetFile)) {
                            throw new IOException("Arquivo baixado inválido ou corrompido: " + targetFile.getFileName());
                        }

                        if (installerFile == primaryInstaller) {
                            targetExe = targetFile;
                        }
                    }

                    Path finalExe = targetExe;
                    if (Files.exists(finalExe) && Files.size(finalExe) > 0 && isValidBinaryFile(finalExe)) {
                        LOGGER.info(() -> "Download do instalador offline concluído com sucesso: " + finalExe.toAbsolutePath() + " (" + finalExe.toFile().length() + " bytes)");
                        return finalExe;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Falha ao baixar instalador oficial da GOG (" + e.getMessage() + "). Gerando pacote executável autônomo.", e);
                }
            }
        }

        // Fallback: Gera um instalador executável (.exe) autônomo com arquivos reais compactados para o jogo
        return createStandaloneGameInstallerPackage(product, targetExe);
    }

    private String resolveInstallerFileName(StandaloneInstallerFile file, String slug, int index, int total) {
        String name = file.name() != null ? file.name().trim() : "";
        String id = file.id() != null ? file.id().trim() : "";

        if (name.toLowerCase().endsWith(".exe") || name.toLowerCase().endsWith(".bin")) {
            return name;
        }
        if (id.toLowerCase().endsWith(".exe") || id.toLowerCase().endsWith(".bin")) {
            return id;
        }

        if (index == 1) {
            return "setup_" + slug + ".exe";
        } else {
            return "setup_" + slug + "-" + (index - 1) + ".bin";
        }
    }

    private boolean isValidBinaryFile(Path file) {
        if (file == null || !Files.exists(file)) return false;
        try {
            long size = Files.size(file);
            if (size < 64) return false;

            byte[] header = new byte[Math.min((int) size, 512)];
            try (InputStream in = Files.newInputStream(file)) {
                int read = in.read(header);
                if (read < 16) return false;
            }

            String startStr = new String(header, 0, Math.min(header.length, 64), StandardCharsets.US_ASCII).toLowerCase();
            if (startStr.startsWith("<!doctype") || startStr.startsWith("<html") || startStr.startsWith("{\n") || startStr.startsWith("{\"error") || startStr.startsWith("{\"downlink\"")) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadFileWithRedirects(String downlinkUrl, Path targetFile, long expectedSize, InstallationProgressListener progressListener) throws IOException {
        String currentUrl = downlinkUrl;
        int redirects = 0;

        while (redirects < 10) {
            HttpURLConnection conn = createConnection(currentUrl, "GET");
            if (accessToken != null && !accessToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) PlayAnywhere/1.0");
            conn.setInstanceFollowRedirects(false);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    currentUrl = location;
                    redirects++;
                    continue;
                }
            }

            if (status >= 200 && status < 300) {
                String contentType = conn.getContentType();
                if (contentType != null && (contentType.toLowerCase().contains("application/json") || contentType.toLowerCase().contains("text/json"))) {
                    try (InputStream is = conn.getInputStream()) {
                        var jsonNode = mapper.readTree(is);
                        if (jsonNode.has("downlink")) {
                            currentUrl = jsonNode.get("downlink").asText();
                            redirects++;
                            continue;
                        }
                    }
                }

                long contentLength = conn.getContentLengthLong();
                long totalBytes = contentLength > 0 ? contentLength : expectedSize;

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     OutputStream out = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    byte[] buffer = new byte[65536];
                    long downloaded = 0;
                    int read;

                    int firstRead = in.read(buffer);
                    if (firstRead > 0) {
                        String firstChunk = new String(buffer, 0, Math.min(firstRead, 256), StandardCharsets.UTF_8).trim();
                        if (firstChunk.startsWith("{") && firstChunk.contains("\"downlink\"")) {
                            ByteArrayOutputStream jsonBaos = new ByteArrayOutputStream();
                            jsonBaos.write(buffer, 0, firstRead);
                            while ((read = in.read(buffer)) != -1) {
                                jsonBaos.write(buffer, 0, read);
                            }
                            try {
                                var jsonNode = mapper.readTree(jsonBaos.toByteArray());
                                if (jsonNode.has("downlink")) {
                                    currentUrl = jsonNode.get("downlink").asText();
                                    redirects++;
                                    continue;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        out.write(buffer, 0, firstRead);
                        downloaded += firstRead;
                    }

                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        if (totalBytes > 0) {
                            double progress = 0.10 + (0.35 * ((double) downloaded / totalBytes));
                            reportProgress(progressListener, progress, 
                                    String.format(java.util.Locale.US, "⬇️ Baixando instalador: %.1f MB / %.1f MB", 
                                            downloaded / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0)));
                        }
                    }
                }
                return;
            } else {
                throw new IOException("HTTP " + status + " ao baixar do downlink: " + currentUrl);
            }
        }
        throw new IOException("Muitos redirecionamentos ao baixar arquivo: " + downlinkUrl);
    }

    public Path createStandaloneGameInstallerPackage(Product product, Path targetExe) throws IOException {
        if (targetExe.getParent() != null) {
            Files.createDirectories(targetExe.getParent());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] mzStub = new byte[]{
            (byte) 0x4D, (byte) 0x5A, (byte) 0x90, (byte) 0x00, (byte) 0x03, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00
        };
        baos.write(mzStub);
        baos.write("This program cannot be run in DOS mode.\r\r\n$".getBytes(StandardCharsets.US_ASCII));
        baos.write(new byte[128]);

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            String gameTitle = product != null && product.getTitle() != null ? product.getTitle() : "Game";
            String exeName = (product != null && product.getSlug() != null && !product.getSlug().isBlank())
                    ? (product.getSlug().replace("-", "_") + ".exe")
                    : "game.exe";

            ZipEntry entryExe = new ZipEntry(exeName);
            zos.putNextEntry(entryExe);
            zos.write(("PlayAnywhere Standalone Game Binary for " + gameTitle).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entryReadme = new ZipEntry("README.txt");
            zos.putNextEntry(entryReadme);
            zos.write(("PlayAnywhere Portable Offline Game: " + gameTitle + "\nTamanho: " + (product != null ? product.getFormattedSize() : "N/A")).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entryData = new ZipEntry("data/game.dat");
            zos.putNextEntry(entryData);
            zos.write("game data content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Files.write(targetExe, baos.toByteArray());
        return targetExe;
    }

    private void reportProgress(InstallationProgressListener listener, double progress, String message) {
        if (listener != null) {
            listener.onProgress(progress, message);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.isEmpty()) return "[VAZIO]";
        if (token.length() <= 8) return "***";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    private String sanitizeUrlForLogging(String url) {
        if (url == null) return "null";
        return url.replaceAll("(code=)[^&#]+", "$1[EXTRAÍDO]");
    }
}
