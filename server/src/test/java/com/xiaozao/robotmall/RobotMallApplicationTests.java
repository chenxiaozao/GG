package com.xiaozao.robotmall;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.auth-minute-limit=1000",
        "spring.datasource.url=jdbc:h2:mem:robotmalltest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class RobotMallApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired com.xiaozao.robotmall.repository.ProductRepository productRepository;
    @Autowired com.xiaozao.robotmall.config.DataInitializer initializer;

    @Test void startupPreservesAdministratorEditsAndDisabledProducts() throws Exception {
        var product = productRepository.findAll().stream()
                .filter(x -> "石头 Qrevo Curv".equals(x.name)).findFirst().orElseThrow();
        String originalImage = product.image, originalSource = product.sourceUrl;
        var originalPrice = product.price;
        String originalParameters = product.parametersJson;
        boolean originalEnabled = product.enabled;
        int originalStock = product.stock;
        try {
            product.image = "/uploads/custom-curv.jpg";
            product.sourceUrl = "https://example.org/custom-curv";
            product.price = new java.math.BigDecimal("1234.56");
            product.parametersJson = "{\"管理员维护\":\"自定义参数\"}";
            product.enabled = false;
            product.stock = 7;
            productRepository.save(product);
            initializer.run();
            var actual = productRepository.findById(product.id).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(product.image, actual.image);
            org.junit.jupiter.api.Assertions.assertEquals(product.sourceUrl, actual.sourceUrl);
            org.junit.jupiter.api.Assertions.assertEquals(0, product.price.compareTo(actual.price));
            org.junit.jupiter.api.Assertions.assertEquals(product.parametersJson, actual.parametersJson);
            org.junit.jupiter.api.Assertions.assertFalse(actual.enabled);
            org.junit.jupiter.api.Assertions.assertEquals(7, actual.stock);
        } finally {
            product.image = originalImage; product.sourceUrl = originalSource;
            product.price = originalPrice; product.parametersJson = originalParameters;
            product.enabled = originalEnabled; product.stock = originalStock;
            productRepository.save(product);
        }
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.user.role").exists())
                .andReturn();
        JsonNode root = mapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private long productIdByName(JsonNode products, String name) {
        for (JsonNode product : products) {
            if (name.equals(product.path("name").asText())) return product.path("id").asLong();
        }
        throw new IllegalStateException("测试商品不存在：" + name);
    }

    private String freshBuyer() throws Exception {
        String username = "flow" + System.nanoTime();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "testpass123", "nickname", "隔离测试"))))
                .andExpect(status().isOk());
        return login(username, "testpass123");
    }
    private void addCart(String token, long productId) throws Exception {
        mvc.perform(post("/api/cart").header("X-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isOk());
    }

    @Test void returnLifecycleEnforcesLogisticsAndPreventsDuplicateFullRefund() throws Exception {
        String buyer = freshBuyer(), administrator = login("admin", "admin123");
        long productId = productRepository.findAll().stream().filter(x -> x.enabled && x.stock > 2).findFirst().orElseThrow().id;
        addCart(buyer, productId);
        MvcResult created = mvc.perform(post("/api/orders").header("X-Token", buyer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverInfo\":\"虚构测试收货信息\"}"))
                .andExpect(status().isOk()).andReturn();
        long orderId = mapper.readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/orders/{id}/pay", orderId).header("X-Token", buyer)).andExpect(status().isOk());
        JsonNode detail = mapper.readTree(mvc.perform(get("/api/orders/{id}", orderId).header("X-Token", buyer)).andReturn()
                .getResponse().getContentAsString()).path("data");
        long itemId = detail.path("items").get(0).path("id").asLong();
        java.math.BigDecimal amount = detail.path("items").get(0).path("price").decimalValue();
        String application = mapper.writeValueAsString(java.util.Map.of("orderId", orderId, "orderItemId", itemId,
                "type", "RETURN", "reason", "商品故障", "description", "此为虚构退货测试场景"));
        MvcResult submitted = mvc.perform(post("/api/after-sales").header("X-Token", buyer)
                        .contentType(MediaType.APPLICATION_JSON).content(application)).andExpect(status().isOk()).andReturn();
        long serviceId = mapper.readTree(submitted.getResponse().getContentAsString()).path("data").path("id").asLong();
        for (String state : new String[] {"PROCESSING", "COMPLETED"}) {
            mvc.perform(put("/api/admin/after-sales/{id}", serviceId).header("X-Token", administrator).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(java.util.Map.of("status", state, "approvedAmount", amount, "adminReply", "非法跳过寄回"))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/admin/after-sales/{id}", serviceId).header("X-Token", administrator).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("status", "WAITING_RETURN", "approvedAmount", amount, "adminReply", "请寄回检测"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/after-sales/{id}/logistics", serviceId).header("X-Token", buyer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"虚构测试物流\",\"logisticsNo\":\"TEST12345678\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RETURN_SHIPPED"));
        for (String state : new String[] {"PROCESSING", "COMPLETED"}) {
            mvc.perform(put("/api/admin/after-sales/{id}", serviceId).header("X-Token", administrator).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(java.util.Map.of("status", state, "approvedAmount", amount, "adminReply", "演示退货处理"))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/after-sales").header("X-Token", buyer).contentType(MediaType.APPLICATION_JSON).content(application))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("该商品已完成全额退款售后，不能重复申请"));
    }

    @Test void failedCheckoutRollsBackEarlierStockAndKeepsCart() throws Exception {
        String buyer = freshBuyer();
        var choices = productRepository.findAll().stream().filter(x -> x.enabled && x.stock > 2)
                .sorted(java.util.Comparator.comparing(x -> x.id)).limit(2).toList();
        var first = choices.get(0); var second = choices.get(1);
        int firstStock = first.stock, secondStock = second.stock, firstSales = first.sales;
        addCart(buyer, first.id); addCart(buyer, second.id);
        second.stock = 0; productRepository.save(second);
        try {
            mvc.perform(post("/api/orders").header("X-Token", buyer).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"receiverInfo\":\"虚构收货信息\"}"))
                    .andExpect(status().isBadRequest());
            var unchanged = productRepository.findById(first.id).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(firstStock, unchanged.stock);
            org.junit.jupiter.api.Assertions.assertEquals(firstSales, unchanged.sales);
            mvc.perform(get("/api/cart").header("X-Token", buyer)).andExpect(jsonPath("$.data.length()").value(2));
            mvc.perform(get("/api/orders").header("X-Token", buyer)).andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            var restored = productRepository.findById(second.id).orElseThrow(); restored.stock = secondStock; productRepository.save(restored);
        }
    }

    @Test void concurrentCheckoutCreatesOneOrderAndReservesStockOnce() throws Exception {
        String buyer = freshBuyer();
        var product = productRepository.findAll().stream().filter(x -> x.enabled && x.stock > 2).findFirst().orElseThrow();
        int stockBefore = product.stock;
        addCart(buyer, product.id);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var ready = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.Callable<Integer> checkout = () -> {
                ready.countDown(); ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/orders").header("X-Token", buyer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverInfo\":\"虚构并发测试地址\"}")).andReturn().getResponse().getStatus();
            };
            var a = executor.submit(checkout); var b = executor.submit(checkout);
            var states = new java.util.ArrayList<>(java.util.List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(states);
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 400), states);
            org.junit.jupiter.api.Assertions.assertEquals(stockBefore - 1, productRepository.findById(product.id).orElseThrow().stock);
            mvc.perform(get("/api/orders").header("X-Token", buyer)).andExpect(jsonPath("$.data.length()").value(1));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void completeUserAndAdminFlow() throws Exception {
        String userToken = login("user", "123456");
        String adminToken = login("admin", "admin123");

        MvcResult productsResult = mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(72)))
                .andReturn();
        JsonNode products = mapper.readTree(productsResult.getResponse().getContentAsString()).path("data");
        long firstProductId = products.get(0).path("id").asLong();
        long g20sId = productIdByName(products, "石头 G20S Ultra");
        long p20Id = productIdByName(products, "石头 P20 Pro");
        long air3sId = productIdByName(products, "大疆 Air 3S");
        long air3sBatteryId = productIdByName(products, "Air 3S 智能飞行电池");
        long go2BatteryId = productIdByName(products, "Go2 智能电池");

        mvc.perform(get("/api/products/{id}", firstProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").isNotEmpty())
                .andExpect(jsonPath("$.data.skuOptions").isNotEmpty())
                .andExpect(jsonPath("$.data.brandName").isNotEmpty())
                .andExpect(jsonPath("$.data.categoryName").isNotEmpty());

        mvc.perform(post("/api/products/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + g20sId + "," + p20Id + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mvc.perform(post("/api/products/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + g20sId + "," + air3sId + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数对比仅支持同一分类的机器人商品"));

        mvc.perform(get("/api/products/{id}/accessories", air3sId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Air 3S 智能飞行电池"));

        mvc.perform(get("/api/compatibility/check")
                        .param("mainId", String.valueOf(air3sId))
                        .param("accessoryId", String.valueOf(air3sBatteryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compatible").value(true));

        mvc.perform(get("/api/compatibility/check")
                        .param("mainId", String.valueOf(air3sId))
                        .param("accessoryId", String.valueOf(go2BatteryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compatible").value(false));

        mvc.perform(post("/api/compatibility/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mainId\":" + air3sId + ",\"accessoryId\":" + air3sBatteryId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compatible").value(true))
                .andExpect(jsonPath("$.data.checks.length()").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.riskNote").isNotEmpty());

        mvc.perform(post("/api/trade-in/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brand\":\"石头科技\",\"model\":\"G20S Ultra\",\"originalPrice\":5999,\"age\":1,\"condition\":\"good\",\"functionStatus\":\"normal\",\"batteryHealth\":\"70-89\",\"accessories\":\"complete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimateLow").isNumber())
                .andExpect(jsonPath("$.data.estimateHigh").isNumber())
                .andExpect(jsonPath("$.data.factors.使用年限系数").exists());

        mvc.perform(post("/api/advisor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":7000,\"scene\":\"清洁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].categoryName").value("地面清洁"));

        mvc.perform(post("/api/advisor/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":7000,\"scene\":\"cleaning\",\"priority\":\"balanced\",\"space\":\"standard\",\"experience\":\"first\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("地面清洁"))
                .andExpect(jsonPath("$.data.items[0].product.name").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].score").isNumber())
                .andExpect(jsonPath("$.data.items[0].reasons.length()").value(greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/cart")
                        .header("X-Token", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + firstProductId + ",\"quantity\":1,\"selectedSku\":\"标准版\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mvc.perform(get("/api/cart").header("X-Token", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].product.name").isNotEmpty());

        MvcResult orderResult = mvc.perform(post("/api/orders")
                        .header("X-Token", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverInfo\":\"演示用户，13800138000，广州市演示地址\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long orderId = mapper.readTree(orderResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mvc.perform(post("/api/orders/{id}/pay", orderId).header("X-Token", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        mvc.perform(post("/api/admin/orders/{id}/ship", orderId).header("X-Token", adminToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("物流公司和单号不能为空"));
        mvc.perform(post("/api/admin/orders/{id}/ship", orderId)
                        .header("X-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsCompany\":\"  \",\"logisticsNo\":\"TEST\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/orders/{id}/ship", orderId)
                        .header("X-Token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"logisticsCompany\":\"顺丰速运\",\"logisticsNo\":\"SFTEST001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        mvc.perform(post("/api/orders/{id}/receive", orderId).header("X-Token", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        MvcResult detailResult = mvc.perform(get("/api/orders/{id}", orderId).header("X-Token", userToken))
                .andExpect(status().isOk()).andReturn();
        long orderItemId = mapper.readTree(detailResult.getResponse().getContentAsString()).path("data").path("items").get(0).path("id").asLong();
        MvcResult serviceResult = mvc.perform(post("/api/after-sales")
                        .header("X-Token", userToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"orderItemId\":" + orderItemId + ",\"type\":\"REPAIR\",\"reason\":\"商品故障\",\"description\":\"设备启动后无法正常完成任务。\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED")).andReturn();
        long serviceId = mapper.readTree(serviceResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(put("/api/admin/after-sales/{id}", serviceId)
                        .header("X-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\",\"adminReply\":\"金额错误测试\",\"approvedAmount\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/after-sales/{id}", serviceId)
                        .header("X-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\",\"adminReply\":\"已通过审核，正在安排检测。\",\"approvedAmount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROCESSING"));
        mvc.perform(get("/api/after-sales").header("X-Token", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].adminReply").isNotEmpty());

        mvc.perform(post("/api/reviews")
                        .header("X-Token", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"productId\":" + firstProductId
                                + ",\"rating\":5,\"content\":\"功能完整，操作流畅。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5));

        mvc.perform(get("/api/products/{id}/reviews", firstProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("功能完整，操作流畅。"));

        mvc.perform(get("/api/admin/dashboard").header("X-Token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProducts").value(greaterThanOrEqualTo(72)))
                .andExpect(jsonPath("$.data.dailySales.length()").value(7))
                .andExpect(jsonPath("$.data.afterSalePending").value(greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/advisor/ai-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.provider").value("DeepSeek"));

        mvc.perform(get("/api/admin/dashboard").header("X-Token", userToken))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/after-sales/{id}", serviceId)
                        .header("X-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"adminReply\":\"维修演示完成\",\"approvedAmount\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedAt").exists());
        mvc.perform(put("/api/admin/after-sales/{id}", serviceId)
                        .header("X-Token", adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\",\"adminReply\":\"尝试重新打开\",\"approvedAmount\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userDataIsIsolatedAndThemeIsPersisted() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String firstUser = "isolationA" + suffix;
        String secondUser = "isolationB" + suffix;
        for (String username : new String[] { firstUser, secondUser }) {
            mvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"testpass123\",\"nickname\":\"测试用户\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
        String firstToken = login(firstUser, "testpass123");
        String secondToken = login(secondUser, "testpass123");

        MvcResult productsResult = mvc.perform(get("/api/products"))
                .andExpect(status().isOk()).andReturn();
        long productId = mapper.readTree(productsResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        MvcResult cartResult = mvc.perform(post("/api/cart")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andExpect(status().isOk()).andReturn();
        long cartId = mapper.readTree(cartResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/favorites/{id}", productId).header("X-Token", firstToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}", productId).header("X-Token", firstToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/profile/summary").header("X-Token", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartCount").value(1))
                .andExpect(jsonPath("$.data.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.historyCount").value(1));
        mvc.perform(get("/api/cart").header("X-Token", secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/favorites").header("X-Token", secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/history").header("X-Token", secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put("/api/cart/{id}", cartId)
                        .header("X-Token", secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权修改"));

        mvc.perform(delete("/api/favorites/{id}", productId).header("X-Token", secondToken)).andExpect(status().isOk());
        mvc.perform(get("/api/favorites").header("X-Token", firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(delete("/api/favorites/{id}", productId).header("X-Token", firstToken)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/favorites").header("X-Token", firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(put("/api/profile/theme")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"midnight\",\"accentColor\":\"#5aa7ff\",\"backgroundColor\":\"#111827\",\"interfaceLayout\":\"editorial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("midnight"))
                .andExpect(jsonPath("$.data.interfaceLayout").value("editorial"));
        mvc.perform(get("/api/auth/me").header("X-Token", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").value("midnight"));

        mvc.perform(delete("/api/cart").header("X-Token", firstToken)).andExpect(status().isOk());
        mvc.perform(delete("/api/favorites").header("X-Token", firstToken)).andExpect(status().isOk());
        mvc.perform(delete("/api/history").header("X-Token", firstToken)).andExpect(status().isOk());
        mvc.perform(get("/api/profile/summary").header("X-Token", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartCount").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(0))
                .andExpect(jsonPath("$.data.historyCount").value(0));

        mvc.perform(post("/api/profile/merge-guest")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cart\":[{\"productId\":" + productId
                                + ",\"quantity\":2,\"selectedSku\":\"标准版\"}],\"favorites\":[" + productId
                                + "],\"history\":[" + productId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cart").value(1))
                .andExpect(jsonPath("$.data.favorites").value(1))
                .andExpect(jsonPath("$.data.history").value(1));
        mvc.perform(get("/api/profile/summary").header("X-Token", firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartCount").value(2))
                .andExpect(jsonPath("$.data.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.historyCount").value(1));
        mvc.perform(get("/api/profile/summary").header("X-Token", secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartCount").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(0))
                .andExpect(jsonPath("$.data.historyCount").value(0));

        mvc.perform(put("/api/profile")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小造测试用户\",\"phone\":\"13800138000\",\"email\":\"test@example.com\",\"avatar\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小造测试用户"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"));

        MvcResult addressResult = mvc.perform(post("/api/addresses")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver\":\"测试用户\",\"phone\":\"13800138000\",\"province\":\"江苏省\",\"city\":\"南京市\",\"district\":\"鼓楼区\",\"detail\":\"中央路 1 号\",\"defaultAddress\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true))
                .andReturn();
        long addressId = mapper.readTree(addressResult.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(delete("/api/addresses/{id}", addressId).header("X-Token", secondToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("无权删除"));

        mvc.perform(post("/api/orders").header("X-Token", secondToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("无权使用该地址"));
        mvc.perform(post("/api/orders").header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + addressId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.receiverInfo").value("测试用户,13800138000,江苏省南京市鼓楼区中央路 1 号"));

        mvc.perform(put("/api/profile/password")
                        .header("X-Token", firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"testpass123\",\"newPassword\":\"newpass789\"}"))
                .andExpect(status().isOk());
        login(firstUser, "newpass789");
    }
}
