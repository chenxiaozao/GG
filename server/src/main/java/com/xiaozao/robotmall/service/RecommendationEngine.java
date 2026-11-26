package com.xiaozao.robotmall.service;

import com.fasterxml.jackson.databind.*;
import com.xiaozao.robotmall.entity.Product;
import com.xiaozao.robotmall.common.BizException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Deterministic requirements and evidence, used by both recommendation entry points. */
/** 提取结构化需求、筛选目录并为候选打分。 */
public class RecommendationEngine {
    public static final String VERSION = "rules-2026-11-26-context";
    public static final Map<String, String> SCENES =
            Map.ofEntries(
                    Map.entry("cleaning", "地面清洁"),
                    Map.entry("window", "擦窗机器人"),
                    Map.entry("garden", "庭院与泳池"),
                    Map.entry("companion", "陪伴娱乐"),
                    Map.entry("drone", "航拍无人机"),
                    Map.entry("education", "教育编程"),
                    Map.entry("quadruped", "四足机器人"),
                    Map.entry("humanoid", "人形机器人"),
                    Map.entry("service", "商用服务"));
    private static final Map<String, List<String>> FEATURES =
            Map.of(
                    "自动集尘",
                    List.of("自动集尘", "集尘"),
                    "避障",
                    List.of("避障"),
                    "防缠绕",
                    List.of("防缠绕", "零缠绕"),
                    "拖地",
                    List.of("拖布", "扫拖", "拖地"),
                    "编程",
                    List.of("编程", "python", "scratch"),
                    "遥控",
                    List.of("遥控"));
    private final ObjectMapper mapper;

