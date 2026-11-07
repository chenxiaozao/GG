package com.xiaozao.robotmall.controller;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import java.util.regex.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.xiaozao.robotmall.common.*;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import com.xiaozao.robotmall.service.AuthService;
import com.xiaozao.robotmall.service.AfterSalePolicy;
import com.xiaozao.robotmall.service.CatalogMediaService;

@RestController
@RequestMapping("/api")
public class ApiController {
    @org.springframework.beans.factory.annotation.Value("${app.avatar-dir:./data/avatars}")
    private String avatarDirectory;

    private final AuthService auth;
    private final UserRepository users;
    private final CategoryRepository cats;
    private final BrandRepository brands;
    private final ProductRepository products;
    private final CartItemRepository carts;
    private final FavoriteRepository favs;
    private final HistoryRepository histories;
    private final AddressRepository addresses;
    private final OrderEntityRepository orders;
    private final OrderItemRepository orderItems;
    private final ReviewRepository reviews;
    private final CompatibilityRepository compat;
    private final AfterSaleRequestRepository afterSales;
    private final CatalogMediaService media;

    public ApiController(
            AuthService a,
            UserRepository u,
            CategoryRepository c,
            BrandRepository b,
            ProductRepository p,
            CartItemRepository ca,
            FavoriteRepository f,
            HistoryRepository h,
            AddressRepository ad,
            OrderEntityRepository o,
            OrderItemRepository oi,
            ReviewRepository r,
            CompatibilityRepository co,
            AfterSaleRequestRepository as,
            CatalogMediaService cm) {
        auth = a;
        users = u;
        cats = c;
        brands = b;
        products = p;
        carts = ca;
        favs = f;
        histories = h;
        addresses = ad;
        orders = o;
        orderItems = oi;
        reviews = r;
        compat = co;
        afterSales = as;
        media = cm;
    }

    private User me(String t) {
        return auth.current(t);
    }

    private User admin(String t) {
        User u = me(t);
        if (!"ADMIN".equals(u.role)) {
            throw new BizException("无管理员权限");
        }
        return u;
    }

    private Product enrich(Product product) {
        if (product == null) return null;
        media.describe(product);
        if (product.categoryId != null) {
            product.categoryName = cats.findById(product.categoryId).map(x -> x.name).orElse("");
        }
        if (product.brandId != null) {
            product.brandName = brands.findById(product.brandId).map(x -> x.name).orElse("");
        }
        return product;
    }


