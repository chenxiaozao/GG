package com.xiaozao.robotmall.service;

import com.fasterxml.jackson.databind.*;
import com.xiaozao.robotmall.entity.Product;
import com.xiaozao.robotmall.common.BizException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Deterministic requirements and evidence, used by both recommendation entry points. */
public class RecommendationEngine {
    public static final String VERSION = "rules-2026-11-05";
    public static final Map<String,String> SCENES = Map.ofEntries(
        Map.entry("cleaning","地面清洁"),Map.entry("window","擦窗机器人"),Map.entry("garden","庭院与泳池"),
        Map.entry("companion","陪伴娱乐"),Map.entry("drone","航拍无人机"),Map.entry("education","教育编程"),
        Map.entry("quadruped","四足机器人"),Map.entry("humanoid","人形机器人"),Map.entry("service","商用服务"));
    private static final Map<String,List<String>> FEATURES = Map.of(
        "自动集尘",List.of("自动集尘","集尘"), "避障",List.of("避障"),
        "防缠绕",List.of("防缠绕","零缠绕"), "拖地",List.of("拖布","扫拖","拖地"),
        "编程",List.of("编程","python","scratch"), "遥控",List.of("遥控"));
    private final ObjectMapper mapper;
    public RecommendationEngine(ObjectMapper mapper) { this.mapper=mapper; }
    public record Need(String category, BigDecimal budget, boolean allowOverBudget,
                       List<String> requiredFeatures, int minEndurance, String priority, boolean accessory) {}
    public Need structured(Map<String,Object> body) {
        String category=SCENES.get(Objects.toString(body.get("scene"),""));
        if(category==null) throw new BizException("请选择有效的商品类别");
        BigDecimal budget;
        try { budget=new BigDecimal(Objects.toString(body.get("budget"),"")); }
        catch(Exception e) { throw new BizException("预算格式不正确"); }
        if(budget.compareTo(BigDecimal.valueOf(500))<0||budget.compareTo(BigDecimal.valueOf(1000000))>0) throw new BizException("预算须为500至1000000元");
        List<String> required=new ArrayList<>();
        if(body.containsKey("requiredFeatures")&&!(body.get("requiredFeatures") instanceof List<?>)) throw new BizException("必要功能须为数组");
        if(body.get("requiredFeatures") instanceof List<?> items) for(Object item:items) {
            String feature=Objects.toString(item,"");
            if(!FEATURES.containsKey(feature)) throw new BizException("不支持的必要功能："+feature);
            if(!required.contains(feature)) required.add(feature);
        }
        if(body.containsKey("minEndurance")&&(!(body.get("minEndurance") instanceof Number number)
                || !Double.isFinite(number.doubleValue()) || number.doubleValue()!=Math.rint(number.doubleValue())
                || number.doubleValue()<0 || number.doubleValue()>1440)) throw new BizException("最低续航须为0至1440整数分钟");
        int endurance=body.get("minEndurance") instanceof Number number?number.intValue():0;
        if(endurance<0||endurance>1440) throw new BizException("最低续航须为0至1440分钟");
        String priority=Objects.toString(body.get("priority"),"balanced");
        if(!Set.of("balanced","value","performance","easy").contains(priority)) throw new BizException("选购侧重点无效");
        return new Need(category,budget,Boolean.TRUE.equals(body.get("allowOverBudget")),List.copyOf(required),endurance,priority,false);
    }
    private static final Map<String,String> CATEGORY_WORDS=Map.ofEntries(
        Map.entry("扫地|扫拖|地板|地面清洁|拖地", "地面清洁"),Map.entry("擦窗|窗户|玻璃", "擦窗机器人"),
        Map.entry("割草|庭院|泳池", "庭院与泳池"),Map.entry("陪伴|宠物机器人", "陪伴娱乐"),
        Map.entry("无人机|航拍|飞行电池|air\\s*3s", "航拍无人机"),Map.entry("编程|教育|孩子", "教育编程"),
        Map.entry("机器狗|四足|go2", "四足机器人"),Map.entry("人形|双足", "人形机器人"),Map.entry("送餐|迎宾|商用", "商用服务"));
    public Need conversation(String question,List<Map<String,String>> history) {
        String previous=history==null?"":history.stream().filter(x->"user".equals(x.get("role"))).map(x->x.getOrDefault("content","")).reduce("",(a,b)->a+"\n"+b);
        String text=question.toLowerCase(Locale.ROOT), all=(previous+"\n"+question).toLowerCase(Locale.ROOT);
        Set<String> selected=categories(text);
        if(selected.isEmpty()) selected=categories(previous.toLowerCase(Locale.ROOT));
        String category=selected.size()==1?selected.iterator().next():null;
        boolean unlimited=text.contains("预算不限")||text.contains("不限预算");
        BigDecimal budget=readBudget(text); if(budget==null&&!unlimited) budget=readBudget(previous);
        List<String> required=new ArrayList<>();
        for(String feature:FEATURES.keySet()) {
            String expression="(?:必须|需要|要有|支持|必需|一定要)[^。！？\\n]{0,8}"+Pattern.quote(feature);
            String negative="(?:不需要|不必|不要|无需)[^。！？\\n]{0,8}"+Pattern.quote(feature);
            if(Pattern.compile(expression).matcher(all).find()&&!Pattern.compile(negative).matcher(text).find()) required.add(feature);
        }
        // Unknown hard requirements must trigger clarification, never silently disappear.
        Matcher hard=Pattern.compile("(?:必须|一定要|必需)([^，。！？\\n]{1,28})").matcher(text);
        while(hard.find()) {
            String clause=hard.group(1);
            for(String part:clause.split("并且|以及|同时|还要|而且|和|且")) {
                String remainder=part;
                for(String feature:FEATURES.keySet())remainder=remainder.replace(feature,"");
                for(String words:CATEGORY_WORDS.keySet())remainder=remainder.replaceAll(words,"");
                remainder=remainder.replaceAll("支持|具备|能够|可以|提供|功能|机器人|设备|主机|带有|要有|有|能|的|\\s","");
                boolean numeric=part.matches(".*续航(?:至少|最低|不低于)\\s*\\d+\\s*分钟")
                    || part.matches("(?:预算|价格)(?:不超过|最多)?\\s*\\d+\\s*(?:元|千|万)?(?:以内)?");
                if(!remainder.isBlank()&&!numeric)required.add("待确认硬条件："+part);
            }
        }
        if(!unlimited&&text.contains("预算")&&readBudget(text)==null)required.add("待确认预算：请用数字和元/千/万表达");
        Matcher endurance=Pattern.compile("(?:续航)[^。！？\\n]{0,6}(?:至少|不低于|最低)\\s*(\\d+)\\s*分钟").matcher(all);
        int minutes=0;while(endurance.find()) minutes=new java.math.BigInteger(endurance.group(1)).min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        if(minutes==0&&Pattern.compile("续航[^。！？\\n]{0,6}(?:至少|最低|不低于)").matcher(text).find()) required.add("待确认最低续航：请用数字和分钟表达");
        boolean allow=Pattern.compile("(?:允许|可以|接受)[^。！？\\n]{0,5}超预算").matcher(all).find()
            && !Pattern.compile("(?:不能|不接受|不允许|不要)超预算").matcher(text).find();
        return new Need(category,budget,allow,List.copyOf(required),minutes,
                all.contains("续航优先")?"performance":all.contains("性价比")?"value":"balanced",
                Pattern.compile("配件|电池|耗材|滤网|拖布|桨叶").matcher(text).find());
    }
    private Set<String> categories(String text) {
        Set<String> result=new HashSet<>();CATEGORY_WORDS.forEach((words,category)->{
            if(Pattern.compile(words,Pattern.CASE_INSENSITIVE).matcher(text).find()) result.add(category);
        });return result;
    }
    private BigDecimal readBudget(String text) {
        Matcher matcher=Pattern.compile("(?:预算|不超过|最多|上限)\\s*(?:改为|调整为|增加到|降低到|约为|为|是|:|：)?\\s*(\\d+(?:\\.\\d+)?)\\s*(万|千|[kK])?").matcher(text);
        BigDecimal value=null;
        while(matcher.find()) { value=new BigDecimal(matcher.group(1));String unit=matcher.group(2);if("万".equals(unit))value=value.multiply(BigDecimal.valueOf(10000));else if(unit!=null)value=value.multiply(BigDecimal.valueOf(1000)); }
        if(value==null) {matcher=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元?以内").matcher(text);while(matcher.find())value=new BigDecimal(matcher.group(1));}
        return value!=null&&value.signum()>0?value:null;
    }
    /** Evidence comes from parameter entries, never inferred from a brand or model name. */
    public String evidence(Product product,String feature) {
        try {
            JsonNode params=mapper.readTree(Objects.toString(product.parametersJson,"{}"));
            if(!params.isObject())return null;
            Iterator<Map.Entry<String,JsonNode>> fields=params.fields();
            while(fields.hasNext()) {
                var entry=fields.next();String value=entry.getValue().asText("");String combined=(entry.getKey()+":"+value).toLowerCase(Locale.ROOT);
                if(Pattern.compile("不支持|不具备|不含|不提供|未配备|无此|无避障|无集尘|^.*:(?:无|否|false|none|0)$").matcher(combined).find())continue;
                if(FEATURES.getOrDefault(feature,List.of(feature)).stream().anyMatch(combined::contains)) return entry.getKey()+"："+value;
            }
        } catch(Exception ignored) {}
        return null;
    }
    public List<String> rejectionReasons(Product product,Need need) {
        List<String> reasons=new ArrayList<>();
        if(!RecommendationRules.eligible(product,need.budget(),need.allowOverBudget()))reasons.add("上架、库存或预算条件不满足");
        if(need.category()!=null&&!need.category().equals(product.categoryName)&&!(need.accessory()&&"ACCESSORY".equals(product.type)))reasons.add("类别不符");
        if(need.category()!=null&&!need.accessory()&&!"ROBOT".equals(product.type))reasons.add("当前需要主机而非配件");
        if(need.minEndurance()>0&&(product.enduranceMinutes==null||product.enduranceMinutes<need.minEndurance()))reasons.add("最低续航未满足或没有数据");
        for(String feature:need.requiredFeatures())if(evidence(product,feature)==null)reasons.add("必要功能缺少参数证据："+feature);
        return reasons;
    }
    public List<Product> eligible(List<Product> catalog,Need need) {return catalog.stream().filter(p->rejectionReasons(p,need).isEmpty()).toList();}
    public List<Map<String,Object>> rank(List<Product> catalog,Need need) {
        List<Product> eligible=eligible(catalog,need);
        int longest=eligible.stream().mapToInt(p->p.enduranceMinutes==null?0:p.enduranceMinutes).max().orElse(0);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Product product:eligible) {
            List<String> evidence=new ArrayList<>(); double score=50;
            if(need.budget()!=null) {
                double ratio=product.price.divide(need.budget(),6,java.math.RoundingMode.HALF_UP).doubleValue();
                score+=ratio<=1?18-Math.abs(ratio-.78)*12:Math.max(0,8-(ratio-1)*35);
                evidence.add(ratio<=1?"价格在预算内":"用户允许的20%超预算备选");
                if("value".equals(need.priority()))score+=Math.max(0,12*(1-ratio));
            }
            for(String feature:need.requiredFeatures())evidence.add(feature+"依据："+evidence(product,feature));
            if("performance".equals(need.priority())&&longest>0&&product.enduranceMinutes!=null) {
                score+=10.0*product.enduranceMinutes/longest;evidence.add("同类目录续航："+product.enduranceMinutes+"分钟；不代表综合性能");
            }
            if("easy".equals(need.priority())&&product.warranty!=null&&!product.warranty.isBlank()) {score+=3;evidence.add("目录售后说明："+product.warranty);}
            Map<String,Object> row=new LinkedHashMap<>();row.put("product",product);row.put("rawScore",score);row.put("score",Math.round(score));
            row.put("withinBudget",need.budget()==null||product.price.compareTo(need.budget())<=0);row.put("reasons",evidence);row.put("ruleVersion",VERSION);rows.add(row);
        }
        rows.sort(Comparator.<Map<String,Object>>comparingDouble(row->((Number)row.get("rawScore")).doubleValue()).reversed()
                .thenComparingLong(row->((Product)row.get("product")).id));return rows;
    }
}
