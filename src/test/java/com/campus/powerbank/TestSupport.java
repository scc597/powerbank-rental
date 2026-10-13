package com.campus.powerbank;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 测试公共工具类。
 *
 * <p>为边界值测试用例提供统一的断言辅助方法，
 * 便于在测试报告中以一致的方式表达「预期结果」。</p>
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * 断言执行 {@code action} 会抛出指定错误码的 {@link BizException}。
     *
     * @param expected 预期错误码
     * @param action   被测操作
     */
    public static void assertBizError(ErrorCode expected, Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run,
                "预期抛出业务异常 " + expected.getCode());
        assertEquals(expected, ex.getErrorCode(),
                "预期错误码 " + expected.getCode() + "，实际 " + ex.getErrorCode().getCode());
    }

    /**
     * 断言执行 {@code action} 不会抛出业务异常。
     *
     * @param action 被测操作
     */
    public static void assertNoBizError(Runnable action) {
        try {
            action.run();
        } catch (BizException e) {
            throw new AssertionError("预期不抛出业务异常，实际抛出："
                    + e.getErrorCode().getCode() + " " + e.getMessage(), e);
        }
    }

    /**
     * 断言两个金额在保留 2 位小数后相等（规避浮点误差）。
     *
     * @param expected 预期金额
     * @param actual   实际金额
     */
    public static void assertMoney(double expected, double actual) {
        assertEquals(round2(expected), round2(actual), 0.0001,
                "预期金额 " + round2(expected) + "，实际 " + round2(actual));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
