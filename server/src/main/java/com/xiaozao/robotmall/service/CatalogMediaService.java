package com.xiaozao.robotmall.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.entity.Product;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Local provenance only: a reviewed image does not certify every specification. */
@Component
public class CatalogMediaService {
    public record Entry(String name, String image, String sourceUrl,
            String imageSourceUrl, String region, String checkedAt, String kind, String note) {}

    private final Map<String, Entry> entries;
    private static final Map<String, String> REFERENCE_MODELS = Map.ofEntries(
            Map.entry("/products/real/roborock-g20s.jpeg", "石头 G20S Ultra"),
            Map.entry("/products/real/dji-air3s.jpg", "大疆 Air 3S"),
            Map.entry("/products/real/ecovacs-t30-pro.jpg", "科沃斯 T30 Pro"),
            Map.entry("/products/real/dreame-x40-ultra.png", "追觅 X40 Ultra"),
            Map.entry("/products/real/dji-robomaster-s1.jpg", "大疆 RoboMaster S1"),
            Map.entry("/products/real/xiaomi-s20-plus.webp", "小米 Robot Vacuum S20+"),
            Map.entry("/products/real/narwal-freo-x.png", "云鲸 Freo X Ultra"),
            Map.entry("/products/real/narwal-z10-ultra.png", "云鲸 Freo Z10 Ultra"),
            Map.entry("/products/real/ecovacs-winbot-w3.webp", "科沃斯 WINBOT W3 OMNI"),
            Map.entry("/products/real/hobot-s7-pro.jpg", "HOBOT S7 Pro"),
            Map.entry("/products/real/beatbot-aquasense2-ultra.jpeg", "Beatbot AquaSense 2 Ultra"),
            Map.entry("/products/real/mammotion-luba2.jpg", "Mammotion LUBA 2 AWD"),
            Map.entry("/products/real/unitree-b2.webp", "宇树 B2"));

    public CatalogMediaService(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("catalog-media.json").getInputStream()) {
            List<Entry> records = mapper.readValue(input, new TypeReference<List<Entry>>() {});
            var indexed = new LinkedHashMap<String, Entry>();
            for (Entry entry : records) {
                if (indexed.put(entry.name(), entry) != null) {
                    throw new IOException("Duplicate catalog media: " + entry.name());
                }
            }
            entries = Map.copyOf(indexed);
        }
    }

    public void applySeed(Product product) {
        Entry entry = entries.get(product.name);
        if (entry == null) return;
        product.image = entry.image();
        product.sourceUrl = entry.sourceUrl();
    }

    public void migrateLegacyAssets(Product product, String legacyImage, String legacySource) {
        Entry entry = entries.get(product.name);
        if (entry == null || (!entry.image().equals(product.image)
                && !legacyImage.equals(product.image))) return;
        // Only migrate known old catalog values; never replace an administrator's upload.
        product.image = entry.image();
        if (legacySource.equals(product.sourceUrl)) product.sourceUrl = entry.sourceUrl();
    }

    public void describe(Product product) {
        // Never retain the old review badge after an administrator changes the image.
        product.imageKind = "UNREVIEWED";
        product.imageNote = "公开产品素材，型号图尚未逐项核验。";
        product.imageSourceUrl = null;
        product.imageCheckedAt = null;
        product.sourceRegion = null;
        Entry entry = entries.get(product.name);
        if (entry != null && entry.image().equals(product.image)) {
            product.imageKind = "OFFICIAL_VISUAL".equals(entry.kind()) ? "OFFICIAL_VISUAL" : "MODEL_PHOTO";
            product.imageNote = entry.note()!=null ? entry.note() : "对应型号官方产品图（" + entry.region() + "），颜色及套装以来源页面为准。";
            product.imageSourceUrl = entry.imageSourceUrl();
            product.imageCheckedAt = entry.checkedAt();
            product.sourceRegion = entry.region();
        } else if (product.image != null && product.image.matches(".*\\.svg(?:\\?.*)?$")) {
            product.imageKind = "ILLUSTRATION";
            product.imageNote = "配件/商品示意图，不代表具体型号的实物外观或包装内容。";
        } else if (REFERENCE_MODELS.containsKey(product.image == null ? "" : product.image)
                && !REFERENCE_MODELS.get(product.image).equals(product.name)) {
            product.imageKind = "REFERENCE";
            product.imageNote = "暂用 " + REFERENCE_MODELS.get(product.image)
                    + " 参考图，不是本型号实物图；本型号图片待补充。";
        } else if ("/products/real/unitree-go2.webp".equals(product.image)
                || "/products/real/unitree-g1.jpg".equals(product.image)) {
            product.imageKind = "REFERENCE";
            product.imageNote = "同系列参考图；Air/Pro/EDU 等配置不能仅凭外观区分，具体套装图待核验。";
        }
    }
}
