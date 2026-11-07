package com.xiaozao.robotmall.controller;

import java.math.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.*;
import com.xiaozao.robotmall.entity.*;
import com.xiaozao.robotmall.repository.*;
import com.xiaozao.robotmall.service.*;

@RestController
@RequestMapping("/api")
public class AdvisorController {
    private final ProductRepository products;
    private final CategoryRepository cats;
    private final BrandRepository brands;
    private final DeepSeekAdvisorService deepSeek;
    private final CatalogMediaService media;
    private final AuthService auth;
    private final AiQuotaService quota;
    private final RecommendationEngine engine;
    private final ShoppingPreferenceService preferences;
    public AdvisorController(ProductRepository products, CategoryRepository cats, BrandRepository brands, DeepSeekAdvisorService deepSeek, CatalogMediaService media, AuthService auth, AiQuotaService quota, ObjectMapper mapper, ShoppingPreferenceService preferences) {
        this.products=products;this.cats=cats;this.brands=brands;this.deepSeek=deepSeek;this.media=media;this.auth=auth;this.quota=quota;this.engine=new RecommendationEngine(mapper);
        this.preferences=preferences;
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

    @GetMapping("/advisor/ai-status")
    public ApiResponse<?> advisorAiStatus() {
        return ApiResponse.ok(Map.of("configured", deepSeek.configured(), "provider", "DeepSeek", "model", deepSeek.modelName()));
    }

    @PostMapping("/advisor/chat")
    public ApiResponse<?> advisorChat(@RequestHeader("X-Token") String token, @RequestBody Map<String, Object> body) {
        String question = Objects.toString(body.get("question"), "");
        if (question.isBlank() || question.length() > 800) throw new BizException("问题须为 1 至 800 字");
        List<Map<String, String>> history = new ArrayList<>();
        Object historyValue = body.get("history");
        if (historyValue instanceof List<?> list) {
            for (Object value : list.stream().skip(Math.max(0, list.size() - 12)).toList()) {
                if (value instanceof Map<?, ?> row) {
                    if (Objects.toString(row.get("content"), "").length() > 4000)
                        throw new BizException("对话历史过长，请开始新的对话");
                    history.add(Map.of("role", Objects.toString(row.get("role"), "user"),
                            "content", Objects.toString(row.get("content"), "")));
                }
            }
        }
        String local=AdvisorScope.localReply(question,history);
        if(local!=null)return ApiResponse.ok(Map.of("answer",local,"products",List.of(),"provider","选购边界","scopeRestricted",true));
        User current=auth.current(token);
        List<Product> available = products.findAll().stream().filter(x -> x.enabled).toList();
        if(body.get("contextProductIds") instanceof List<?> ids && !ids.isEmpty()) {
            if(ids.size()>4)throw new BizException("最多携带4款候选商品");
            Set<Long> contextIds=new LinkedHashSet<>();
            for(Object id:ids)try{contextIds.add(Long.valueOf(id.toString()));}catch(Exception e){throw new BizException("候选商品编号无效");}
            available=available.stream().filter(p->contextIds.contains(p.id)).toList();
            if(available.size()!=contextIds.size())throw new BizException("部分候选商品不存在或已下架，请重新选择");
        }
        available.forEach(this::enrich);
        List<Product> catalog = deepSeek.selectCatalog(question, history, available);
        RecommendationEngine.Need need = engine.conversation(question, history);
        preferences.capture(current.id,engine.conversation(question,List.of()),question);
        if (catalog.isEmpty()) return ApiResponse.ok(Map.of("answer", "当前目录没有同时满足类别、预算、库存及必要功能证据的商品。请调整条件，或补充你最看重的功能；不会自动放宽要求。", "products", List.of(), "provider", "规则校验", "ruleVersion", RecommendationEngine.VERSION));
        if(!quota.acquire(current.id))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"AI咨询额度已达上限");
        List<Map<String,String>> redactedHistory=history.stream().map(row->Map.of("role",row.get("role"),"content",AdvisorScope.redactContacts(row.get("content")))).toList();
        DeepSeekAdvisorService.AdvisorReply reply = deepSeek.chat(AdvisorScope.redactContacts(question), catalog, redactedHistory);
        Map<Long, Product> catalogById = catalog.stream().collect(Collectors.toMap(product -> product.id, product -> product));
        List<Product> referencedProducts = reply.productIds().stream().map(catalogById::get)
                .filter(Objects::nonNull).filter(x -> x.stock > 0).toList();
        boolean invalidReference = referencedProducts.size() != reply.productIds().size();
        String answer = invalidReference ? "AI 返回了不符合目录或条件的商品编号，结果已拦截。请重试或使用按条件推荐。" : reply.answer();
        return ApiResponse.ok(Map.of("answer", answer, "products", invalidReference ? List.of() : referencedProducts,
                "provider", "DeepSeek", "ruleVersion", RecommendationEngine.VERSION,
                "validatedRequirements", need, "evidence", engine.rank(referencedProducts, need),
                "notice", "商品卡片通过规则筛选；模型正文仍可能有误，以卡片参数证据为准"));
    }

    @GetMapping("/advisor/quota")
    public ApiResponse<?> quota(@RequestHeader("X-Token") String token) {
        return ApiResponse.ok(quota.status(auth.current(token).id));
    }

    /** Compatibility endpoint; response stays a product list, rules are no longer duplicated. */
    @PostMapping("/advisor")
    public ApiResponse<?> legacyAdvisor(@RequestBody Map<String,Object> body) {
        String scene=Objects.toString(body.get("scene"),"");
        if("清洁".equals(scene)||"家用清洁".equals(scene)) {scene="cleaning";body=new HashMap<>(body);body.put("scene",scene);}
        RecommendationEngine.Need need=RecommendationEngine.SCENES.containsKey(scene) ? engine.structured(body)
                : engine.conversation(scene+(body.containsKey("budget")?" 预算"+body.get("budget"):""),List.of());
        List<Product> catalog=products.findAll();catalog.forEach(this::enrich);
        return ApiResponse.ok(engine.rank(catalog,need).stream().limit(8).map(row->row.get("product")).toList());
    }

    @PostMapping("/advisor/recommend")
    public ApiResponse<?> advisorRecommend(@RequestBody Map<String,Object> body) {
        RecommendationEngine.Need need=engine.structured(body);
        List<Product> catalog=products.findAll();catalog.forEach(this::enrich);
        List<Map<String,Object>> ranked=engine.rank(catalog,need);
        long unknown=catalog.stream().filter(p->p.enabled&&need.category().equals(p.categoryName))
            .filter(p->engine.rejectionReasons(p,need).stream().anyMatch(reason->reason.contains("没有数据")||reason.contains("缺少参数证据"))).count();
        return ApiResponse.ok(Map.of("categoryName",need.category(),"budget",need.budget(),"items",ranked.stream().limit(6).toList(),
            "ruleVersion",RecommendationEngine.VERSION,"unverifiedCount",unknown,
            "summary",ranked.isEmpty()?"没有满足全部硬条件的商品；未擅自放宽预算或功能要求":"按类别、预算、库存和必要功能参数筛选。规则分不是正确率；空间与经验不作无依据加分"));
    }

}
