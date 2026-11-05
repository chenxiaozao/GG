package com.xiaozao.robotmall;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.entity.Product;
import com.xiaozao.robotmall.service.DeepSeekAdvisorService;
import org.junit.jupiter.api.Test;

class AdvisorCatalogTests {
    private final DeepSeekAdvisorService service = new DeepSeekAdvisorService(new ObjectMapper());
    private Product product(long id, String name, String category, String type, int sales) {
        Product item = new Product(); item.id = id; item.name = name; item.categoryName = category;
        item.type = type; item.sales = sales; item.price = new BigDecimal("999"); return item;
    }
    private List<Product> crowdedCatalog() {
        List<Product> items = new ArrayList<>();
        for (long id = 1; id <= 60; id++) items.add(product(id, "清洁设备 " + id, "地面清洁", "ROBOT", 10000));
        items.add(product(61, "宇树 Go2 Pro", "四足机器人", "ROBOT", 1));
        items.add(product(62, "Go2 智能电池", "机器人配件", "ACCESSORY", 0));
        items.add(product(63, "大疆 Air 3S", "航拍无人机", "ROBOT", 0));
        items.add(product(64, "Air 3S 智能飞行电池", "机器人配件", "ACCESSORY", 0));
        return items;
    }
    @Test void lowSalesAccessoriesAreSearchableBeyondOldTopForty() {
        List<Product> selected = service.selectCatalog("想买 Go2 的电池配件", List.of(), crowdedCatalog());
        assertEquals(62L, selected.get(0).id);
        assertTrue(selected.stream().anyMatch(x -> x.id == 61));
        assertEquals(40, selected.size());
    }
    @Test void followupUsesRecentUserContextAndIgnoresDisabledProducts() {
        List<Product> items = crowdedCatalog(); items.get(61).enabled = false;
        List<Product> selected = service.selectCatalog("有配件吗？", List.of(
                Map.of("role", "user", "content", "我想买大疆 Air 3S")), items);
        assertTrue(selected.subList(0, 3).stream().anyMatch(x -> x.id == 64));
        assertTrue(selected.stream().noneMatch(x -> x.id == 62));
    }
    @Test void genericQueryProvidesCategoryDiversity() {
        List<Product> selected = service.selectCatalog("帮我选一下", List.of(), crowdedCatalog());
        assertTrue(selected.stream().anyMatch(x -> x.id == 61));
        assertTrue(selected.stream().anyMatch(x -> x.id == 63));
        assertTrue(selected.stream().anyMatch(x -> "ACCESSORY".equals(x.type)));
    }
    @Test void currentCategoryOverridesUnrelatedHistoricalCategory() {
        List<Product> selected = service.selectCatalog("现在想看航拍无人机", List.of(
                Map.of("role", "user", "content", "之前想买扫地机器人")), crowdedCatalog());
        assertEquals(63L, selected.get(0).id);
        assertTrue(service.selectCatalog("", null, List.of()).isEmpty());
    }
    @Test void budgetRanksAffordableOptionsWithoutDroppingExactModel() {
        Product costly = product(1, "旗舰扫地机器人", "地面清洁", "ROBOT", 1000);
        costly.price = new BigDecimal("6999");
        Product affordable = product(2, "基础扫地机器人", "地面清洁", "ROBOT", 1);
        affordable.price = new BigDecimal("2999");
        List<Product> selected = service.selectCatalog("预算 3 千，想看地板清洁", null, List.of(costly, affordable));
        assertEquals(2L, selected.get(0).id);
        assertEquals(1L, service.selectCatalog("旗舰扫地机器人，预算3000", null, List.of(costly, affordable)).get(0).id);
    }
    @Test void structuredRecommendationsDoNotTreatComparedNamesAsRecommendations() {
        var reply = service.parseReply("{\"answer\":\"推荐 A，不推荐 B。\",\"recommendedProductIds\":[1,1,-2,\"3\",99999999999999999999999,4,5,6]}");
        assertEquals(List.of(1L, 4L, 5L), reply.productIds());
        assertEquals("推荐 A，不推荐 B。", reply.answer());
        assertTrue(service.parseReply("{\"answer\":\"请补充预算\",\"recommendedProductIds\":[]}").productIds().isEmpty());
        assertThrows(com.xiaozao.robotmall.common.BizException.class, () -> service.parseReply("not json"));
        assertThrows(com.xiaozao.robotmall.common.BizException.class, () -> service.parseReply("{\"answer\":\"\",\"recommendedProductIds\":[]}"));
        assertThrows(com.xiaozao.robotmall.common.BizException.class, () -> service.parseReply("{\"answer\":\"缺字段\"}"));
    }
}
