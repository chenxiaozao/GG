package com.xiaozao.robotmall.service;

import java.math.BigDecimal;
import java.util.*;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.AfterSaleRequest;

/** Shared server-side rules; UI restrictions are not an authorization boundary. */
public final class AfterSalePolicy {
    private AfterSalePolicy() {}

    public static List<String> allowedStatuses(AfterSaleRequest request) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        switch (Objects.toString(request.status, "")) {
            case "SUBMITTED", "REVIEWING" -> {
                result.add("REVIEWING");
                if (!"REFUND".equals(request.type)) result.add("WAITING_RETURN");
                if (!"RETURN".equals(request.type)) result.add("PROCESSING");
                result.add("REJECTED");
            }
            case "WAITING_RETURN" -> result.add("REJECTED");
            case "RETURN_SHIPPED" -> { result.add("PROCESSING"); result.add("REJECTED"); }
            case "PROCESSING" -> { result.add("COMPLETED"); result.add("REJECTED"); }
            default -> { return List.of(); }
        }
        if (!"SUBMITTED".equals(request.status)) result.add(request.status);
        return List.copyOf(result);
    }

    public static BigDecimal validate(AfterSaleRequest request, String status, Object amount, String reply) {
        if (allowedStatuses(request).isEmpty()) throw new BizException("售后单已结束，不能再次修改");
        if (!allowedStatuses(request).contains(status)) throw new BizException("当前售后进度不允许转为该状态");
        if (reply.isBlank() || reply.length() > 1000) throw new BizException("处理说明请填写 1～1000 字");
        if (!(amount instanceof Number)) throw new BizException("请填写有效的核准金额");
        BigDecimal approved;
        try { approved = new BigDecimal(amount.toString()); }
        catch (NumberFormatException error) { throw new BizException("核准金额格式不正确"); }
        if (approved.stripTrailingZeros().scale() > 2) throw new BizException("核准金额最多保留两位小数");
        BigDecimal maximum = Objects.requireNonNullElse(request.requestedAmount, BigDecimal.ZERO);
        if (approved.signum() < 0 || approved.compareTo(maximum) > 0) {
            throw new BizException("核准金额不能超过申请金额或小于零");
        }
        if ("REPAIR".equals(request.type) && approved.signum() != 0) throw new BizException("维修售后不产生退款金额");
        if ("RETURN".equals(request.type) && Set.of("PROCESSING", "COMPLETED").contains(status)
                && (request.returnLogisticsNo == null || request.returnLogisticsNo.isBlank())) {
            throw new BizException("退货售后需先由用户提交寄回物流");
        }
        return approved;
    }
}
