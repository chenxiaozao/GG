package com.xiaozao.robotmall;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.entity.Product;
import com.xiaozao.robotmall.service.CatalogMediaService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogMediaTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private Product product(String name, String image) {
        var product = new Product(); product.name = name; product.image = image; return product;
    }

    @Test void officialManifestHasUniqueLocalAssetsAndTraceableHttpsSources() throws Exception {
        try (var input = getClass().getResourceAsStream("/catalog-media.json")) {
            var entries = mapper.readValue(input, new TypeReference<List<CatalogMediaService.Entry>>() {});
            assertTrue(entries.size() >= 8);
            var names = new HashSet<String>();
            var images = new HashSet<String>();
            for (var entry : entries) {
                assertTrue(names.add(entry.name()));
                assertTrue(images.add(entry.image()));
                assertTrue(entry.image().startsWith("/products/real/"));
                assertFalse(entry.image().contains(".."));
                assertEquals("https", URI.create(entry.sourceUrl()).getScheme());
                assertEquals("https", URI.create(entry.imageSourceUrl()).getScheme());
                assertEquals("2026-09-17", entry.checkedAt());
                Path asset = Path.of("../web/public" + entry.image());
                assertTrue(Files.isRegularFile(asset), entry.image());
                assertTrue(Files.size(asset) > 1000);
            }
        }
    }

    @Test void reviewedModelGetsExactImageButChangingImageInvalidatesBadge() throws Exception {
        var service = new CatalogMediaService(mapper);
        var product = product("石头 Qrevo Curv", "/products/real/roborock-g20s.jpeg");
        service.applySeed(product); service.describe(product);
        assertEquals("MODEL_PHOTO", product.imageKind);
        assertTrue(product.sourceUrl.contains("/products/roborock-qrevo-curv"));
        assertNotNull(product.imageCheckedAt);
        product.image = "/products/custom.jpg";
        service.describe(product);
        assertEquals("UNREVIEWED", product.imageKind);
        assertNull(product.imageCheckedAt);
        assertNull(product.imageSourceUrl);
    }

    @Test void anotherModelsPictureAndSeriesVariantsAreClearlyReferenceImages() throws Exception {
        var service = new CatalogMediaService(mapper);
        var mini = product("大疆 Mini 4 Pro", "/products/real/dji-air3s.jpg");
        service.describe(mini);
        assertEquals("REFERENCE", mini.imageKind);
        assertTrue(mini.imageNote.contains("不是本型号实物图"));
        var go2 = product("宇树 Go2 EDU", "/products/real/unitree-go2.webp");
        service.describe(go2);
        assertEquals("REFERENCE", go2.imageKind);
        var baseModel = product("小米 Robot Vacuum S20+", "/products/real/xiaomi-s20-plus.webp");
        service.describe(baseModel);
        assertEquals("UNREVIEWED", baseModel.imageKind);
    }

    @Test void illustrationAndUnknownUploadNeverBecomeVerifiedPhotos() throws Exception {
        var service = new CatalogMediaService(mapper);
        var accessory = product("配件", "/products/battery.svg");
        service.describe(accessory);
        assertEquals("ILLUSTRATION", accessory.imageKind);
        var unknown = product("新增型号", null);
        service.describe(unknown);
        assertEquals("UNREVIEWED", unknown.imageKind);
        assertNull(unknown.sourceRegion);
    }

    @Test void legacyMigrationDoesNotReplaceAdministratorMediaOrSource() throws Exception {
        var service = new CatalogMediaService(mapper);
        var product = product("石头 Qrevo Curv", "/uploads/admin-curv.jpg");
        product.sourceUrl = "https://example.org/admin-reference";
        service.migrateLegacyAssets(product, "/products/real/roborock-g20s.jpeg", "https://old.example.org/");
        assertEquals("/uploads/admin-curv.jpg", product.image);
        assertEquals("https://example.org/admin-reference", product.sourceUrl);
        var legacy = product("石头 Qrevo Curv", "/products/real/roborock-g20s.jpeg");
        legacy.sourceUrl = "https://old.example.org/";
        service.migrateLegacyAssets(legacy, legacy.image, legacy.sourceUrl);
        assertEquals("/products/real/roborock-qrevo-curv-official.jpg", legacy.image);
        assertEquals("https://us.roborock.com/products/roborock-qrevo-curv", legacy.sourceUrl);
    }
}
