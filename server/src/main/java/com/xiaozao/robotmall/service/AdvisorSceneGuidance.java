package com.xiaozao.robotmall.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 把实测和使用手册里的选购场景转为追问，不把资料缺口当作商品能力。 */
public final class AdvisorSceneGuidance {
    private AdvisorSceneGuidance() {}

    /** 保存按品类给出的选购提醒和一个尚待确认的问题。 */
    public record Guidance(String note, String nextQuestion) {}

    /** 根据当前品类和已给信息生成提示；没有资料时不承诺产品可用。 */
    public static Guidance forCategory(
            String category, String question, List<Map<String, String>> history) {
        String text =
                history.stream()
                                .filter(row -> "user".equals(row.get("role")))
                                .map(row -> row.getOrDefault("content", ""))
                                .collect(Collectors.joining("\n"))
                        + "\n"
                        + question;
        return switch (Objects.toString(category, "")) {
            case "地面清洁" ->
                    new Guidance(
                            "扫拖先区分地板、地毯和门槛；标称吸力不能代替毛发、越障或实测效果。",
                            !has(text, "硬地板|木地板|地毯|瓷砖|地砖") ? "家里主要是硬地板，还是也有地毯？" : "");
            case "擦窗机器人" ->
                    new Guidance(
                            "先按具体型号核对玻璃类型、最小尺寸和固定要求；不能只凭吸附参数判断现场可用。",
                            !has(text, "无框|有框|带框")
                                    ? "玻璃是有框还是无框？"
                                    : !has(text, "\\d\\s*(?:cm|厘米|米|[×x])")
                                            ? "最小一块玻璃大约多宽、多高？"
                                            : "");
            case "庭院与泳池" -> outdoor(text, question);
            case "陪伴娱乐" ->
                    new Guidance(
                            "陪伴、远程看宠物和看家不是同一用途。要按型号确认网络、门槛、摄像头控制和存储方式；不把它当安全监护设备。",
                            !has(text, "远程|摄像|看家|监控|逗猫|陪伴|看宠物") ? "你主要想远程看宠物、互动，还是在家陪伴？" : "");
            case "航拍无人机" ->
                    new Guidance(
                            "先区分便携、跟拍、画质和飞行环境；标称续航不是实际可拍时长，避障也有光线和环境条件。",
                            !has(text, "旅行|跟拍|夜景|海边|山里|户外|室内|竖拍") ? "你主要想旅行航拍、跟拍自己，还是拍夜景？" : "");
            case "教育编程" ->
                    new Guidance(
                            "先区分图形化入门和后续 Python；具体支持的平台、设备、课程和扩展要按型号核对。",
                            !has(text, "scratch|图形化|python|arduino")
                                    ? "想先用图形化编程入门，还是已打算学 Python？"
                                    : "");
            case "四足机器人" ->
                    new Guidance(
                            "遥控演示和二次开发不是一回事。SDK、控制权限、载重与续航都要按准确版本核对。",
                            !has(text, "遥控|巡检|二次开发|sdk|ros|编队|实验") ? "你主要想遥控演示，还是要接 SDK 做开发？" : "");
            case "人形机器人" ->
                    new Guidance(
                            "先分展示、科研和实际作业；要核对具体配置、开发权限及操作安全要求，不能把演示动作当现场能力。",
                            !has(text, "科研|展示|表演|搬运|开发|教学") ? "是展览演示，还是科研、开发或实际作业？" : "");
            case "商用服务" ->
                    new Guidance(
                            "商用先看现场路线、通道宽度、楼层和系统联动，再看载重、充电与现场实施条件。",
                            !has(text, "电梯|楼层|门禁|通道") ? "只在同一层跑，还是需要联动电梯或门禁？" : "");
            default -> new Guidance("", "");
        };
    }

    /** 将合并品类中的割草和泳池两个完全不同的任务分开追问。 */
    private static Guidance outdoor(String text, String current) {
        boolean pool = has(current, "池底|池壁|水线|泳池|游泳池");
        boolean lawn = has(current, "割草|草坪|坡度|树荫");
        if (!pool && !lawn) {
            pool = has(text, "池底|池壁|水线|泳池|游泳池");
            lawn = has(text, "割草|草坪|坡度|树荫");
        }
        if (pool && !lawn)
            return new Guidance(
                    "泳池要分别核对池底、池壁和水线清洁模式，以及形状、材质和台阶限制。",
                    !has(text, "池底|池壁|水线") ? "只清池底，还是需要一起清池壁和水线？" : "");
        if (lawn && !pool)
            return new Guidance(
                    "割草要看实测或标注的坡度、边界方式及树墙遮挡；不能只按面积给出能用的结论。",
                    !has(text, "坡度|坡地|陡坡|树荫|遮挡") ? "草坪有没有明显坡地、大树或高墙遮挡？" : "");
        return new Guidance("割草和泳池是不同任务，要先分开。", "你想解决的是草坪割草，还是泳池清洁？");
    }

    /** 匹配已提供的线索，不区分英文大小写。 */
    private static boolean has(String text, String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
}
