package com.xiaozao.robotmall.service;

import java.net.URI;
import java.math.BigDecimal;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.Product;

@Service
public class DeepSeekAdvisorService {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @Value("${deepseek.api-key:}") private String apiKey;
    @Value("${deepseek.base-url:https://api.deepseek.com}") private String baseUrl;
    @Value("${deepseek.model:deepseek-chat}") private String model;

    public DeepSeekAdvisorService(ObjectMapper mapper) { this.mapper = mapper; }

    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    public String modelName() { return model; }

    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "地面清洁", List.of("扫地", "扫拖", "拖地", "毛发", "养宠", "地板", "地面清洁"),
            "擦窗机器人", List.of("擦窗", "窗户", "玻璃"),
            "庭院与泳池", List.of("庭院", "割草", "草坪", "泳池", "游泳池"),
            "陪伴娱乐", List.of("陪伴", "娱乐", "宠物机器人"),
            "航拍无人机", List.of("航拍", "无人机", "飞行", "摄影"),
            "教育编程", List.of("教育", "编程", "学习", "积木"),
            "四足机器人", List.of("四足", "机器狗", "go2", "b2"),
            "人形机器人", List.of("人形", "双足", "humanoid"),
            "商用服务", List.of("商用", "配送", "送餐", "迎宾"));

    private static String normalized(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]", "");
    }

    private static Set<String> categories(String query) {
        Set<String> result = new HashSet<>();
        CATEGORY_WORDS.forEach((category, words) -> {
            if (words.stream().anyMatch(query::contains)) result.add(category);
        });
        return result;
    }

    private static BigDecimal budget(String question) {
        Matcher matcher = Pattern.compile("(?:预算|不超过|以内|最多|上限)\\s*[约为是:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*(万|千|[kK])?")
                .matcher(Objects.toString(question, ""));
        if (!matcher.find()) return null;
        BigDecimal value = new BigDecimal(matcher.group(1));
        String unit = matcher.group(2);
        if ("万".equals(unit)) value = value.multiply(new BigDecimal("10000"));
        else if (unit != null) value = value.multiply(new BigDecimal("1000"));
        return value.signum() > 0 ? value : null;
    }

    private static int relevance(Product product, String query, Set<String> preferred) {
        if (query.isBlank()) return 0;
        String name = normalized(product.name);
        int score = 0;
        if (!name.isBlank() && query.contains(name)) score += 180;
        String brand = normalized(product.brandName);
        if (!brand.isBlank() && query.contains(brand)) score += 24;
        if (preferred.contains(product.categoryName)) score += 32;
        boolean accessoryQuestion = Pattern.compile("配件|电池|耗材|滤网|拖布|边刷|桨叶|充电器|底座").matcher(query).find();
        if (accessoryQuestion && "ACCESSORY".equals(product.type)) score += 30;
        // Model tokens survive queries such as “Go2 的电池” or “Air 3S 配件”.
        Matcher tokens = Pattern.compile("[a-z0-9]+(?:[a-z0-9]+)*").matcher(name);
        while (tokens.find()) {
            String token = tokens.group();
            if (token.length() >= 2 && query.contains(token)) score += token.matches(".*\\d.*") ? 65 : 10;
        }
        Set<String> matched = new HashSet<>();
        Matcher chinese = Pattern.compile("[\\p{IsHan}]+").matcher(name);
        while (chinese.find()) {
            String word = chinese.group();
            for (int length = 2; length <= Math.min(5, word.length()); length++) {
                for (int start = 0; start + length <= word.length(); start++) {
                    String part = word.substring(start, start + length);
                    if (query.contains(part) && matched.add(part)) score += length;
                }
            }
        }
        return score;
    }

    /** Search every enabled robot and accessory before bounding the model context. */
    public List<Product> selectCatalog(String question, List<Map<String, String>> history, List<Product> available) {
        String query = normalized(question);
        String previous = history == null ? "" : history.stream()
                .filter(x -> "user".equals(x.get("role"))).skip(Math.max(0, history.stream()
                        .filter(x -> "user".equals(x.get("role"))).count() - 3))
                .map(x -> normalized(x.get("content"))).reduce("", (a, b) -> a + " " + b);
        Set<String> preferred = categories(query);
        if (preferred.isEmpty()) preferred.addAll(categories(previous));
        final Set<String> selectedCategories = preferred;
        BigDecimal maximum = budget(question);
        if (maximum == null) maximum = budget(previous);
        final BigDecimal selectedBudget = maximum;
        Map<Long, Integer> scores = new HashMap<>();
        available.stream().filter(x -> x.enabled).forEach(product -> scores.put(product.id,
                4 * relevance(product, query, selectedCategories) + relevance(product, previous, selectedCategories)
                        + (selectedBudget != null && product.price != null && product.price.compareTo(selectedBudget) <= 0 ? 12 : 0)));
        List<Product> ranked = available.stream().filter(x -> x.enabled)
                .sorted(Comparator.comparingInt((Product x) -> scores.get(x.id)).reversed()
                        .thenComparing(Comparator.comparingInt((Product x) -> x.sales).reversed())
                        .thenComparing(x -> x.id)).toList();
        if (scores.values().stream().anyMatch(x -> x > 0)) return ranked.stream().limit(40).toList();
        // An unspecified question gets category/type diversity, not only bestseller cleaning robots.
        Map<String, List<Product>> groups = ranked.stream().collect(java.util.stream.Collectors.groupingBy(
                x -> Objects.toString(x.categoryName, "其他") + "/" + Objects.toString(x.type, "ROBOT"), LinkedHashMap::new,
                java.util.stream.Collectors.toList()));
        List<Product> result = new ArrayList<>();
        for (int index = 0; result.size() < 40; index++) {
            boolean added = false;
            for (List<Product> group : groups.values()) {
                if (index < group.size() && result.size() < 40) { result.add(group.get(index)); added = true; }
            }
            if (!added) break;
        }
        return List.copyOf(result);
    }

    public record AdvisorReply(String answer, List<Long> productIds) {}

    public AdvisorReply parseReply(String content) {
        try {
            JsonNode root = mapper.readTree(content);
            if (!root.isObject() || !root.path("answer").isTextual() || !root.path("recommendedProductIds").isArray()) {
                throw new BizException("AI 回答格式不完整，请重试");
            }
            String answer = root.path("answer").asText().trim();
            if (answer.isBlank()) throw new BizException("AI 服务未返回有效内容");
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (JsonNode id : root.path("recommendedProductIds")) {
                if (id.isIntegralNumber() && id.canConvertToLong() && id.asLong() > 0 && ids.size() < 3) ids.add(id.asLong());
            }
            return new AdvisorReply(answer, List.copyOf(ids));
        } catch (BizException error) { throw error; }
        catch (Exception error) { throw new BizException("AI 回答格式异常，请重试"); }
    }

    public AdvisorReply chat(String question, List<Product> catalog, List<Map<String, String>> history) {
        if (!configured()) throw new BizException("AI 顾问尚未配置 DeepSeek Token，请先在启动环境中设置 DEEPSEEK_API_KEY");
        if (question == null || question.isBlank()) throw new BizException("请输入想咨询的选购问题");
        if (question.length() > 800) throw new BizException("单次咨询不能超过 800 字");
        String catalogText = catalog.stream().limit(40).map(p -> String.format(Locale.ROOT,
                "ID:%d | %s | 类型:%s | 分类:%s | 品牌:%s | 演示售价:%.2f | 库存:%d | 续航:%s分钟 | 载重:%skg | %s | 参数:%s",
                p.id, p.name, p.type, Objects.toString(p.categoryName, ""), Objects.toString(p.brandName, ""), p.price, p.stock,
                Objects.toString(p.enduranceMinutes, "-"), Objects.toString(p.payloadKg, "-"), Objects.toString(p.subtitle, ""),
                Objects.toString(p.parametersJson, "{}")))
                .reduce((a, b) -> a + "\n" + b).orElse("暂无商品");
        String system = "你是小造智械商城的机器人选购顾问。只能依据下方商城目录回答，不得编造商品、价格或参数。"
                + "信息不足时明确说明，并提出一个最有价值的追问。推荐时给出 1～3 款，说明适合理由和取舍。"
                + "返回严格 JSON 对象，例如 {\"answer\":\"推荐理由和取舍\",\"recommendedProductIds\":[12,34]}。"
                + "answer 是供用户阅读的中文正文，不包含 JSON 或商品 ID 标记。recommendedProductIds 只放你本轮正面推荐的 1～3 款商品 ID。"
                + "仅用于比较、提及、缺货、超预算或明确不推荐的商品不得放入推荐 ID 列表；无适合商品或只需追问时返回空数组。"
                + "目录是按当前咨询从全站在售机器人和配件检索出的候选，不等于全部市场商品。缺货商品须明确提示，不作为可立即购买的首选。"
                + "配件名称或品牌相同不能证明兼容；没有官方适配依据时不得保证兼容。价格、库存及交易均为毕设演示数据。"
                + "不宣称代替官方兼容性、安全或医疗意见。\n\n商城目录：\n" + catalogText;
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        if (history != null) history.stream().skip(Math.max(0, history.size() - 6)).forEach(item -> {
            String role = "assistant".equals(item.get("role")) ? "assistant" : "user";
            String content = Objects.toString(item.get("content"), "");
            if (!content.isBlank() && content.length() <= 2000) messages.add(Map.of("role", role, "content", content));
        });
        messages.add(Map.of("role", "user", "content", question.trim()));
        try {
            String json = mapper.writeValueAsString(Map.of("model", model, "messages", messages, "temperature", 0.3,
                    "max_tokens", 1600, "response_format", Map.of("type", "json_object"),
                    "thinking", Map.of("type", "disabled")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(35)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey).POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException("AI 服务暂时无法回答（HTTP " + response.statusCode() + "）");
            }
            JsonNode root = mapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (answer.isBlank()) throw new BizException("AI 服务未返回有效内容");
            return parseReply(answer);
        } catch (BizException error) { throw error; }
        catch (java.net.http.HttpTimeoutException error) { throw new BizException("AI 服务响应超时，请稍后重试"); }
        catch (Exception error) { throw new BizException("AI 顾问连接失败，请检查网络和 Token 配置"); }
    }
}
