package com.xiaozao.robotmall.service;

import com.xiaozao.robotmall.entity.Product;
import java.math.BigDecimal;

/** Shared hard constraints for form ranking and conversation candidates. */
public final class RecommendationRules {
    private RecommendationRules() {}
    public static boolean eligible(Product p, BigDecimal budget, boolean allowOverBudget) {
        return p.enabled && p.stock > 0 && p.price != null && p.price.signum() >= 0
                && (budget == null || p.price.compareTo(allowOverBudget ? budget.multiply(new BigDecimal("1.2")) : budget) <= 0);
    }
}