    /** 初始化当前对象及其依赖。 */
    public RecommendationEngine(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 区分没给预算、明确不限、已给金额和待澄清金额。 */
    public enum BudgetStatus {
        UNSPECIFIED,
        UNLIMITED,
        CAPPED,
        NEEDS_CLARIFICATION
    }

    /** 表示影响地面清洁排序、但不会淘汰商品的选购侧重点。 */
    public enum FloorFocus {
        LARGE_AREA,
        PET_HAIR,
        QUIET
    }

    /** 庭院大类中的具体工作；未知时先询问，不混推两种设备。 */
    public enum OutdoorTask {
        UNSPECIFIED,
        LAWN,
        POOL
    }

    /** 保存分类、预算、硬条件和有依据才加分的使用场景。 */
    public record Need(
            String category,
            BigDecimal budget,
            boolean allowOverBudget,
            List<String> requiredFeatures,
            int minEndurance,
            String priority,
            boolean accessory,
            BudgetStatus budgetStatus,
            List<FloorFocus> floorFocus,
            boolean floorAreaStated,
            boolean floorSurfaceStated,
            OutdoorTask outdoorTask) {}

    /** 校验表单字段并生成筛选条件。 */
    public Need structured(Map<String, Object> body) {
        String category = SCENES.get(Objects.toString(body.get("scene"), ""));
        if (category == null) throw new BizException("请选择有效的商品类别");
        BigDecimal budget;
        try {
            budget = new BigDecimal(Objects.toString(body.get("budget"), ""));
        } catch (Exception e) {
            throw new BizException("预算格式不正确");
        }
        if (budget.compareTo(BigDecimal.valueOf(500)) < 0
                || budget.compareTo(BigDecimal.valueOf(1000000)) > 0)
            throw new BizException("预算须为500至1000000元");
        List<String> required = new ArrayList<>();
        if (body.containsKey("requiredFeatures")
                && !(body.get("requiredFeatures") instanceof List<?>))
            throw new BizException("必要功能须为数组");
        if (body.get("requiredFeatures") instanceof List<?> items)
            for (Object item : items) {
                String feature = Objects.toString(item, "");
                if (!FEATURES.containsKey(feature)) throw new BizException("不支持的必要功能：" + feature);
                if (!required.contains(feature)) required.add(feature);
            }
        if (body.containsKey("minEndurance")
                && (!(body.get("minEndurance") instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < 0
                        || number.doubleValue() > 1440)) throw new BizException("最低续航须为0至1440整数分钟");
        int endurance = body.get("minEndurance") instanceof Number number ? number.intValue() : 0;
        if (endurance < 0 || endurance > 1440) throw new BizException("最低续航须为0至1440分钟");
        String priority = Objects.toString(body.get("priority"), "balanced");
        if (!Set.of("balanced", "value", "performance", "easy").contains(priority))
            throw new BizException("选购侧重点无效");
        return new Need(
                category,
                budget,
                Boolean.TRUE.equals(body.get("allowOverBudget")),
                List.copyOf(required),
                endurance,
                priority,
                false,
                BudgetStatus.CAPPED,
                List.of(),
                false,
                false,
                OutdoorTask.UNSPECIFIED);
    }

    private static final Map<String, String> CATEGORY_WORDS =
            Map.ofEntries(
                    Map.entry("扫地|扫拖|地板|地面清洁|拖地", "地面清洁"),
                    Map.entry("擦窗|窗户|玻璃", "擦窗机器人"),
                    Map.entry("割草|草坪|庭院|泳池|池底|池壁|水线", "庭院与泳池"),
                    Map.entry("陪伴|宠物机器人", "陪伴娱乐"),
                    Map.entry("无人机|航拍|飞行电池|air\\s*3s", "航拍无人机"),
                    Map.entry("编程|教育|scratch|stem", "教育编程"),
                    Map.entry("机器狗|四足|go2", "四足机器人"),
                    Map.entry("人形|双足", "人形机器人"),
                    Map.entry("送餐|迎宾|商用", "商用服务"));

    /** 用 Java 规则从本轮中文和历史提取条件，未调用模型。 */
    public Need conversation(String question, List<Map<String, String>> history) {
        String previous =
                history == null
                        ? ""
                        : history.stream()
                                .filter(x -> "user".equals(x.get("role")))
                                .map(x -> x.getOrDefault("content", ""))
                                .reduce("", (a, b) -> a + "\n" + b);
        String text = question.toLowerCase(Locale.ROOT),
                all = (previous + "\n" + question).toLowerCase(Locale.ROOT);
        Set<String> selected = categories(text);
        Set<String> historicalCategories = categories(previous.toLowerCase(Locale.ROOT));
        boolean newCategory =
                !selected.isEmpty()
                        && !historicalCategories.isEmpty()
                        && Collections.disjoint(selected, historicalCategories);
        if (selected.isEmpty()) selected = historicalCategories;
        if (newCategory) all = text;
        String category = selected.size() == 1 ? selected.iterator().next() : null;
        BudgetChoice budget = budgetChoice(text, !previous.isBlank() && !newCategory);
        if (budget.status() == BudgetStatus.UNSPECIFIED && !newCategory) {
            for (String earlier : previous.split("\\R")) {
                BudgetChoice candidate = budgetChoice(earlier, true);
                if (candidate.status() != BudgetStatus.UNSPECIFIED) budget = candidate;
            }
        }
        List<String> required = new ArrayList<>();
        for (String feature : FEATURES.keySet()) {
            String expression = "(?:必须|需要|要有|支持|必需|一定要)[^。！？\\n]{0,8}" + Pattern.quote(feature);
            String negative = "(?:不需要|不必|不要|无需)[^。！？\\n]{0,8}" + Pattern.quote(feature);
            if (Pattern.compile(expression).matcher(all).find()
                    && !Pattern.compile(negative).matcher(text).find()) required.add(feature);
        }
        // Unknown hard requirements must trigger clarification, never silently disappear.
        Matcher hard = Pattern.compile("(?:必须|一定要|必需)([^，。！？\\n]{1,28})").matcher(text);
        while (hard.find()) {
            String clause = hard.group(1);
            for (String part : clause.split("并且|以及|同时|还要|而且|和|且")) {
                String remainder = part;
                for (String feature : FEATURES.keySet()) remainder = remainder.replace(feature, "");
                for (String words : CATEGORY_WORDS.keySet())
                    remainder = remainder.replaceAll(words, "");
                remainder = remainder.replaceAll("支持|具备|能够|可以|提供|功能|机器人|设备|主机|带有|要有|有|能|的|\\s", "");
                boolean numeric =
                        part.matches(".*续航(?:至少|最低|不低于)\\s*\\d+\\s*分钟")
                                || part.matches(
                                        "(?:预算|价格)(?:不超过|最多)?\\s*\\d+\\s*(?:元|千|万)?(?:以内)?");
                if (!remainder.isBlank() && !numeric) required.add("待确认硬条件：" + part);
            }
        }
        if (budget.status() == BudgetStatus.NEEDS_CLARIFICATION)
            required.add("待确认预算：请告诉我一个明确的最高金额，例如 3000 元");
        Matcher endurance =
                Pattern.compile("(?:续航)[^。！？\\n]{0,6}(?:至少|不低于|最低)\\s*(\\d+)\\s*分钟").matcher(all);
        int minutes = 0;
        while (endurance.find())
            minutes =
                    new java.math.BigInteger(endurance.group(1))
                            .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE))
                            .intValue();
        if (minutes == 0 && Pattern.compile("续航[^。！？\\n]{0,6}(?:至少|最低|不低于)").matcher(text).find())
            required.add("待确认最低续航：请用数字和分钟表达");
        boolean allow =
                Pattern.compile("(?:允许|可以|接受)[^。！？\\n]{0,5}超预算").matcher(all).find()
                        && !Pattern.compile("(?:不能|不接受|不允许|不要)超预算").matcher(text).find();
        return new Need(
                category,
                budget.amount(),
                allow,
                List.copyOf(required),
                minutes,
                all.contains("续航优先") ? "performance" : all.contains("性价比") ? "value" : "balanced",
                Pattern.compile("配件|电池|耗材|滤网|拖布|桨叶").matcher(text).find(),
                budget.status(),
                "地面清洁".equals(category) ? floorFocus(all) : List.of(),
                Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:平方米|平米|平方|㎡|平(?![方米]))")
                        .matcher(all)
                        .find(),
                Pattern.compile("硬地板|木地板|地毯|瓷砖|地砖|地面材质").matcher(all).find(),
                "庭院与泳池".equals(category) ? outdoorTask(text, all) : OutdoorTask.UNSPECIFIED);
    }

    private record BudgetChoice(BudgetStatus status, BigDecimal amount) {}

    /** 只在需求明确区分割草与泳池时收窄候选。 */
    private OutdoorTask outdoorTask(String current, String all) {
        Pattern lawn = Pattern.compile("割草|草坪");
        Pattern pool = Pattern.compile("泳池|池底|池壁|水线");
        String scope = lawn.matcher(current).find() || pool.matcher(current).find() ? current : all;
        boolean wantsLawn = lawn.matcher(scope).find();
        boolean wantsPool = pool.matcher(scope).find();
        if (wantsLawn == wantsPool) return OutdoorTask.UNSPECIFIED;
        return wantsLawn ? OutdoorTask.LAWN : OutdoorTask.POOL;
    }

    /** 按单轮提取预算状态，避免明示不限后又读回旧预算。 */
    private BudgetChoice budgetChoice(String text, boolean acceptShortTurn) {
        BigDecimal amount = readBudget(text);
        if (amount == null && acceptShortTurn && text.matches("\\d{2,7}\\s*(?:元|块钱?)?"))
            amount = readBudget("预算" + text);
        if (amount != null) return new BudgetChoice(BudgetStatus.CAPPED, amount);
        if (Pattern.compile("预算\\s*(?:不设上限|不限|没有上限|无上限|无所谓|不限制)|不限预算|不用考虑价格").matcher(text).find())
            return new BudgetChoice(BudgetStatus.UNLIMITED, null);
        if (text.matches("(?s).*(?:预算|不超过|上限|最多).{0,8}[0-9零〇一二两三四五六七八九十百千万几].*")
                && !text.matches("(?s).*(?:不超过|最多)\\s*\\d+\\s*(?:分钟|小时).*"))
            return new BudgetChoice(BudgetStatus.NEEDS_CLARIFICATION, null);
        return new BudgetChoice(BudgetStatus.UNSPECIFIED, null);
    }

    /** 从家庭清洁场景提取软偏好，优先使用当前明确的否定线索。 */
    private List<FloorFocus> floorFocus(String text) {
        List<FloorFocus> focus = new ArrayList<>();
        if (Pattern.compile("家里.{0,5}大|房子.{0,5}大|大面积|大户型|全屋|(?<!\\d)(?:[1-9]\\d{2,})\\s*(?:平|㎡)")
                .matcher(text)
                .find()) focus.add(FloorFocus.LARGE_AREA);
        if (Pattern.compile("宠物|猫毛|狗毛|养猫|养狗|有猫|有狗|毛发").matcher(text).find()
                && !Pattern.compile("没有宠物|不养宠物|不用考虑毛发|不在意毛发")
                        .matcher(text.substring(text.lastIndexOf('\n') + 1))
                        .find()) focus.add(FloorFocus.PET_HAIR);
        if (Pattern.compile("婴儿|宝宝|午睡|睡觉|静音|低噪|安静|噪音").matcher(text).find()
                && !Pattern.compile("不用考虑噪音|不在意噪音")
                        .matcher(text.substring(text.lastIndexOf('\n') + 1))
                        .find()) focus.add(FloorFocus.QUIET);
        return List.copyOf(focus);
    }

    /** 依据关键词表返回文字命中的商品分类。 */
    private Set<String> categories(String text) {
        Set<String> result = new HashSet<>();
        CATEGORY_WORDS.forEach(
                (words, category) -> {
                    if (Pattern.compile(words, Pattern.CASE_INSENSITIVE).matcher(text).find())
                        result.add(category);
                });
        return result;
    }

    /** 提取可识别的数字金额并统一为元。 */
    private BigDecimal readBudget(String text) {
        if (Pattern.compile(
                        "(?:\\d+(?:\\.\\d+)?|[一二两三四五六七八九][千万]?)\\s*(?:元|块钱?)?\\s*(?:到|至|[-~～—])\\s*[0-9一二两三四五六七八九]|[一二两三四五六七八九]{2}[千万]")
                .matcher(text)
                .find()) return null;
        Matcher matcher =
                Pattern.compile(
                                "(?:预算|不超过|最多|上限)\\s*(?:改为|调整为|增加到|降低到|约为|大约|大概|约|为|是|在|:|：)?\\s*(\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十百千万]+)\\s*(万|千|[kK])?")
                        .matcher(text);
        BigDecimal value = null;
        while (matcher.find()) {
            if (text.substring(matcher.end()).matches("(?s)^\\s*(?:分钟|小时|年|岁).*")) continue;
            value = budgetNumber(matcher.group(1));
            if (value == null) continue;
            String unit = matcher.group(2);
            if ("万".equals(unit)) value = value.multiply(BigDecimal.valueOf(10000));
            else if (unit != null) value = value.multiply(BigDecimal.valueOf(1000));
        }
        if (value == null) {
            matcher =
                    Pattern.compile("(\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十百千万]+)\\s*(?:元|块钱?)?以内")
                            .matcher(text);
            while (matcher.find()) value = budgetNumber(matcher.group(1));
        }
        return value != null && value.signum() > 0 ? value : null;
    }

    /** 把明确的阿拉伯数字或中文金额转换为元；不猜测口语省略的位值。 */
    private BigDecimal budgetNumber(String value) {
        if (value.matches("\\d+(?:\\.\\d+)?")) return new BigDecimal(value);
        long total = 0, section = 0;
        int digit = 0, lastUnit = 10000;
        boolean pending = false;
        for (char c : value.toCharArray()) {
            int number = "零一二三四五六七八九".indexOf(c);
            if (c == '〇') number = 0;
            if (c == '两') number = 2;
            if (number >= 0) {
                if (pending && digit != 0) return null;
                digit = number;
                pending = true;
                continue;
            }
            if (c == '万') {
                if (total > 0 || section + digit == 0) return null;
                total = (section + digit) * 10000;
                section = 0;
                lastUnit = 10000;
            } else {
                int unit = c == '千' ? 1000 : c == '百' ? 100 : 10;
                if (unit >= lastUnit || (!pending && !(unit == 10 && section == 0))) return null;
                section += (pending ? digit : 1) * unit;
                lastUnit = unit;
            }
            digit = 0;
            pending = false;
        }
        if (pending
                && digit > 0
                && (lastUnit >= 100 && section + total > 0)
                && !value.matches(".*[零〇][一二两三四五六七八九]$")) return null;
        return BigDecimal.valueOf(total + section + digit);
    }

    /** Evidence comes from parameter entries, never inferred from a brand or model name. */
    /** 从商品参数键和值找功能依据，跳过否定描述。 */
    public String evidence(Product product, String feature) {
        try {
            JsonNode params = mapper.readTree(Objects.toString(product.parametersJson, "{}"));
            if (!params.isObject()) return null;
            Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String value = entry.getValue().asText("");
                String combined = (entry.getKey() + ":" + value).toLowerCase(Locale.ROOT);
                if (Pattern.compile(
                                "不支持|不具备|不含|不提供|未配备|无此|无避障|无集尘|无防缠绕|无断点续扫|无静音模式|无低噪模式|^.*:(?:无|否|false|none|0)$")
                        .matcher(combined)
                        .find()) continue;
                if (FEATURES.getOrDefault(feature, List.of(feature)).stream()
                        .anyMatch(combined::contains)) return entry.getKey() + "：" + value;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 列出商品不满足的硬条件或缺失证据。 */
    public List<String> rejectionReasons(Product product, Need need) {
        List<String> reasons = new ArrayList<>();
        if (!RecommendationRules.eligible(product, need.budget(), need.allowOverBudget()))
            reasons.add("上架、库存或预算条件不满足");
        if (need.category() != null
                && !need.category().equals(product.categoryName)
                && !(need.accessory() && "ACCESSORY".equals(product.type))) reasons.add("类别不符");
        if (need.category() != null && !need.accessory() && !"ROBOT".equals(product.type))
            reasons.add("当前需要主机而非配件");
        if (need.outdoorTask() != OutdoorTask.UNSPECIFIED) {
            String taskEvidence =
                    Objects.toString(product.subtitle, "")
                            + " "
                            + Objects.toString(product.parametersJson, "");
            String requiredTask = need.outdoorTask() == OutdoorTask.LAWN ? "割草|草坪" : "泳池|池底|池壁|水线";
            if (!Pattern.compile(requiredTask).matcher(taskEvidence).find())
                reasons.add("缺少对应的庭院任务证据");
        }
        if (need.minEndurance() > 0
                && (product.enduranceMinutes == null
                        || product.enduranceMinutes < need.minEndurance()))
            reasons.add("最低续航未满足或没有数据");
        for (String feature : need.requiredFeatures())
            if (evidence(product, feature) == null) reasons.add("必要功能缺少参数证据：" + feature);
        return reasons;
    }

    /** 仅留下通过全部硬条件的商品。 */
    public List<Product> eligible(List<Product> catalog, Need need) {
        return catalog.stream().filter(p -> rejectionReasons(p, need).isEmpty()).toList();
    }

    /** 按侧重点给合格商品评分、附上依据并排序。 */
    public List<Map<String, Object>> rank(List<Product> catalog, Need need) {
        List<Product> eligible = eligible(catalog, need);
        int longest =
                eligible.stream()
                        .mapToInt(p -> p.enduranceMinutes == null ? 0 : p.enduranceMinutes)
                        .max()
                        .orElse(0);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product product : eligible) {
            List<String> evidence = new ArrayList<>();
            double score = 50;
            if (need.budget() != null) {
                double ratio =
                        product.price
                                .divide(need.budget(), 6, java.math.RoundingMode.HALF_UP)
                                .doubleValue();
                score +=
                        ratio <= 1
                                ? 18 - Math.abs(ratio - .78) * 12
                                : Math.max(0, 8 - (ratio - 1) * 35);
                evidence.add(ratio <= 1 ? "价格在预算内" : "用户允许的20%超预算备选");
                if ("value".equals(need.priority())) score += Math.max(0, 12 * (1 - ratio));
            }
            for (String feature : need.requiredFeatures())
                evidence.add(feature + "依据：" + evidence(product, feature));
            if ("performance".equals(need.priority())
                    && longest > 0
                    && product.enduranceMinutes != null) {
                score += 10.0 * product.enduranceMinutes / longest;
                evidence.add("同类目录续航：" + product.enduranceMinutes + "分钟；不代表综合性能");
            }
            if ("easy".equals(need.priority())
                    && product.warranty != null
                    && !product.warranty.isBlank()) {
                score += 3;
                evidence.add("目录售后说明：" + product.warranty);
            }
            score += floorScore(product, need, longest, evidence);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("product", product);
            row.put("rawScore", score);
            row.put("score", Math.round(score));
            row.put(
                    "withinBudget",
                    need.budget() == null || product.price.compareTo(need.budget()) <= 0);
            row.put("reasons", evidence);
            row.put("ruleVersion", VERSION);
            rows.add(row);
        }
        rows.sort(
                Comparator.<Map<String, Object>>comparingDouble(
                                row -> ((Number) row.get("rawScore")).doubleValue())
                        .reversed()
                        .thenComparingLong(row -> ((Product) row.get("product")).id));
        return rows;
    }

    /** 只凭实际参数给场景偏好加分，未知指标不会替商品背书。 */
    private double floorScore(Product product, Need need, int longest, List<String> reasons) {
        double score = 0;
        if (need.floorFocus().contains(FloorFocus.PET_HAIR)) {
            String antiTangle = evidence(product, "防缠绕");
            if (antiTangle != null) {
                score += 18;
                reasons.add("宠物毛发相关参数：" + antiTangle + "；不代表实测清洁效果");
            }
        }
        if (need.floorFocus().contains(FloorFocus.LARGE_AREA)) {
            String resume = evidence(product, "断点续扫");
            if (resume != null) {
                score += 14;
                reasons.add("全屋清洁相关参数：" + resume);
            }
            if (longest > 0 && product.enduranceMinutes != null && product.enduranceMinutes > 0) {
                score += 12.0 * product.enduranceMinutes / longest;
                reasons.add("目录续航 " + product.enduranceMinutes + " 分钟；不能换算为可清扫面积");
            }
        }
        if (need.floorFocus().contains(FloorFocus.QUIET)) {
            String quiet = evidence(product, "静音模式");
            if (quiet == null) quiet = evidence(product, "低噪模式");
            if (quiet != null) {
                score += 8;
                reasons.add("安静使用相关参数：" + quiet + "；不代表实际运行噪声");
            }
        }
        return score;
    }
}