    @GetMapping("/profile/summary")
    public ApiResponse<?> profileSummary(@RequestHeader("X-Token") String t) {
        User u = me(t);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cartCount", carts.findByUserId(u.id).stream().mapToInt(x -> x.quantity).sum());
        result.put("favoriteCount", favs.findByUserId(u.id).size());
        result.put("historyCount", histories.findByUserId(u.id).size());
        List<OrderEntity> userOrders = orders.findByUserId(u.id);
        result.put("orderCount", userOrders.size());
        result.put("pendingPaymentCount", userOrders.stream().filter(x -> "PENDING".equals(x.status)).count());
        result.put("pendingReceiptCount", userOrders.stream().filter(x -> "SHIPPED".equals(x.status)).count());
        result.put("recentOrders", userOrders.stream()
                .sorted(Comparator.comparing((OrderEntity x) -> x.createdAt).reversed())
                .limit(3).toList());

        List<Map<String, Object>> recentProducts = histories.findByUserId(u.id).stream()
                .sorted(Comparator.comparing((History x) -> x.viewedAt).reversed())
                .limit(4)
                .map(record -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("viewTime", record.viewedAt);
                    row.put("product", products.findById(record.productId).map(this::enrich).orElse(null));
                    return row;
                })
                .filter(row -> row.get("product") != null)
                .toList();
        result.put("recentProducts", recentProducts);
        return ApiResponse.ok(result);
    }

    @PutMapping("/profile/theme")
    public ApiResponse<?> updateTheme(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, String> body) {
        User u = me(t);
        String theme = body.getOrDefault("theme", "classic");
        if (!Set.of("classic", "midnight", "warm", "custom").contains(theme)) {
            throw new BizException("主题类型不正确");
        }
        String accent = body.getOrDefault("accentColor", "#d9363e");
        String background = body.getOrDefault("backgroundColor", "#f5f6f8");
        if (!accent.matches("^#[0-9a-fA-F]{6}$") || !background.matches("^#[0-9a-fA-F]{6}$")) {
            throw new BizException("颜色格式不正确");
        }
        String layout = body.getOrDefault("interfaceLayout", Objects.requireNonNullElse(u.interfaceLayout, "standard"));
        if (!Set.of("standard", "editorial", "compact").contains(layout)) throw new BizException("界面布局不正确");
        u.theme = theme;
        u.accentColor = accent;
        u.backgroundColor = background;
        u.interfaceLayout = layout;
        users.save(u);
        return ApiResponse.ok(u);
    }

    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestHeader("X-Token") String t, @RequestBody Map<String, String> body) {
        User u = me(t);
        return ApiResponse.ok(auth.updateProfile(u, body.get("nickname"), body.get("phone"), body.get("email"), body.get("avatar")));
    }

    @PutMapping("/profile/password")
    public ApiResponse<?> changePassword(@RequestHeader("X-Token") String t, @RequestBody Map<String, String> body) {
        auth.changePassword(me(t), body.get("oldPassword"), body.get("newPassword"));
        return ApiResponse.ok();
    }

    @PostMapping("/profile/avatar")
    public ApiResponse<?> uploadAvatar(@RequestHeader("X-Token") String t, @RequestParam("file") MultipartFile file) {
        User u = me(t);
        if (file == null || file.isEmpty()) throw new BizException("请选择头像图片");
        if (file.getSize() > 2 * 1024 * 1024) throw new BizException("头像文件不能超过 2MB");
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new BizException("头像上传仅接收 JPG 或 PNG 图片");
        };
        try {
            if (ImageIO.read(file.getInputStream()) == null) throw new BizException("无法识别头像图片内容");
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("头像图片读取失败");
        }
        try {
            Path directory = Paths.get(avatarDirectory).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String filename = u.id + "-" + System.currentTimeMillis() + "." + extension;
            file.transferTo(directory.resolve(filename));
            try (Stream<Path> existing = Files.list(directory)) {
                existing.filter(path -> path.getFileName().toString().startsWith(u.id + "-") && !path.getFileName().toString().equals(filename)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
            u.avatar = "/uploads/avatars/" + filename;
            users.save(u);
            return ApiResponse.ok(u);
        } catch (Exception error) {
            throw new BizException("头像保存失败，请稍后重试");
        }
    }

    @PostMapping("/profile/merge-guest")
    public ApiResponse<?> mergeGuestData(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        int mergedCart = 0;
        int mergedFavorites = 0;
        int mergedHistory = 0;

        Object cartValue = body.get("cart");
        if (cartValue instanceof List<?> guestCart) {
            for (Object value : guestCart.stream().limit(100).toList()) {
                if (!(value instanceof Map<?, ?> row) || !(row.get("productId") instanceof Number productNumber)) continue;
                long productId = productNumber.longValue();
                Product product = products.findById(productId).filter(x -> x.enabled).orElse(null);
                if (product == null || product.stock < 1) continue;
                int quantity = row.get("quantity") instanceof Number number ? number.intValue() : 1;
                quantity = Math.max(1, Math.min(quantity, product.stock));
                String selectedSku = Objects.toString(row.get("selectedSku"), "标准版");
                Optional<CartItem> existing = carts.findByUserIdAndProductId(u.id, productId);
                CartItem item = existing.orElseGet(CartItem::new);
                if (existing.isEmpty()) {
                    item.userId = u.id;
                    item.productId = productId;
                    item.quantity = 0;
                }
                item.quantity = Math.min(product.stock, item.quantity + quantity);
                item.selectedSku = selectedSku;
                carts.save(item);
                mergedCart++;
            }
        }

        Object favoriteValue = body.get("favorites");
        if (favoriteValue instanceof List<?> guestFavorites) {
            for (Object value : guestFavorites.stream().limit(100).toList()) {
                if (!(value instanceof Number number)) continue;
                long productId = number.longValue();
                if (products.findById(productId).filter(x -> x.enabled).isEmpty()) continue;
                if (favs.findByUserIdAndProductId(u.id, productId).isEmpty()) {
                    Favorite favorite = new Favorite();
                    favorite.userId = u.id;
                    favorite.productId = productId;
                    favs.save(favorite);
                    mergedFavorites++;
                }
            }
        }

        Object historyValue = body.get("history");
        if (historyValue instanceof List<?> guestHistory) {
            for (Object value : guestHistory.stream().limit(20).toList()) {
                if (!(value instanceof Number number)) continue;
                long productId = number.longValue();
                if (products.findById(productId).filter(x -> x.enabled).isEmpty()) continue;
                History history = histories.findByUserId(u.id).stream()
                        .filter(x -> Objects.equals(x.productId, productId)).findFirst().orElseGet(History::new);
                history.userId = u.id;
                history.productId = productId;
                history.viewedAt = LocalDateTime.now();
                histories.save(history);
                mergedHistory++;
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("cart", mergedCart);
        result.put("favorites", mergedFavorites);
        result.put("history", mergedHistory);
        return ApiResponse.ok(result);
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories() {
        return ApiResponse.ok(cats.findAll().stream()
                .filter(x -> x.enabled)
                .sorted(Comparator.comparingInt((Category x) -> x.sortNo))
                .toList());
    }

    @GetMapping("/brands")
    public ApiResponse<?> brands() {
        return ApiResponse.ok(brands.findAll().stream()
                .filter(x -> x.enabled)
                .toList());
    }

    @GetMapping("/products")
    public ApiResponse<?> products(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minEndurance,
            @RequestParam(required = false) Double minPayload,
            @RequestParam(defaultValue = "") String intelligence,
            @RequestParam(defaultValue = "sales") String sort) {
        List<Product> list = products.findAll().stream()
                .filter(x -> x.enabled)
                .filter(x -> keyword.isBlank()
                        || x.name.toLowerCase().contains(keyword.toLowerCase())
                        || Optional.ofNullable(x.subtitle).orElse("").contains(keyword))
                .filter(x -> categoryId == null || Objects.equals(x.categoryId, categoryId))
                .filter(x -> brandId == null || Objects.equals(x.brandId, brandId))
                .filter(x -> minPrice == null || x.price.compareTo(minPrice) >= 0)
                .filter(x -> maxPrice == null || x.price.compareTo(maxPrice) <= 0)
                .filter(x -> minEndurance == null || Optional.ofNullable(x.enduranceMinutes).orElse(0) >= minEndurance)
                .filter(x -> minPayload == null || Optional.ofNullable(x.payloadKg).orElse(0d) >= minPayload)
                .filter(x -> intelligence.isBlank() || intelligence.equals(x.intelligenceLevel))
                .collect(Collectors.toList());

        Comparator<Product> cp = switch (sort) {
            case "priceAsc" -> Comparator.comparing((Product x) -> x.price);
            case "priceDesc" -> Comparator.comparing((Product x) -> x.price).reversed();
            case "rating" -> Comparator.comparingDouble((Product x) -> x.rating).reversed();
            default -> Comparator.comparingInt((Product x) -> x.sales).reversed();
        };

        list.sort(cp);
        list.forEach(this::enrich);
        return ApiResponse.ok(list);
    }

    @GetMapping("/products/{id}")
    public ApiResponse<?> product(
            @PathVariable Long id,
            @RequestHeader(value = "X-Token", required = false) String t) {
        Product p = products.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        if (t != null && !t.isBlank()) {
            try {
                User u = me(t);
                List<History> hList = histories.findByUserId(u.id).stream()
                        .filter(h -> Objects.equals(h.productId, id))
                        .toList();
                if (hList.isEmpty()) {
                    History h = new History();
                    h.userId = u.id;
                    h.productId = id;
                    h.viewedAt = LocalDateTime.now();
                    histories.save(h);
                } else {
                    History h = hList.get(0);
                    h.viewedAt = LocalDateTime.now();
                    histories.save(h);
                }
            } catch (Exception ignored) {
            }
        }
        return ApiResponse.ok(enrich(p));
    }

    @GetMapping("/products/{id}/reviews")
    public ApiResponse<?> productReviews(@PathVariable Long id) {
        products.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        return ApiResponse.ok(reviews.findByProductIdAndVisibleTrue(id).stream()
                .sorted(Comparator.comparing((Review x) -> x.createdAt).reversed())
                .toList());
    }

    @PostMapping("/products/compare")
    public ApiResponse<?> compare(@RequestBody List<Long> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 4) {
            throw new BizException("请选择 2～4 件商品进行对比");
        }
        List<Long> uniqueIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.size() != ids.size()) throw new BizException("不能重复选择同一件商品");
        Map<Long, Product> byId = products.findAllById(uniqueIds).stream()
                .filter(x -> x.enabled && "ROBOT".equals(x.type))
                .collect(Collectors.toMap(x -> x.id, x -> x));
        if (byId.size() != uniqueIds.size()) throw new BizException("部分商品不存在或不可参与对比");
        List<Product> list = uniqueIds.stream().map(byId::get).toList();
        Long categoryId = list.get(0).categoryId;
        if (list.stream().anyMatch(x -> !Objects.equals(categoryId, x.categoryId))) {
            throw new BizException("参数对比仅支持同一分类的机器人商品");
        }
        list.forEach(this::enrich);
        return ApiResponse.ok(list);
    }

    @GetMapping("/products/{id}/accessories")
    public ApiResponse<?> accessories(@PathVariable Long id) {
        List<Compatibility> clist = compat.findByRobotProductId(id);
        Set<Long> aid = clist.stream().map(x -> x.accessoryProductId).collect(Collectors.toSet());
        List<Product> list = products.findAllById(aid).stream()
                .filter(x -> x.enabled)
                .toList();
        list.forEach(this::enrich);
        return ApiResponse.ok(list);
    }

    @GetMapping("/compatibility/check")
    public ApiResponse<?> checkCompat(
            @RequestParam Long mainId,
            @RequestParam Long accessoryId) {
        Product main = products.findById(mainId).orElseThrow(() -> new BizException("机器人商品不存在"));
        Product accessory = products.findById(accessoryId).orElseThrow(() -> new BizException("配件商品不存在"));
        if (!"ROBOT".equals(main.type) || !"ACCESSORY".equals(accessory.type)) {
            throw new BizException("请选择机器人主机和配件商品");
        }
        Compatibility c = compat.findByRobotProductIdAndAccessoryProductId(mainId, accessoryId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("compatible", c != null);
        result.put("note", c != null ? c.note : "平台暂未建立该型号的适配关系，请联系运营人员确认");
        return ApiResponse.ok(result);
    }

    @PostMapping("/compatibility/analyze")
    public ApiResponse<?> analyzeCompatibility(@RequestBody Map<String, Object> body) {
        Long mainId = numberAsLong(body.get("mainId"), "请选择机器人主机");
        Long accessoryId = numberAsLong(body.get("accessoryId"), "请选择候选配件");
        Product main = products.findById(mainId).orElseThrow(() -> new BizException("机器人商品不存在"));
        Product accessory = products.findById(accessoryId).orElseThrow(() -> new BizException("配件商品不存在"));
        if (!"ROBOT".equals(main.type) || !"ACCESSORY".equals(accessory.type)) {
            throw new BizException("请选择机器人主机和配件商品");
        }
        Compatibility relation = compat.findByRobotProductIdAndAccessoryProductId(mainId, accessoryId).orElse(null);
        boolean confirmed = relation != null;
        String level = confirmed ? Objects.toString(relation.compatibilityLevel, "COMPATIBLE") : "UNVERIFIED";
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(compatCheck("型号关系", confirmed ? "PASS" : "UNKNOWN",
                confirmed ? valueOr(relation.supportedModels, "已录入主机与配件的适配关系") : "适配库中没有找到该组合"));
        checks.add(compatCheck("接口与连接", confirmed && relation.interfaceType != null ? "PASS" : "UNKNOWN",
                confirmed ? valueOr(relation.interfaceType, "暂无独立接口说明") : "需核对接口、电压、通信协议及安装方式"));
        checks.add(compatCheck("尺寸与结构", confirmed && relation.sizeRequirement != null ? "PASS" : "UNKNOWN",
                confirmed ? valueOr(relation.sizeRequirement, "未设置额外尺寸限制") : "需核对物理尺寸、安装位和承重限制"));
        checks.add(compatCheck("型号代际", confirmed && relation.generationRequirement != null ? "PASS" : "UNKNOWN",
                confirmed ? valueOr(relation.generationRequirement, "已按当前型号关系确认") : "同品牌不同代际也可能不通用"));
        checks.add(compatCheck("软件与固件", confirmed && relation.firmwareRequirement != null ? "PASS" : "UNKNOWN",
                confirmed ? valueOr(relation.firmwareRequirement, "无已知固件限制") : "如属于智能配件，请确认 App 和固件版本"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("compatible", confirmed && !"INCOMPATIBLE".equals(level));
        result.put("confidence", confirmed ? ("CONDITIONAL".equals(level) ? 78 : 96) : 28);
        result.put("title", confirmed ? ("CONDITIONAL".equals(level) ? "有条件适配" : "已确认适配") : "暂无充分依据");
        result.put("summary", confirmed ? valueOr(relation.note, "平台已建立该组合的适配关系") : "未建立关系不等于一定不兼容，也不能作为可安全使用的依据。");
        result.put("riskNote", confirmed ? valueOr(relation.riskNote, "安装前仍应核对商品包装和官方说明") : "建议暂缓购买，联系品牌或平台客服确认完整型号、代际和接口。");
        result.put("checks", checks);
        result.put("mainProduct", enrich(main));
        result.put("accessoryProduct", enrich(accessory));
        return ApiResponse.ok(result);
    }

    private Long numberAsLong(Object value, String message) {
        if (!(value instanceof Number number)) throw new BizException(message);
        return number.longValue();
    }

    private Map<String, Object> compatCheck(String name, String status, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name); row.put("status", status); row.put("detail", detail);
        return row;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }



    @GetMapping("/cart")
    public ApiResponse<?> cart(@RequestHeader("X-Token") String t) {
        User u = me(t);
        List<Map<String, Object>> result = carts.findByUserId(u.id).stream().map(item -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.id);
            row.put("productId", item.productId);
            row.put("quantity", item.quantity);
            row.put("selectedSku", item.selectedSku);
            row.put("product", products.findById(item.productId).orElse(null));
            return row;
        }).toList();
        return ApiResponse.ok(result);
    }

    @PostMapping("/cart")
    public ApiResponse<?> addCart(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        Long pid = ((Number) body.get("productId")).longValue();
        int qty = ((Number) body.getOrDefault("quantity", 1)).intValue();
        Product product = products.findById(pid).orElseThrow(() -> new BizException("商品不存在"));
        if (!product.enabled) throw new BizException("商品已下架");
        if (qty < 1) throw new BizException("数量必须大于 0");
        Optional<CartItem> optItem = carts.findByUserIdAndProductId(u.id, pid);
        CartItem item = optItem.orElseGet(CartItem::new);
        if (optItem.isEmpty()) {
            item.userId = u.id;
            item.productId = pid;
            item.quantity = 0;
        }
        int newQty = item.quantity + qty;
        if (newQty > product.stock) throw new BizException("购买数量超过库存");
        item.quantity = newQty;
        item.selectedSku = String.valueOf(body.getOrDefault("selectedSku", "标准版"));
        carts.save(item);
        return ApiResponse.ok(item);
    }

    @PutMapping("/cart/{id}")
    public ApiResponse<?> updateCart(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        CartItem item = carts.findById(id).orElseThrow(() -> new BizException("购物车项不存在"));
        if (!Objects.equals(item.userId, u.id)) throw new BizException("无权修改");
        int qty = ((Number) body.get("quantity")).intValue();
        Product product = products.findById(item.productId).orElseThrow(() -> new BizException("商品不存在"));
        if (!product.enabled) throw new BizException("商品已下架");
        if (qty < 1 || qty > product.stock) throw new BizException("数量不合法或超过库存");
        item.quantity = qty;
        carts.save(item);
        return ApiResponse.ok(item);
    }

    @DeleteMapping("/cart/{id}")
    public ApiResponse<?> removeCart(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        CartItem item = carts.findById(id).orElseThrow(() -> new BizException("购物车项不存在"));
        if (!Objects.equals(item.userId, u.id)) {
            throw new BizException("无权删除");
        }
        carts.delete(item);
        return ApiResponse.ok();
    }

    @DeleteMapping("/cart")
    public ApiResponse<?> clearCart(@RequestHeader("X-Token") String t) {
        User u = me(t);
        carts.deleteAll(carts.findByUserId(u.id));
        return ApiResponse.ok();
    }

    @GetMapping("/favorites")
    public ApiResponse<?> favorites(@RequestHeader("X-Token") String t) {
        User u = me(t);
        List<Favorite> list = favs.findByUserId(u.id);
        Set<Long> pid = list.stream().map(x -> x.productId).collect(Collectors.toSet());
        List<Product> plist = products.findAllById(pid).stream()
                .filter(x -> x.enabled)
                .toList();
        plist.forEach(this::enrich);
        return ApiResponse.ok(plist);
    }

    @PostMapping("/favorites/{pid}")
    public ApiResponse<?> toggleFavorite(
            @RequestHeader("X-Token") String t,
            @PathVariable Long pid) {
        User u = me(t);
        Optional<Favorite> optFav = favs.findByUserIdAndProductId(u.id, pid);
        if (optFav.isEmpty()) {
            Favorite f = new Favorite();
            f.userId = u.id;
            f.productId = pid;
            favs.save(f);
            return ApiResponse.ok(true);
        } else {
            favs.delete(optFav.get());
            return ApiResponse.ok(false);
        }
    }

    @DeleteMapping("/favorites/{pid}")
    public ApiResponse<?> removeFavorite(@RequestHeader("X-Token") String t, @PathVariable Long pid) {
        User u = me(t);
        favs.findByUserIdAndProductId(u.id, pid).ifPresent(favs::delete);
        return ApiResponse.ok(true);
    }

    @DeleteMapping("/favorites")
    public ApiResponse<?> clearFavorites(@RequestHeader("X-Token") String t) {
        User u = me(t);
        favs.deleteAll(favs.findByUserId(u.id));
        return ApiResponse.ok();
    }

    @GetMapping("/history")
    public ApiResponse<?> history(@RequestHeader("X-Token") String t) {
        User u = me(t);
        List<History> list = histories.findByUserId(u.id).stream()
                .sorted(Comparator.comparing((History x) -> x.viewedAt).reversed())
                .limit(20)
                .toList();
        Set<Long> pid = list.stream().map(x -> x.productId).collect(Collectors.toSet());
        Map<Long, Product> pmap = products.findAllById(pid).stream()
                .filter(x -> x.enabled)
                .collect(Collectors.toMap(x -> x.id, x -> x));
        List<Map<String, Object>> result = list.stream()
                .filter(x -> pmap.containsKey(x.productId))
                .map(x -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("product", pmap.get(x.productId));
                    m.put("viewTime", x.viewedAt);
                    return m;
                })
                .toList();
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/history/{productId}")
    public ApiResponse<?> removeHistory(
            @RequestHeader("X-Token") String t,
            @PathVariable Long productId) {
        User u = me(t);
        List<History> matched = histories.findByUserId(u.id).stream()
                .filter(x -> Objects.equals(x.productId, productId)).toList();
        histories.deleteAll(matched);
        return ApiResponse.ok();
    }

    @DeleteMapping("/history")
    public ApiResponse<?> clearHistory(@RequestHeader("X-Token") String t) {
        User u = me(t);
        histories.deleteAll(histories.findByUserId(u.id));
        return ApiResponse.ok();
    }

    @GetMapping("/addresses")
    public ApiResponse<?> addresses(@RequestHeader("X-Token") String t) {
        User u = me(t);
        return ApiResponse.ok(addresses.findByUserId(u.id));
    }

    @PostMapping("/addresses")
    public ApiResponse<?> addAddress(
            @RequestHeader("X-Token") String t,
            @RequestBody Address a) {
        User u = me(t);
        validateAddress(a);
        a.userId = u.id;
        if (addresses.findByUserId(u.id).isEmpty()) a.defaultAddress = true;
        if (a.defaultAddress) clearDefaultAddress(u.id, null);
        addresses.save(a);
        return ApiResponse.ok(a);
    }

    @PutMapping("/addresses/{id}")
    public ApiResponse<?> updateAddress(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id,
            @RequestBody Address a) {
        User u = me(t);
        Address old = addresses.findById(id).orElseThrow(() -> new BizException("地址不存在"));
        if (!Objects.equals(old.userId, u.id)) {
            throw new BizException("无权修改");
        }
        validateAddress(a);
        old.receiver = a.receiver;
        old.phone = a.phone;
        old.province = a.province;
        old.city = a.city;
        old.district = a.district;
        old.detail = a.detail;
        old.defaultAddress = a.defaultAddress;
        if (old.defaultAddress) clearDefaultAddress(u.id, old.id);
        addresses.save(old);
        return ApiResponse.ok(old);
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<?> removeAddress(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        Address a = addresses.findById(id).orElseThrow(() -> new BizException("地址不存在"));
        if (!Objects.equals(a.userId, u.id)) {
            throw new BizException("无权删除");
        }
        addresses.delete(a);
        List<Address> remaining = addresses.findByUserId(u.id);
        if (a.defaultAddress && !remaining.isEmpty()) {
            remaining.get(0).defaultAddress = true;
            addresses.save(remaining.get(0));
        }
        return ApiResponse.ok();
    }

    private void validateAddress(Address a) {
        if (a.receiver == null || a.receiver.trim().isBlank() || a.receiver.trim().length() > 30) throw new BizException("请填写正确的收货人姓名");
        if (a.phone == null || !a.phone.trim().matches("^1[3-9]\\d{9}$")) throw new BizException("请填写正确的收货手机号");
        if (Stream.of(a.province, a.city, a.district, a.detail).anyMatch(x -> x == null || x.trim().isBlank())) throw new BizException("请填写完整的收货地址");
        if (a.detail.trim().length() > 100) throw new BizException("详细地址不能超过 100 个字符");
    }

    private void clearDefaultAddress(Long userId, Long exceptId) {
        List<Address> list = addresses.findByUserId(userId);
        list.stream().filter(x -> x.defaultAddress && !Objects.equals(x.id, exceptId)).forEach(x -> x.defaultAddress = false);
        addresses.saveAll(list);
    }

    @PostMapping("/orders")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> createOrder(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        String receiverInfo = String.valueOf(body.getOrDefault("receiverInfo", "")).trim();
        users.findForUpdate(u.id).orElseThrow(() -> new BizException("用户不存在"));
        Object addrValue = body.get("addressId");
        if (addrValue instanceof Number number) {
            Address addr = addresses.findById(number.longValue()).orElseThrow(() -> new BizException("地址不存在"));
            if (!Objects.equals(addr.userId, u.id)) throw new BizException("无权使用该地址");
            receiverInfo = addr.receiver + "," + addr.phone + "," + addr.province + addr.city + addr.district + addr.detail;
        }
        if (receiverInfo.isBlank()) throw new BizException("请填写收货信息");

        List<CartItem> items = carts.findByUserId(u.id).stream()
                .sorted(Comparator.comparing(x -> x.productId)).toList();
        if (items.isEmpty()) throw new BizException("购物车为空");

        OrderEntity order = new OrderEntity();
        order.userId = u.id;
        order.status = "PENDING";
        order.orderNo = "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        order.createdAt = LocalDateTime.now();
        order.receiverInfo = receiverInfo;

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> newItems = new ArrayList<>();
        for (CartItem cartItem : items) {
            Product product = products.findForUpdate(cartItem.productId).orElseThrow(() -> new BizException("商品不存在"));
            if (!product.enabled) throw new BizException(product.name + " 已下架");
            if (cartItem.quantity < 1 || cartItem.quantity > product.stock) throw new BizException(product.name + " 库存不足");
            OrderItem item = new OrderItem();
            item.productId = product.id;
            item.productName = product.name;
            item.price = product.price;
            item.quantity = cartItem.quantity;
            item.selectedSku = Optional.ofNullable(cartItem.selectedSku).orElse("标准版");
            newItems.add(item);
            total = total.add(product.price.multiply(BigDecimal.valueOf(cartItem.quantity)));
            product.stock -= cartItem.quantity;
            product.sales += cartItem.quantity;
            products.save(product);
        }
        order.totalAmount = total;
        order = orders.save(order);
        for (OrderItem item : newItems) {
            item.orderId = order.id;
            orderItems.save(item);
        }
        carts.deleteAll(items);
        return ApiResponse.ok(order);
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders(@RequestHeader("X-Token") String t) {
        User u = me(t);
        List<Map<String, Object>> result = orders.findByUserId(u.id).stream()
                .sorted(Comparator.comparing((OrderEntity x) -> x.createdAt).reversed())
                .map(order -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("order", order);
                    row.put("items", orderItems.findByOrderId(order.id));
                    return row;
                }).toList();
        return ApiResponse.ok(result);
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<?> orderDetail(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        OrderEntity order = orders.findById(id).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) {
            throw new BizException("无权查看");
        }
        List<OrderItem> items = orderItems.findByOrderId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return ApiResponse.ok(result);
    }

    @PostMapping("/orders/{id}/pay")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> payOrder(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        OrderEntity order = orders.findForUpdate(id).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) {
            throw new BizException("无权操作");
        }
        if (!"PENDING".equals(order.status)) {
            throw new BizException("订单状态不正确");
        }
        order.status = "PAID";
        order.paidAt = LocalDateTime.now();
        orders.save(order);
        return ApiResponse.ok(order);
    }

    @PostMapping("/orders/{id}/cancel")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> cancelOrder(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        OrderEntity order = orders.findForUpdate(id).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) {
            throw new BizException("无权操作");
        }
        if (!"PENDING".equals(order.status)) {
            throw new BizException("订单状态不正确");
        }
        for (OrderItem item : orderItems.findByOrderId(order.id).stream().sorted(Comparator.comparing(x -> x.productId)).toList()) {
            products.findForUpdate(item.productId).ifPresent(product -> {
                product.stock += item.quantity;
                product.sales = Math.max(0, product.sales - item.quantity);
                products.save(product);
            });
        }
        order.status = "CANCELLED";
        orders.save(order);
        return ApiResponse.ok(order);
    }

    @PostMapping("/orders/{id}/receive")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> receiveOrder(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User u = me(t);
        OrderEntity order = orders.findForUpdate(id).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) {
            throw new BizException("无权操作");
        }
        if (!"SHIPPED".equals(order.status)) {
            throw new BizException("订单状态不正确");
        }
        order.status = "COMPLETED";
        orders.save(order);
        return ApiResponse.ok(order);
    }

    @PostMapping("/reviews")
    public ApiResponse<?> addReview(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        Long orderId = ((Number) body.get("orderId")).longValue();
        Long productId = ((Number) body.get("productId")).longValue();
        Object ratingValue = body.get("rating");
        if (!(ratingValue instanceof Number number)) {
            throw new BizException("请选择评分");
        }
        int rating = number.intValue();
        if (rating < 1 || rating > 5) {
            throw new BizException("评分必须为 1 至 5 星");
        }
        String content = Objects.toString(body.get("content"), "").trim();
        if (content.isEmpty()) {
            throw new BizException("请填写评价内容");
        }
        if (content.length() > 500) {
            throw new BizException("评价内容不能超过 500 字");
        }

        OrderEntity order = orders.findById(orderId).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) {
            throw new BizException("无权评价");
        }
        if (!"COMPLETED".equals(order.status)) {
            throw new BizException("订单未完成");
        }

        List<OrderItem> oItems = orderItems.findByOrderId(orderId);
        OrderItem oItem = oItems.stream()
                .filter(oi -> Objects.equals(oi.productId, productId))
                .findFirst()
                .orElseThrow(() -> new BizException("该订单不包含此商品"));

        List<Review> existing = reviews.findByUserId(u.id).stream()
                .filter(r -> Objects.equals(r.orderItemId, oItem.id))
                .toList();
        if (!existing.isEmpty()) {
            throw new BizException("已评价过该商品");
        }

        Review r = new Review();
        r.userId = u.id;
        r.orderItemId = oItem.id;
        r.productId = productId;
        r.score = rating;
        r.content = content;
        r.createdAt = LocalDateTime.now();
        reviews.save(r);

        Product p = products.findById(productId).orElse(null);
        if (p != null) {
            List<Review> pr = reviews.findByProductIdAndVisibleTrue(productId);
            double avg = pr.stream().mapToInt(x -> x.score).average().orElse(0);
            p.rating = avg;
            products.save(p);
        }

        return ApiResponse.ok(r);
    }

    @GetMapping("/reviews/mine")
    public ApiResponse<?> myReviews(@RequestHeader("X-Token") String t) {
        User u = me(t);
        List<Review> list = reviews.findByUserId(u.id).stream()
                .sorted(Comparator.comparing((Review x) -> x.createdAt).reversed())
                .toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/after-sales")
    public ApiResponse<?> myAfterSales(@RequestHeader("X-Token") String t) {
        User u = me(t);
        return ApiResponse.ok(afterSales.findByUserId(u.id).stream()
                .sorted(Comparator.comparing((AfterSaleRequest x) -> x.createdAt).reversed()).toList());
    }

    @PostMapping("/after-sales")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> createAfterSale(
            @RequestHeader("X-Token") String t,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        Long orderId = numberAsLong(body.get("orderId"), "请选择订单");
        Long orderItemId = numberAsLong(body.get("orderItemId"), "请选择售后商品");
        OrderEntity order = orders.findForUpdate(orderId).orElseThrow(() -> new BizException("订单不存在"));
        if (!Objects.equals(order.userId, u.id)) throw new BizException("无权申请该订单售后");
        if (!Set.of("PAID", "SHIPPED", "COMPLETED").contains(order.status)) throw new BizException("当前订单状态不支持售后");
        OrderItem item = orderItems.findByOrderId(orderId).stream()
                .filter(x -> Objects.equals(x.id, orderItemId)).findFirst()
                .orElseThrow(() -> new BizException("订单中不存在该商品"));
        boolean active = afterSales.findByOrderItemId(orderItemId).stream()
                .anyMatch(x -> !Set.of("REJECTED", "CANCELLED", "COMPLETED").contains(x.status));
        if (active) throw new BizException("该商品已有处理中的售后单");
        String type = Objects.toString(body.get("type"), "").trim();
        if (!Set.of("REFUND", "RETURN", "REPAIR").contains(type)) throw new BizException("请选择退款、退货或维修");
        BigDecimal completedRefunds = afterSales.findByOrderItemId(orderItemId).stream()
                .filter(x -> "COMPLETED".equals(x.status) && !"REPAIR".equals(x.type))
                .map(x -> Objects.requireNonNullElse(x.approvedAmount, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingAmount = item.price.multiply(BigDecimal.valueOf(item.quantity)).subtract(completedRefunds);
        if (remainingAmount.signum() <= 0) throw new BizException("该商品已完成全额退款售后，不能重复申请");
        String reason = Objects.toString(body.get("reason"), "").trim();
        String description = Objects.toString(body.get("description"), "").trim();
        if (reason.isBlank()) throw new BizException("请选择售后原因");
        if (description.length() < 5 || description.length() > 1000) throw new BizException("问题说明请填写 5～1000 字");
        AfterSaleRequest request = new AfterSaleRequest();
        request.serviceNo = "AS" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", new Random().nextInt(10000));
        request.userId = u.id; request.orderId = orderId; request.orderItemId = item.id;
        request.productId = item.productId; request.productName = item.productName; request.type = type;
        request.reason = reason; request.description = description; request.status = "SUBMITTED";
        request.requestedAmount = "REPAIR".equals(type) ? BigDecimal.ZERO : remainingAmount;
        request.createdAt = LocalDateTime.now(); request.updatedAt = request.createdAt;
        return ApiResponse.ok(afterSales.save(request));
    }

    @PostMapping("/after-sales/{id}/cancel")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> cancelAfterSale(@RequestHeader("X-Token") String t, @PathVariable Long id) {
        User u = me(t);
        AfterSaleRequest request = afterSales.findForUpdate(id).orElseThrow(() -> new BizException("售后单不存在"));
        if (!Objects.equals(request.userId, u.id)) throw new BizException("无权操作");
        if (!Set.of("SUBMITTED", "REVIEWING").contains(request.status)) throw new BizException("当前进度不能取消");
        request.status = "CANCELLED"; request.updatedAt = LocalDateTime.now();
        return ApiResponse.ok(afterSales.save(request));
    }

    @PostMapping("/after-sales/{id}/logistics")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> submitReturnLogistics(
            @RequestHeader("X-Token") String t, @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User u = me(t);
        AfterSaleRequest request = afterSales.findForUpdate(id).orElseThrow(() -> new BizException("售后单不存在"));
        if (!Objects.equals(request.userId, u.id)) throw new BizException("无权操作");
        if (!"WAITING_RETURN".equals(request.status)) throw new BizException("当前不需要填写退回物流");
        String company = Objects.toString(body.get("company"), "").trim();
        String logisticsNo = Objects.toString(body.get("logisticsNo"), "").trim();
        if (company.isBlank() || company.length() > 50 || logisticsNo.length() < 5 || logisticsNo.length() > 80) {
            throw new BizException("物流公司请填写 1～50 字，物流单号请填写 5～80 字");
        }
        request.returnLogisticsCompany = company; request.returnLogisticsNo = logisticsNo;
        request.status = "RETURN_SHIPPED"; request.updatedAt = LocalDateTime.now();
        return ApiResponse.ok(afterSales.save(request));
    }

    @PostMapping("/trade-in/estimate")
    public ApiResponse<?> tradeInEstimate(@RequestBody Map<String, Object> body) {
        String brand = Objects.toString(body.get("brand"), "").trim();
        String model = Objects.toString(body.get("model"), "").trim();
        String condition = Objects.toString(body.get("condition"), "good");
        String functionStatus = Objects.toString(body.get("functionStatus"), "normal");
        String batteryHealth = Objects.toString(body.get("batteryHealth"), "70-89");
        String accessories = Objects.toString(body.get("accessories"), "complete");
        int age = body.get("age") instanceof Number number ? number.intValue() : -1;
        BigDecimal originalPrice;
        try {
            originalPrice = new BigDecimal(Objects.toString(body.get("originalPrice"), "0"));
        } catch (NumberFormatException exception) {
            throw new BizException("原购买价格格式不正确");
        }
        if (brand.isBlank() || model.isBlank()) throw new BizException("请填写旧设备品牌和型号");
        if (age < 0 || age > 10) throw new BizException("使用年限应在 0～10 年之间");
        if (originalPrice.compareTo(new BigDecimal("500")) < 0
                || originalPrice.compareTo(new BigDecimal("1000000")) > 0) {
            throw new BizException("原购买价格应在 500～1000000 元之间");
        }

        Map<String, BigDecimal> conditionRates = Map.of(
                "excellent", new BigDecimal("0.92"), "good", new BigDecimal("0.80"),
                "fair", new BigDecimal("0.62"), "poor", new BigDecimal("0.38"));
        Map<String, BigDecimal> functionRates = Map.of(
                "normal", BigDecimal.ONE, "minor", new BigDecimal("0.78"),
                "major", new BigDecimal("0.42"));
        Map<String, BigDecimal> batteryRates = Map.of(
                "90+", BigDecimal.ONE, "70-89", new BigDecimal("0.88"),
                "50-69", new BigDecimal("0.68"), "below50", new BigDecimal("0.45"));
        Map<String, BigDecimal> accessoryRates = Map.of(
                "complete", BigDecimal.ONE, "partial", new BigDecimal("0.92"),
                "none", new BigDecimal("0.82"));
        if (!conditionRates.containsKey(condition) || !functionRates.containsKey(functionStatus)
                || !batteryRates.containsKey(batteryHealth) || !accessoryRates.containsKey(accessories)) {
            throw new BizException("设备状态选项不正确");
        }

        BigDecimal ageRate = BigDecimal.valueOf(Math.pow(0.78d, age));
        BigDecimal estimate = originalPrice.multiply(new BigDecimal("0.55"))
                .multiply(ageRate)
                .multiply(conditionRates.get(condition))
                .multiply(functionRates.get(functionStatus))
                .multiply(batteryRates.get(batteryHealth))
                .multiply(accessoryRates.get(accessories));
        BigDecimal low = estimate.multiply(new BigDecimal("0.90")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal high = estimate.multiply(new BigDecimal("1.10")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal midpoint = estimate.setScale(0, RoundingMode.HALF_UP);

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("使用年限系数", ageRate.setScale(2, RoundingMode.HALF_UP));
        factors.put("外观成色系数", conditionRates.get(condition));
        factors.put("功能状态系数", functionRates.get(functionStatus));
        factors.put("电池健康系数", batteryRates.get(batteryHealth));
        factors.put("配件完整系数", accessoryRates.get(accessories));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("estimate", midpoint);
        result.put("estimateLow", low);
        result.put("estimateHigh", high);
        result.put("tradeInBonus", originalPrice.compareTo(new BigDecimal("10000")) >= 0 ? 500 : 200);
        result.put("brand", brand);
        result.put("model", model);
        result.put("originalPrice", originalPrice);
        result.put("factors", factors);
        result.put("disclaimer", "结果为毕业设计模拟估价，最终价格应以实物检测为准");
        return ApiResponse.ok(result);
    }

    @GetMapping("/admin/dashboard")
    public ApiResponse<?> dashboard(@RequestHeader("X-Token") String t) {
        admin(t);
        long totalUsers = users.count();
        long totalProducts = products.count();
        long totalOrders = orders.count();
        long totalReviews = reviews.count();

        BigDecimal totalSales = orders.findAll().stream()
                .filter(x -> "COMPLETED".equals(x.status))
                .map(x -> x.totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderEntity> recentOrders = orders.findAll().stream()
                .sorted(Comparator.comparing((OrderEntity x) -> x.createdAt).reversed())
                .limit(10)
                .toList();

        List<OrderEntity> allOrders = orders.findAll();
        Map<String, Long> statusDistribution = allOrders.stream().collect(Collectors.groupingBy(
                x -> x.status, LinkedHashMap::new, Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> dailySales = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            BigDecimal amount = allOrders.stream()
                    .filter(x -> x.createdAt != null && x.createdAt.toLocalDate().equals(date))
                    .filter(x -> Set.of("PAID", "SHIPPED", "COMPLETED").contains(x.status))
                    .map(x -> x.totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = allOrders.stream().filter(x -> x.createdAt != null && x.createdAt.toLocalDate().equals(date)).count();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.toString()); day.put("amount", amount); day.put("orders", count);
            dailySales.add(day);
        }
        Map<String, Integer> categorySales = new LinkedHashMap<>();
        orderItems.findAll().forEach(item -> products.findById(item.productId).ifPresent(product -> {
            String category = product.categoryId == null ? "其他" : cats.findById(product.categoryId).map(x -> x.name).orElse("其他");
            categorySales.merge(category, item.quantity, Integer::sum);
        }));
        List<Product> lowStockProducts = products.findAll().stream().filter(x -> x.enabled && x.stock <= 5)
                .sorted(Comparator.comparingInt(x -> x.stock)).limit(10).peek(this::enrich).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalProducts", totalProducts);
        result.put("totalOrders", totalOrders);
        result.put("totalReviews", totalReviews);
        result.put("totalSales", totalSales);
        result.put("recentOrders", recentOrders);
        result.put("statusDistribution", statusDistribution);
        result.put("dailySales", dailySales);
        result.put("categorySales", categorySales);
        result.put("lowStockProducts", lowStockProducts);
        result.put("afterSalePending", afterSales.findAll().stream()
                .filter(x -> !Set.of("REJECTED", "CANCELLED", "COMPLETED").contains(x.status)).count());
        result.put("paidConversionRate", totalOrders == 0 ? 0 : Math.round(allOrders.stream()
                .filter(x -> Set.of("PAID", "SHIPPED", "COMPLETED").contains(x.status)).count() * 1000d / totalOrders) / 10d);
        return ApiResponse.ok(result);
    }

    @GetMapping("/admin/after-sales")
    public ApiResponse<?> adminAfterSales(@RequestHeader("X-Token") String t) {
        admin(t);
        return ApiResponse.ok(afterSales.findAll().stream()
                .sorted(Comparator.comparing((AfterSaleRequest x) -> x.createdAt).reversed())
                .peek(x -> x.allowedStatuses = AfterSalePolicy.allowedStatuses(x)).toList());
    }

    @PutMapping("/admin/after-sales/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> updateAfterSale(
            @RequestHeader("X-Token") String t, @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        admin(t);
        AfterSaleRequest request = afterSales.findForUpdate(id).orElseThrow(() -> new BizException("售后单不存在"));
        String status = Objects.toString(body.get("status"), "").trim();
        String reply = Objects.toString(body.get("adminReply"), "").trim();
        BigDecimal approved = AfterSalePolicy.validate(request, status, body.get("approvedAmount"), reply);
        request.status = status; request.adminReply = reply;
        request.approvedAmount = approved;
        request.updatedAt = LocalDateTime.now();
        if ("COMPLETED".equals(status)) request.completedAt = request.updatedAt;
        return ApiResponse.ok(afterSales.save(request));
    }

    @GetMapping("/admin/users")
    public ApiResponse<?> adminUsers(@RequestHeader("X-Token") String t) {
        admin(t);
        return ApiResponse.ok(users.findAll().stream().map(u->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",u.id);row.put("username",u.username);row.put("nickname",u.nickname);row.put("role",u.role);row.put("enabled",u.enabled);row.put("createTime",u.createTime);row.put("mustChangePassword",u.mustChangePassword);row.put("phoneVerified",u.phoneVerified);row.put("maskedPhone",u.phone==null||u.phone.isBlank()?"未填写":(u.phone.length()==11?u.phone.substring(0,3)+"****"+u.phone.substring(7):"已填写"));return row;}).toList());
    }

    @PutMapping("/admin/users/{id}/toggle")
    public ApiResponse<?> toggleUser(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        User currentAdmin = admin(t);
        User target = users.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        if (Objects.equals(currentAdmin.id, target.id) || "ADMIN".equals(target.role)) {
            throw new BizException("不能禁用管理员账号");
        }
        target.enabled = !target.enabled;
        if (!target.enabled) { target.token = null; target.tokenExpire = null; }
        users.save(target);
        return ApiResponse.ok(target);
    }

    @GetMapping("/admin/products")
    public ApiResponse<?> adminProducts(@RequestHeader("X-Token") String t) {
        admin(t);
        List<Product> list = products.findAll();
        list.forEach(this::enrich);
        return ApiResponse.ok(list);
    }

    @PostMapping("/admin/products")
    public ApiResponse<?> addProduct(
            @RequestHeader("X-Token") String t,
            @RequestBody Product p) {
        admin(t);
        if (p.name == null || p.name.isBlank()) throw new BizException("商品名称不能为空");
        if (p.price == null || p.price.signum() < 0) throw new BizException("商品价格不合法");
        if (p.categoryId == null || cats.findById(p.categoryId).isEmpty()) throw new BizException("请选择有效商品分类");
        if (p.brandId == null || brands.findById(p.brandId).isEmpty()) throw new BizException("请选择有效商品品牌");
        p.id = null;
        if (p.type == null || p.type.isBlank()) p.type = "ROBOT";
        if (p.image == null || p.image.isBlank()) p.image = "/products/placeholder.svg";
        if (p.skuOptions == null || p.skuOptions.isBlank()) p.skuOptions = "标准版";
        if (p.marketPrice == null || p.marketPrice.compareTo(p.price) < 0) p.marketPrice = p.price;
        if (p.warranty == null || p.warranty.isBlank()) p.warranty = "整机保修 1 年";
        if (p.shippingNote == null || p.shippingNote.isBlank()) p.shippingNote = "现货商品预计 24～48 小时内发出";
        products.save(p);
        return ApiResponse.ok(p);
    }

    @PutMapping("/admin/products/{id}")
    public ApiResponse<?> updateProduct(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id,
            @RequestBody Product p) {
        admin(t);
        Product old = products.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        if (p.name == null || p.name.isBlank()) throw new BizException("商品名称不能为空");
        if (p.price == null || p.price.signum() < 0) throw new BizException("商品价格不合法");
        if (p.categoryId == null || cats.findById(p.categoryId).isEmpty()) throw new BizException("请选择有效商品分类");
        if (p.brandId == null || brands.findById(p.brandId).isEmpty()) throw new BizException("请选择有效商品品牌");
        old.name = p.name;
        old.subtitle = p.subtitle;
        old.type = p.type;
        old.price = p.price;
        old.stock = p.stock;
        old.sales = p.sales;
        old.rating = p.rating;
        old.image = p.image;
        old.marketPrice = p.marketPrice;
        old.sourceUrl = p.sourceUrl;
        old.warranty = p.warranty;
        old.shippingNote = p.shippingNote;
        old.categoryId = p.categoryId;
        old.brandId = p.brandId;
        old.description = p.description;
        old.parametersJson = p.parametersJson;
        old.enduranceMinutes = p.enduranceMinutes;
        old.payloadKg = p.payloadKg;
        old.intelligenceLevel = p.intelligenceLevel;
        old.skuOptions = (p.skuOptions == null || p.skuOptions.isBlank()) ? "标准版" : p.skuOptions;
        old.enabled = p.enabled;
        products.save(old);
        return ApiResponse.ok(old);
    }

    @DeleteMapping("/admin/products/{id}")
    public ApiResponse<?> deleteProduct(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id) {
        admin(t);
        Product product = products.findById(id).orElseThrow(() -> new BizException("商品不存在"));
        product.enabled = false;
        products.save(product);
        return ApiResponse.ok(product);
    }

    @GetMapping("/admin/orders")
    public ApiResponse<?> adminOrders(@RequestHeader("X-Token") String t) {
        admin(t);
        return ApiResponse.ok(orders.findAll().stream()
                .sorted(Comparator.comparing((OrderEntity x) -> x.createdAt).reversed())
                .toList());
    }

    @PostMapping("/admin/orders/{id}/ship")
    public ApiResponse<?> shipOrder(
            @RequestHeader("X-Token") String t,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        admin(t);
        OrderEntity order = orders.findById(id).orElseThrow(() -> new BizException("订单不存在"));
        if (!"PAID".equals(order.status)) throw new BizException("订单状态不正确");
        if (body == null || !(body.get("logisticsCompany") instanceof String company)
                || !(body.get("logisticsNo") instanceof String number)
                || company.isBlank() || number.isBlank()) throw new BizException("物流公司和单号不能为空");
        if (company.trim().length() > 50 || number.trim().length() > 80) throw new BizException("物流信息长度超出限制");
        order.logisticsCompany = company.trim();
        order.logisticsNo = number.trim();
        order.status = "SHIPPED";
        orders.save(order);
        return ApiResponse.ok(order);
    }
}
