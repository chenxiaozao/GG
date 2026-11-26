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

/** 提供规则推荐、对话助选和额度状态接口。 */
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
    private final AdvisorHistoryService conversations;

    /** 初始化当前对象及其依赖。 */
    public AdvisorController(
            ProductRepository products,
            CategoryRepository cats,
            BrandRepository brands,
            DeepSeekAdvisorService deepSeek,
            CatalogMediaService media,
            AuthService auth,
            AiQuotaService quota,
            ObjectMapper mapper,
            ShoppingPreferenceService preferences,
            AdvisorHistoryService conversations) {
        this.products = products;
        this.cats = cats;
        this.brands = brands;
        this.deepSeek = deepSeek;
        this.media = media;
        this.auth = auth;
        this.quota = quota;
        this.engine = new RecommendationEngine(mapper);
        this.preferences = preferences;
        this.conversations = conversations;
    }

    /** 读取当前账号保存的选购对话，不接收外部账号编号。 */
    @GetMapping("/advisor/history")
    public ApiResponse<?> history(@RequestHeader("X-Token") String token) {
        return ApiResponse.ok(conversations.list(auth.current(token).id));
    }

    /** 清除当前账号的选购对话。 */
    @DeleteMapping("/advisor/history")
    public ApiResponse<?> clearHistory(@RequestHeader("X-Token") String token) {
        conversations.clear(auth.current(token).id);
        return ApiResponse.ok();
    }

    /** 列出登录账号自己的对话窗口。 */
    @GetMapping("/advisor/threads")
    public ApiResponse<?> threads(@RequestHeader("X-Token") String token) {
        return ApiResponse.ok(conversations.summaries(auth.current(token).id));
    }

    /** 创建无历史的新窗口，可保留从其他页面带来的商品候选。 */
    @PostMapping("/advisor/threads")
    public ApiResponse<?> createThread(
            @RequestHeader("X-Token") String token, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(
                conversations.create(
                        auth.current(token).id, parseCandidates(body.get("contextProductIds"))));
    }

    /** 只按当前账号读取指定窗口。 */
    @GetMapping("/advisor/threads/{id}")
    public ApiResponse<?> thread(@RequestHeader("X-Token") String token, @PathVariable String id) {
        return ApiResponse.ok(conversations.detail(auth.current(token).id, id));
    }

    /** 取消指定窗口的商品范围，不删除对话。 */
    @DeleteMapping("/advisor/threads/{id}/context")
    public ApiResponse<?> clearThreadContext(
            @RequestHeader("X-Token") String token, @PathVariable String id) {
        conversations.clearCandidates(auth.current(token).id, id);
        return ApiResponse.ok();
    }

    /** 删除指定窗口，不影响同账号的其他窗口。 */
    @DeleteMapping("/advisor/threads/{id}")
    public ApiResponse<?> deleteThread(
            @RequestHeader("X-Token") String token, @PathVariable String id) {
        conversations.delete(auth.current(token).id, id);
        return ApiResponse.ok();
    }

    /** 统一校验候选编号和数量。 */
    private Set<Long> parseCandidates(Object value) {
        var result = new LinkedHashSet<Long>();
        if (value == null) return result;
        if (!(value instanceof List<?> ids)) throw new BizException("候选商品须为数组");
        if (ids.size() > 4) throw new BizException("最多携带4款候选商品");
        for (Object id : ids)
            try {
                long parsed = Long.parseLong(Objects.toString(id, ""));
                if (parsed <= 0) throw new NumberFormatException();
                result.add(parsed);
            } catch (NumberFormatException e) {
                throw new BizException("候选商品编号无效");
            }
        return result;
    }

    /** 为商品补媒体、分类和品牌名称。 */
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

    /** 返回密钥是否配置及模型名称，不检查外部服务是否可用。 */
    @GetMapping("/advisor/ai-status")
    public ApiResponse<?> advisorAiStatus() {
        return ApiResponse.ok(
                Map.of(
                        "configured",
                        deepSeek.configured(),
                        "provider",
                        "DeepSeek",
                        "model",
                        deepSeek.modelName()));
    }

    /** 校验问题、先做本地范围判断和目录筛选，再按额度请求模型并核对推荐编号。 */
    @PostMapping("/advisor/chat")
    public ApiResponse<?> advisorChat(
            @RequestHeader("X-Token") String token, @RequestBody Map<String, Object> body) {
        String question = Objects.toString(body.get("question"), "");
        if (question.isBlank() || question.length() > 800) throw new BizException("问题须为 1 至 800 字");
        User current = auth.current(token);
        String threadId = Objects.toString(body.get("threadId"), "").strip();
        if (threadId.isEmpty()) threadId = null;
        var thread = threadId == null ? null : conversations.detail(current.id, threadId);
        var saved = thread == null ? conversations.list(current.id) : thread.messages();
        Set<Long> contextIds =
                thread == null
                        ? parseCandidates(body.get("contextProductIds"))
                        : new LinkedHashSet<>(thread.contextProductIds());
        List<Map<String, String>> history = new ArrayList<>();
        Object historyValue = body.get("history");
        if (thread == null && historyValue instanceof List<?> list) {
            for (Object value : list.stream().skip(Math.max(0, list.size() - 12)).toList()) {
                if (value instanceof Map<?, ?> row) {
                    if (Objects.toString(row.get("content"), "").length() > 4000)
                        throw new BizException("对话历史过长，请开始新的对话");
                    history.add(
                            Map.of(
                                    "role",
                                    Objects.toString(row.get("role"), "user"),
                                    "content",
                                    Objects.toString(row.get("content"), "")));
                }
            }
        }
        /** 明确查询历史时只从本人数据库记录取回，不把记录送给模型。 */
        if (recallRequest(question)) {
            String last =
                    saved.stream()
                            .filter(m -> "user".equals(m.role()))
                            .reduce((a, b) -> b)
                            .map(AdvisorHistoryService.Message::content)
                            .orElse("");
            return ApiResponse.ok(
                    Map.of(
                            "answer",
                            last.isBlank()
                                    ? "你还没有保存的选购咨询。你现在想看哪类机器人？"
                                    : "你上次问的是：「" + last + "」。想继续这个需求，还是换一个用途或预算？",
                            "products",
                            List.of(),
                            "provider",
                            "咨询记录",
                            "previousQuestion",
                            last));
        }
        if (thread != null || !body.containsKey("history")) {
            saved.stream()
                    .map(m -> Map.of("role", m.role(), "content", m.content()))
                    .forEach(history::add);
            if (history.size() > 6)
                history = new ArrayList<>(history.subList(history.size() - 6, history.size()));
        }
        /** 领域判断先于商品查询，直接回复不会申请模型额度。 */
        String local = AdvisorScope.localReply(question, history, !contextIds.isEmpty());
        if (local != null)
            return ApiResponse.ok(
                    Map.of(
                            "answer",
                            local,
                            "products",
                            List.of(),
                            "provider",
                            "选购边界",
                            "scopeRestricted",
                            true));
        List<Product> available = products.findAll().stream().filter(x -> x.enabled).toList();
        if (!contextIds.isEmpty()) {
            available = available.stream().filter(p -> contextIds.contains(p.id)).toList();
            if (available.size() != contextIds.size())
                throw new BizException("部分候选商品不存在或已下架，请重新选择");
        }
        available.forEach(this::enrich);
        RecommendationEngine.Need need = engine.conversation(question, history);
        AdvisorSceneGuidance.Guidance guidance =
                AdvisorSceneGuidance.forCategory(need.category(), question, history);
        preferences.capture(current.id, engine.conversation(question, List.of()), question);
        List<String> pending =
                need.requiredFeatures().stream().filter(x -> x.startsWith("待确认")).toList();
        if (!pending.isEmpty())
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    List.of(),
                    "我识别到"
                            + (need.category() == null ? "这是选购咨询" : "你在看「" + need.category() + "」")
                            + "，先确认这一点："
                            + pending.get(0)
                            + "。",
                    "规则追问");
        if ("庭院与泳池".equals(need.category())
                && !need.accessory()
                && need.outdoorTask() == RecommendationEngine.OutdoorTask.UNSPECIFIED
                && contextIds.isEmpty())
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    List.of(),
                    "割草机器人和泳池清洁机不是同一种设备。你要解决的是草坪割草，还是泳池清洁？",
                    "规则追问");
        List<Product> catalog = deepSeek.selectCatalog(question, history, available);
        /** 已进入选购范围但无合格候选时，在申请额度之前结束。 */
        if (catalog.isEmpty())
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    List.of(),
                    "在"
                            + (contextIds.isEmpty() ? "商城目录" : "你选的这几款")
                            + "中，暂未找到同时符合现有条件的商品。"
                            + (need.budget() == null
                                    ? ""
                                    : "预算仍按 "
                                            + need.budget().stripTrailingZeros().toPlainString()
                                            + " 元核对。")
                            + "你想优先保留预算、品类，还是必须具备的功能？我不会自动放宽条件。",
                    "规则校验");
        if (need.budgetStatus() == RecommendationEngine.BudgetStatus.UNSPECIFIED
                || need.budgetStatus() == RecommendationEngine.BudgetStatus.UNLIMITED)
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    catalog,
                    ruleAdvice(need, guidance),
                    "规则推荐");
        if (!deepSeek.configured())
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    catalog,
                    ruleAdvice(need, guidance),
                    "规则推荐");
        /** 仅对仍需外部模型的咨询申请次数，外部请求在这之后。 */
        if (!quota.acquire(current.id))
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    catalog,
                    "今天的模型咨询额度已用完，仍可查看按你的条件筛出的商品。",
                    "规则推荐");
        List<Map<String, String>> redactedHistory =
                history.stream()
                        .map(
                                row ->
                                        Map.of(
                                                "role",
                                                row.get("role"),
                                                "content",
                                                AdvisorScope.redactContacts(row.get("content"))))
                        .toList();
        DeepSeekAdvisorService.AdvisorReply reply;
        try {
            reply = deepSeek.chat(AdvisorScope.redactContacts(question), catalog, redactedHistory);
        } catch (BizException modelUnavailable) {
            return localRecommendation(
                    current.id,
                    threadId,
                    question,
                    need,
                    catalog,
                    "模型暂时不可用，先看按你的条件筛出的在售商品。",
                    "规则推荐");
        }
        /** 模型给出的编号只能回查本轮候选，正文不逐句校验。 */
        Map<Long, Product> catalogById =
                catalog.stream()
                        .collect(Collectors.toMap(product -> product.id, product -> product));
        List<Product> referencedProducts =
                reply.productIds().stream()
                        .map(catalogById::get)
                        .filter(Objects::nonNull)
                        .filter(x -> x.stock > 0)
                        .toList();
        boolean invalidReference = referencedProducts.size() != reply.productIds().size();
        String answer =
                invalidReference ? "AI 返回了不符合目录或条件的商品编号，结果已拦截。请重试或使用按条件推荐。" : reply.answer();
        conversations.append(current.id, threadId, question, answer);
        return ApiResponse.ok(
                Map.of(
                        "answer",
                        answer,
                        "products",
                        invalidReference ? List.of() : referencedProducts,
                        "provider",
                        "DeepSeek",
                        "ruleVersion",
                        RecommendationEngine.VERSION,
                        "validatedRequirements",
                        need,
                        "evidence",
                        engine.rank(referencedProducts, need),
                        "notice",
                        "商品卡片通过规则筛选；模型正文仍可能有误，以卡片参数证据为准"));
    }

    /** 用已确认的预算状态和使用场景生成规则回复，不重复追问已回答的条件。 */
    private String ruleAdvice(
            RecommendationEngine.Need need, AdvisorSceneGuidance.Guidance guidance) {
        var focus = need.floorFocus();
        var answer = new StringBuilder();
        if (need.budgetStatus() == RecommendationEngine.BudgetStatus.UNLIMITED)
            answer.append("明白，预算不限。");
        if (focus.isEmpty()) {
            answer.append("我先列几款符合目前已知条件、且有货的参考。");
            answer.append(guidance.note());
        } else {
            answer.append("我按你补充的使用场景重新比了一遍有货机型。");
            if (focus.contains(RecommendationEngine.FloorFocus.PET_HAIR))
                answer.append("宠物毛发优先看有明确防缠绕结构的机型，不只看吸力。");
            if (focus.contains(RecommendationEngine.FloorFocus.LARGE_AREA))
                answer.append("大面积清扫优先参考续航和断点续扫资料，但续航不能直接换算成覆盖面积。");
            if (focus.contains(RecommendationEngine.FloorFocus.QUIET))
                answer.append("对安静使用有要求时，优先看有静音模式资料的机型；没有噪声数据就不保证实际效果。");
        }
        if (need.budgetStatus() == RecommendationEngine.BudgetStatus.UNSPECIFIED)
            return answer.append("你的预算最高是多少？").toString();
        if (focus.contains(RecommendationEngine.FloorFocus.LARGE_AREA) && !need.floorAreaStated())
            return answer.append("家里大约多少平方米需要清扫？").toString();
        if (!focus.isEmpty() && !need.floorSurfaceStated())
            return answer.append("家里主要是硬地板，还是也有地毯？").toString();
        if (!guidance.nextQuestion().isBlank())
            return answer.append(guidance.nextQuestion()).toString();
        return answer.append("如果还有最在意的功能，可以继续告诉我。").toString();
    }

    /** 只匹配以回顾咨询为目的的短句，不把夹带其他任务的整段文字当历史查询。 */
    private boolean recallRequest(String question) {
        String text = question.replaceAll("[\\s，,。！？!?]", "");
        return text.matches(
                "(?:请|帮我|我想|能不能|可以|你还记得)?"
                        + "(?:查看|查询|查一下|看看|看一下|回顾|找回|继续)?"
                        + "(?:我)?(?:上次|之前|上一段|刚才|这段)"
                        + "(?:的|问过的|咨询的|问了|问过|问|聊过)?"
                        + "(?:选购咨询|咨询记录|聊天记录|咨询|问题|对话|记录|什么|啥)?"
                        + "(?:是什么|是啥|吗|么)?");
    }

    /** 返回 Java 规则结果并将有效选购对话保存到当前账号。 */
    private ApiResponse<?> localRecommendation(
            long userId,
            String threadId,
            String question,
            RecommendationEngine.Need need,
            List<Product> catalog,
            String answer,
            String provider) {
        List<Map<String, Object>> evidence = engine.rank(catalog, need).stream().limit(3).toList();
        List<Product> cards = evidence.stream().map(row -> (Product) row.get("product")).toList();
        String summary =
                cards.isEmpty()
                        ? answer
                        : answer
                                + " 参考："
                                + evidence.stream()
                                        .map(this::ruleCardSummary)
                                        .collect(Collectors.joining("、"))
                                + "。";
        conversations.append(userId, threadId, question, summary);
        return ApiResponse.ok(
                Map.of(
                        "answer",
                        summary,
                        "products",
                        cards,
                        "provider",
                        provider,
                        "validatedRequirements",
                        need,
                        "evidence",
                        evidence,
                        "ruleVersion",
                        RecommendationEngine.VERSION));
    }

    /** 文本中保留商品价格和场景依据，刷新后仍可回看取舍。 */
    private String ruleCardSummary(Map<String, Object> row) {
        Product product = (Product) row.get("product");
        String related =
                ((List<?>) row.get("reasons"))
                        .stream()
                                .map(Objects::toString)
                                .filter(
                                        reason ->
                                                reason.startsWith("宠物毛发")
                                                        || reason.startsWith("全屋清洁")
                                                        || reason.startsWith("目录续航")
                                                        || reason.startsWith("安静使用"))
                                .limit(2)
                                .collect(Collectors.joining("；"));
        return product.name
                + "（¥"
                + product.price.stripTrailingZeros().toPlainString()
                + (related.isEmpty() ? "" : "；" + related)
                + "）";
    }

    /** 查询当前账号的可用次数。 */
    @GetMapping("/advisor/quota")
    public ApiResponse<?> quota(@RequestHeader("X-Token") String token) {
        return ApiResponse.ok(quota.status(auth.current(token).id));
    }

    /** Compatibility endpoint; response stays a product list, rules are no longer duplicated. */
    /** 兼容旧版推荐调用并返回精简的结果。 */
    @PostMapping("/advisor")
    public ApiResponse<?> legacyAdvisor(@RequestBody Map<String, Object> body) {
        String scene = Objects.toString(body.get("scene"), "");
        if ("清洁".equals(scene) || "家用清洁".equals(scene)) {
            scene = "cleaning";
            body = new HashMap<>(body);
            body.put("scene", scene);
        }
        RecommendationEngine.Need need =
                RecommendationEngine.SCENES.containsKey(scene)
                        ? engine.structured(body)
                        : engine.conversation(
                                scene
                                        + (body.containsKey("budget")
                                                ? " 预算" + body.get("budget")
                                                : ""),
                                List.of());
        List<Product> catalog = products.findAll();
        catalog.forEach(this::enrich);
        return ApiResponse.ok(
                engine.rank(catalog, need).stream()
                        .limit(8)
                        .map(row -> row.get("product"))
                        .toList());
    }

    /** 应用表单条件生成带参数依据的推荐结果。 */
    @PostMapping("/advisor/recommend")
    public ApiResponse<?> advisorRecommend(@RequestBody Map<String, Object> body) {
        RecommendationEngine.Need need = engine.structured(body);
        List<Product> catalog = products.findAll();
        catalog.forEach(this::enrich);
        List<Map<String, Object>> ranked = engine.rank(catalog, need);
        long unknown =
                catalog.stream()
                        .filter(p -> p.enabled && need.category().equals(p.categoryName))
                        .filter(
                                p ->
                                        engine.rejectionReasons(p, need).stream()
                                                .anyMatch(
                                                        reason ->
                                                                reason.contains("没有数据")
                                                                        || reason.contains(
                                                                                "缺少参数证据")))
                        .count();
        return ApiResponse.ok(
                Map.of(
                        "categoryName",
                        need.category(),
                        "budget",
                        need.budget(),
                        "items",
                        ranked.stream().limit(6).toList(),
                        "ruleVersion",
                        RecommendationEngine.VERSION,
                        "unverifiedCount",
                        unknown,
                        "summary",
                        ranked.isEmpty()
                                ? "没有满足全部硬条件的商品；未擅自放宽预算或功能要求"
                                : "按类别、预算、库存和必要功能参数筛选。规则分不是正确率；空间与经验不作无依据加分"));
    }
}
