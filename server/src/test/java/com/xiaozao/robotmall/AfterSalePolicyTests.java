package com.xiaozao.robotmall;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.entity.AfterSaleRequest;
import com.xiaozao.robotmall.service.AfterSalePolicy;
import org.junit.jupiter.api.Test;

/** 验证售后状态与金额规则。 */
class AfterSalePolicyTests {
    /** 构造不同状态的售后记录。 */
    private AfterSaleRequest request(String type, String status) {
        AfterSaleRequest item = new AfterSaleRequest();
        item.type = type;
        item.status = status;
        item.requestedAmount = new BigDecimal("100.00");
        return item;
    }

    /** 验证终态不能重新开启。 */
    @Test
    void terminalRequestsCannotBeReopened() {
        for (String status : new String[] {"COMPLETED", "REJECTED", "CANCELLED"}) {
            AfterSaleRequest item = request("REFUND", status);
            assertTrue(AfterSalePolicy.allowedStatuses(item).isEmpty());
            assertThrows(
                    BizException.class,
                    () -> AfterSalePolicy.validate(item, "REVIEWING", 0, "重新审核"));
        }
    }

    /** 验证先校验金额再修改记录。 */
    @Test
    void amountsAreValidatedBeforeMutation() {
        AfterSaleRequest item = request("REFUND", "PROCESSING");
        for (Object amount :
                new Object[] {
                    -1, 101, "10", new BigDecimal("1.001"), Double.NaN, Double.POSITIVE_INFINITY
                }) {
            assertThrows(
                    BizException.class,
                    () -> AfterSalePolicy.validate(item, "COMPLETED", amount, "处理完成"));
            assertEquals("PROCESSING", item.status);
        }
        assertEquals(
                new BigDecimal("99.99"),
                AfterSalePolicy.validate(item, "COMPLETED", new BigDecimal("99.99"), "处理完成"));
        assertThrows(
                BizException.class, () -> AfterSalePolicy.validate(item, "COMPLETED", 100, " "));
        assertThrows(
                BizException.class,
                () -> AfterSalePolicy.validate(item, "COMPLETED", 100, "长".repeat(1001)));
    }

    /** 验证退货物流前置条件及状态只能向前。 */
    @Test
    void returnRequiresUserLogisticsAndForwardProgress() {
        AfterSaleRequest item = request("RETURN", "SUBMITTED");
        assertThrows(
                BizException.class,
                () -> AfterSalePolicy.validate(item, "PROCESSING", 100, "跳过退货"));
        assertThrows(
                BizException.class, () -> AfterSalePolicy.validate(item, "COMPLETED", 100, "跳过处理"));
        assertNotNull(AfterSalePolicy.validate(item, "WAITING_RETURN", 100, "请寄回商品"));
        item.status = "WAITING_RETURN";
        assertThrows(
                BizException.class,
                () -> AfterSalePolicy.validate(item, "RETURN_SHIPPED", 100, "管理员伪造寄回"));
        item.status = "RETURN_SHIPPED";
        assertThrows(
                BizException.class,
                () -> AfterSalePolicy.validate(item, "PROCESSING", 100, "未填物流"));
        item.returnLogisticsNo = "TEST12345";
        assertNotNull(AfterSalePolicy.validate(item, "PROCESSING", 100, "已收到退货"));
        item.status = "PROCESSING";
        assertThrows(
                BizException.class, () -> AfterSalePolicy.validate(item, "REVIEWING", 100, "倒退状态"));
        assertNotNull(AfterSalePolicy.validate(item, "COMPLETED", 100, "模拟退款处理完成"));
    }

    /** 验证维修不退款且仅退款不要求退货。 */
    @Test
    void repairCannotRefundAndRefundCannotRequestReturn() {
        assertThrows(
                BizException.class,
                () ->
                        AfterSalePolicy.validate(
                                request("REPAIR", "PROCESSING"), "COMPLETED", 1, "维修完成"));
        assertNotNull(
                AfterSalePolicy.validate(request("REPAIR", "SUBMITTED"), "PROCESSING", 0, "远程检测"));
        assertThrows(
                BizException.class,
                () ->
                        AfterSalePolicy.validate(
                                request("REFUND", "REVIEWING"), "WAITING_RETURN", 100, "类型不符"));
    }
}
