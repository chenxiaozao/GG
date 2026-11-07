package com.xiaozao.robotmall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.service.*;
import com.xiaozao.robotmall.entity.Product;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTests {
    private final ObjectMapper mapper=new ObjectMapper();
    private final RecommendationEngine engine=new RecommendationEngine(mapper);
    private Product product(long id,int price,int stock,String params) {
        Product p=new Product();p.id=id;p.name="测试商品"+id;p.type="ROBOT";p.categoryName="地面清洁";
        p.price=BigDecimal.valueOf(price);p.stock=stock;p.parametersJson=params;p.enduranceMinutes=120;return p;
    }
    @Test void structuredAndConversationShareConstraintsAndEvidence() {
        List<Product> catalog=List.of(product(1,2000,5,"{\"基站能力\":\"集尘/洗拖布\"}"),
                product(2,2500,0,"{\"基站能力\":\"集尘\"}"),product(3,4000,5,"{\"基站能力\":\"集尘\"}"),
                product(4,1800,5,"{\"自动集尘\":\"不支持\"}"),product(5,1500,5,"{}"));
        var a=engine.structured(Map.of("scene","cleaning","budget",3000,"requiredFeatures",List.of("自动集尘")));
        var b=engine.conversation("扫地机器人，预算3000，必须自动集尘",List.of());
        assertEquals(List.of(1L),engine.eligible(catalog,a).stream().map(p->p.id).toList());
        assertEquals(engine.eligible(catalog,a),engine.eligible(catalog,b));
        assertTrue(engine.rank(catalog,a).get(0).get("reasons").toString().contains("基站能力"));
    }
    @Test void unsupportedRequirementsAndMissingParametersNeverPass() {
        var need=engine.conversation("预算3000，扫地机器人必须能爬楼梯",List.of());
        assertTrue(engine.eligible(List.of(product(1,1000,5,"{}")),need).isEmpty());
        var duration=engine.conversation("扫地机器人，续航至少150分钟",List.of());
        assertTrue(engine.eligible(List.of(product(1,1000,5,"{}")),duration).isEmpty());
        var followup=engine.conversation("预算改为2000",List.of(Map.of("role","user","content","扫地机器人预算5000")));
        assertEquals(new BigDecimal("2000"),followup.budget());
        assertEquals("地面清洁",followup.category());
    }
    @Test void deterministicConstraintBenchmark() throws Exception {
        var root=mapper.readTree(getClass().getResourceAsStream("/recommendation-cases.json"));
        List<Product> catalog=new ArrayList<>();
        for(var row:root.path("catalog")) {Product p=product(row.path("id").asLong(),row.path("price").asInt(),row.path("stock").asInt(),row.path("params").toString());
            p.categoryName=row.path("category").asText();p.enabled=row.path("enabled").asBoolean(true);p.enduranceMinutes=row.path("endurance").asInt(120);catalog.add(p);}
        List<Map<String,Object>> results=new ArrayList<>();
        int baselineViolations=0, baselineReturned=0, ruleReturned=0;
        for(var row:root.path("cases")) {
            var need=engine.conversation(row.path("question").asText(),List.of());
            List<Long> expected=new ArrayList<>();row.path("expected").forEach(x->expected.add(x.asLong()));
            List<Long> actual=engine.eligible(catalog,need).stream().map(p->p.id).sorted().toList();
            assertEquals(expected,actual,row.path("id").asText());
            var baseline=catalog.stream().sorted(java.util.Comparator.comparing(p->p.price)).limit(3).map(p->p.id).toList();
            baselineReturned+=baseline.size();
            baselineViolations+=(int)baseline.stream().filter(id->!expected.contains(id)).count();
            ruleReturned+=actual.size();
            results.add(Map.of("id",row.path("id").asText(),"expected",expected,"actual",actual,"unconstrainedPriceBaseline",baseline,"pass",true));
        }
        Files.createDirectories(Path.of("target"));
        mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("target/recommendation-evaluation.json").toFile(),
            Map.of("ruleVersion",RecommendationEngine.VERSION,"scope","人工构造约束用例，不是用户满意度、模型效果或市场真实性实验", "cases",results,
                "baseline",Map.of("name","无约束价格升序前3项","returned",baselineReturned,"constraintViolations",baselineViolations),
                "rules",Map.of("returned",ruleReturned,"constraintViolations",0)));
    }
}
