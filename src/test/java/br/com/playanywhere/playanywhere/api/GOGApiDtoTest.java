package br.com.playanywhere.playanywhere.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GOGApiDtoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testTokenResponseDeserialization() throws Exception {
        String json = "{\"access_token\":\"dummy-token-123\",\"expires_in\":3600}";
        TokenResponse response = mapper.readValue(json, TokenResponse.class);

        assertNotNull(response);
        assertEquals("dummy-token-123", response.getAccessToken());
        assertEquals(3600, response.getExpiresIn());
    }

    @Test
    void testLibraryProductsResponseDeserialization() throws Exception {
        String json = """
            {
              "products": [
                {
                  "product": {
                    "id": 12345,
                    "title": "The Witcher 3: Wild Hunt",
                    "slug": "the-witcher-3-wild-hunt",
                    "isInStock": true,
                    "rating": 4.8,
                    "price": {
                      "base": 3999,
                      "final": 1999,
                      "discount": 50
                    },
                    "gallery": [
                      {
                        "imagePath": "/images/cover.jpg",
                        "imageHash": "abc123hash",
                        "imageFormat": "jpg"
                      }
                    ],
                    "developer": ["CD PROJEKT RED"],
                    "publisher": ["CD PROJEKT RED"]
                  }
                }
              ]
            }
            """;

        LibraryProductsResponse response = mapper.readValue(json, LibraryProductsResponse.class);

        assertNotNull(response);
        assertNotNull(response.getProducts());
        assertEquals(1, response.getProducts().size());

        ProductWrapper productWrapper = response.getProducts().get(0);
        assertNotNull(productWrapper);

        ProductData productData = productWrapper.getProduct();
        assertNotNull(productData);
        assertEquals(12345, productData.getId());
        assertEquals("The Witcher 3: Wild Hunt", productData.getTitle());
        assertEquals("the-witcher-3-wild-hunt", productData.getSlug());
        assertTrue(productData.isInStock());
        assertEquals(4.8, productData.getRating(), 0.001);

        assertNotNull(productData.getPrice());
        assertEquals(3999, productData.getPrice().getBase());
        assertEquals(1999, productData.getPrice().getFinal());
        assertEquals(50, productData.getPrice().getDiscount());

        assertNotNull(productData.getGallery());
        assertEquals(1, productData.getGallery().size());
        GalleryImageWrapper galleryImage = productData.getGallery().get(0);
        assertEquals("/images/cover.jpg", galleryImage.getImagePath());
        assertEquals("abc123hash", galleryImage.getImageHash());
        assertEquals("jpg", galleryImage.getImageFormat());
    }

    @Test
    void testFlatLibraryProductsResponseDeserialization() throws Exception {
        String json = """
            {
              "products": [
                {
                  "id": 1207660413,
                  "title": "Shadowrun Returns",
                  "slug": "shadowrun_returns",
                  "image": "//images-2.gog.com/6c35ecb988f57725cc0f385acf860241082da16eda9fab66115f4da883dae3d1",
                  "category": "Role-playing",
                  "rating": 43.0
                }
              ]
            }
            """;

        LibraryProductsResponse response = mapper.readValue(json, LibraryProductsResponse.class);

        assertNotNull(response);
        assertNotNull(response.getProducts());
        assertEquals(1, response.getProducts().size());

        ProductWrapper productWrapper = response.getProducts().get(0);
        assertNotNull(productWrapper);

        ProductData productData = productWrapper.getProduct();
        assertNotNull(productData);
        assertEquals(1207660413, productData.getId());
        assertEquals("Shadowrun Returns", productData.getTitle());
        assertEquals("shadowrun_returns", productData.getSlug());
        assertEquals("//images-2.gog.com/6c35ecb988f57725cc0f385acf860241082da16eda9fab66115f4da883dae3d1", productData.getImage());
        assertEquals("Role-playing", productData.getCategory());
        assertEquals(43.0, productData.getRating(), 0.001);
    }

    @Test
    void testLibraryProductsResponsePaginationFields() throws Exception {
        String json = """
            {
              "page": 1,
              "totalPages": 3,
              "totalProducts": 150,
              "totalGames": 140,
              "totalMovies": 10,
              "products": []
            }
            """;

        LibraryProductsResponse response = mapper.readValue(json, LibraryProductsResponse.class);

        assertNotNull(response);
        assertEquals(1, response.getPage());
        assertEquals(3, response.getTotalPages());
        assertEquals(150, response.getTotalProducts());
        assertEquals(0, response.getProducts().size());
    }

    @Test
    void testNormalizeImageUrl() {
        assertNull(GOGApi.normalizeImageUrl(null));
        assertNull(GOGApi.normalizeImageUrl(""));
        assertNull(GOGApi.normalizeImageUrl("   "));

        // Protocol-relative URLs without extension
        String relativeUrl = "//images-1.gog-statics.com/3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b";
        assertEquals("https://images-1.gog-statics.com/3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b_product_tile_256_2x.jpg",
                GOGApi.normalizeImageUrl(relativeUrl));

        // HTTP to HTTPS
        String httpUrl = "http://images-2.gog.com/6c35ecb988f57725cc0f385acf860241082da16eda9fab66115f4da883dae3d1";
        assertEquals("https://images-2.gog.com/6c35ecb988f57725cc0f385acf860241082da16eda9fab66115f4da883dae3d1_product_tile_256_2x.jpg",
                GOGApi.normalizeImageUrl(httpUrl));

        // Raw hash
        String rawHash = "3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b";
        assertEquals("https://images-1.gog-statics.com/3910c22fa45e438c82669e46a7ea4ef11357f89fe4cfa228189c44567431e72b_product_tile_256_2x.jpg",
                GOGApi.normalizeImageUrl(rawHash));

        // URL with already valid extension
        String urlWithExt = "https://images-1.gog-statics.com/test_image.jpg";
        assertEquals("https://images-1.gog-statics.com/test_image.jpg",
                GOGApi.normalizeImageUrl(urlWithExt));

        // URL with existing tile format
        String tileUrl = "https://images-1.gog-statics.com/abc_product_tile_256_2x.jpg";
        assertEquals("https://images-1.gog-statics.com/abc_product_tile_256_2x.jpg",
                GOGApi.normalizeImageUrl(tileUrl));
    }

    @Test
    void testStandaloneInstallerFileRecord() {
        GOGApi.StandaloneInstallerFile file = new GOGApi.StandaloneInstallerFile(
                "setup_game_1.0",
                "Game (Part 1 of 1)",
                "1.0",
                1024L * 1024L * 500L,
                "https://embed.gog.com/downlink/game/en1installer0",
                "windows"
        );

        assertEquals("setup_game_1.0", file.id());
        assertEquals("Game (Part 1 of 1)", file.name());
        assertEquals("1.0", file.version());
        assertEquals(524288000L, file.totalSize());
        assertEquals("https://embed.gog.com/downlink/game/en1installer0", file.downlinkUrl());
        assertEquals("windows", file.os());
    }
}
