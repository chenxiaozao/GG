package com.xiaozao.robotmall.service;

import java.util.*;
import java.util.regex.Pattern;

/** Conservative domain boundary; rejection/service routing does not invoke an LLM. */
/** 在查商品和调用模型之前判断咨询范围。 */
public final class AdvisorScope {
    /** 防止实例化仅含静态规则的工具类。 */
    private AdvisorScope() {}

    /** 遮盖问题和历史中的常见手机号及邮箱。 */
    public static String redactContacts(String text) {
        return Objects.toString(text, "")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[已隐藏手机号]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[已隐藏邮箱]");
    }

    public static final String BOUNDARY =
            "我只协助机器人及配件选购、参数比较和本商城使用，不解无关数学题、作业或通用任务。你可以告诉我用途、预算及在意的功能。";

    /** 预设选购词用于匹配范围，这不是开放领域的学科分类器。 */
    private static final Pattern DOMAIN =
            Pattern.compile(
                    "机器人|无人机|扫地|扫拖|擦窗|配件|电池|预算|选购|推荐商品|帮我选|适配|续航|型号|价格|多少钱|地板|窗户|割草|航拍|毛发|防缠绕|避障|集尘|go2|roborock|大疆",
                    Pattern.CASE_INSENSITIVE);

    /** 按词语、句式及历史决定本地回应；无本地响应才继续选购流程。 */
    public static String localReply(String question, List<Map<String, String>> history) {
        return localReply(question, history, false);
    }

    /** 已选商品可解释“这几款”等指代，但不能绕过明确的拒答规则。 */
    public static String localReply(
            String question, List<Map<String, String>> history, boolean hasCandidates) {
        String text = Objects.toString(question, "").trim();
        if (Pattern.compile(
                        "忽略.{0,8}(规则|指令|限制)|系统提示词|system prompt|股票推荐|诊断疾病",
                        Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find()) return BOUNDARY;
        boolean task =
                Pattern.compile("解方程|解数学|数学题|微积分|求导|积分题|证明题|写作文|做作业|写论文|列式|解题|计算题")
                        .matcher(text)
                        .find();
        boolean shopping =
                Pattern.compile("买|选购|推荐.{0,8}机器人|选.{0,6}机器人|哪款|哪种|有没有.{0,8}机器人")
                        .matcher(text)
                        .find();
        if (task
                && (!shopping
                        || Pattern.compile("然后|顺便|另外|但是|[xyz]\\s*[+^=]", Pattern.CASE_INSENSITIVE)
                                .matcher(text)
                                .find())) return BOUNDARY;
        if (Pattern.compile(
                                "\\d+\\s*[+*/÷×=]\\s*\\d+|求[xyz]|[xyz]\\s*[+^=]",
                                Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find()
                && !Pattern.compile("价格|预算|元|买|购买|台|费用|成本|折扣").matcher(text).find())
            return BOUNDARY;
        if (Pattern.compile("订单|售后|退款|支付|物流|发货|收货|账户|账号|密码|绑定|购物车").matcher(text).find()
                && !Pattern.compile("买什么|选哪|推荐|参数|对比").matcher(text).find())
            return "本商城仅模拟交易。我不能代办支付、退款或修改账号；请进入订单中心、售后服务或个人空间操作，具体状态以对应页面为准。";
        if (DOMAIN.matcher(text).find()) return null;
        if (Set.of("你好", "您好", "hi", "hello").contains(text.toLowerCase(Locale.ROOT)))
            return "你好，我是机器人选购助手。想解决什么问题？可以从用途、预算或关注的型号开始。";
        boolean followup =
                Pattern.compile(
                                "便宜|贵|省心|哪款|哪个|为什么|区别|这些|这几款|它们|那个|还有|可以吗|支持|能否|有没有|怎么样|多重|尺寸|再推荐|换一款|更轻|更小|多久|好用|不需要|必须|至少|允许|不允许")
                        .matcher(text)
                        .find();
        boolean relevantHistory =
                history != null
                        && history.stream()
                                .filter(row -> "user".equals(row.get("role")))
                                .anyMatch(
                                        row ->
                                                DOMAIN.matcher(row.getOrDefault("content", ""))
                                                        .find());
        if (text.length() <= 80 && followup && (relevantHistory || hasCandidates)) return null;
        if (relevantHistory && text.matches("\\d{2,7}\\s*(?:元|块钱?)?")) return null;
        return BOUNDARY;
    }
}
