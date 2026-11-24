package com.xiaozao.robotmall.service;

import com.xiaozao.robotmall.entity.Product;
import java.math.BigDecimal;

/** Shared hard constraints for form ranking and conversation candidates. */
/** 集中校验商品的基础准入条件。 */
public final class RecommendationRules {
    /** 防止实例化仅含静态规则的工具类。 */
    private RecommendationRules() {}

    /** 检查上架、库存、价格及预算放宽规则。 */
    public static boolean eligible(Product p, BigDecimal budget, boolean allowOverBudget) {
        return p.enabled
                && p.stock > 0
                && p.price != null
                && p.price.signum() >= 0
                && (budget == null
                        || p.price.compareTo(
                                        allowOverBudget
                                                ? budget.multiply(new BigDecimal("1.2"))
                                                : budget)
                                <= 0);
    }
}
