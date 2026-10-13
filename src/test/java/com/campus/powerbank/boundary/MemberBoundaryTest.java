package com.campus.powerbank.boundary;

import com.campus.powerbank.TestSupport;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Member;
import com.campus.powerbank.model.MemberLevel;
import com.campus.powerbank.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【方法一】正常边界值分析法测试 —— 会员模块。
 *
 * <p>正常边界值分析法只取边界内的有效值：下界点、下界点+1、有效内点、
 * 上界点-1、上界点。对每个被测输入域取 5 个值。</p>
 *
 * <p>被测输入域：</p>
 * <table border="1">
 *   <caption>会员模块输入域</caption>
 *   <tr><th>输入</th><th>下界</th><th>上界</th><th>边界取值</th></tr>
 *   <tr><td>年龄 age</td><td>18</td><td>60</td><td>18, 19, 39, 59, 60</td></tr>
 *   <tr><td>单次充值金额</td><td>1</td><td>2000</td><td>1, 2, 1000, 1999, 2000</td></tr>
 *   <tr><td>信用分 credit</td><td>0</td><td>1000</td><td>0, 1, 500, 999, 1000</td></tr>
 * </table>
 */
@DisplayName("方法一：正常边界值分析法 —— 会员模块")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberBoundaryTest {

    private MemberService memberService;

    private void setUp() {
        memberService = new MemberService();
    }

    // ==================== 输入域 1：年龄 [18, 60] ====================

    @Nested
    @DisplayName("年龄输入域 [18, 60]")
    class AgeDomain {

        @ParameterizedTest(name = "age={0} 应注册成功")
        @ValueSource(ints = {18, 19, 39, 59, 60})
        @DisplayName("TC-MB-01 有效边界值：下界、下界+1、内点、上界-1、上界")
        void validBoundaryAges(int age) {
            setUp();
            TestSupport.assertNoBizError(() -> {
                Member m = memberService.register("13800138000", "张三", age);
                assertEquals(age, m.getAge());
            });
        }

        @ParameterizedTest(name = "age={0} 应被拒绝")
        @ValueSource(ints = {17, 61})
        @DisplayName("TC-MB-02 越界值：下界-1、上界+1 必须被拒绝（覆盖健壮性，见 RobustBoundaryTest）")
        void outOfRangeAges(int age) {
            setUp();
            TestSupport.assertBizError(ErrorCode.AGE_OUT_OF_RANGE,
                    () -> memberService.register("13800138000", "张三", age));
        }

        @Test
        @Order(1)
        @DisplayName("TC-MB-03 边界恒等式：min 与 max 均为闭区间端点")
        void ageBoundsAreInclusive() {
            assertEquals(18, Member.MIN_AGE, "年龄下界应为 18 且包含");
            assertEquals(60, Member.MAX_AGE, "年龄上界应为 60 且包含");
        }
    }

    // ==================== 输入域 2：单次充值金额 [1, 2000] ====================

    @Nested
    @DisplayName("单次充值金额输入域 [1, 2000]")
    class RechargeDomain {

        @ParameterizedTest(name = "单次充值 {0} 元应成功")
        @ValueSource(ints = {1, 2, 1000, 1999, 2000})
        @DisplayName("TC-MB-04 有效边界值：下界、下界+1、内点、上界-1、上界")
        void validRechargeAmounts(int amount) {
            setUp();
            TestSupport.assertNoBizError(() -> {
                Member m = memberService.register("13800138000", "张三", 25);
                memberService.recharge(m.getId(), amount);
                assertEquals(amount, m.getTotalRecharge());
            });
        }

        @ParameterizedTest(name = "单次充值 {0} 元应被拒绝")
        @ValueSource(ints = {0, -1, 2001})
        @DisplayName("TC-MB-05 越界值：0、-1、上界+1")
        void outOfRangeRechargeAmounts(int amount) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertBizError(ErrorCode.RECHARGE_OUT_OF_RANGE,
                    () -> memberService.recharge(m.getId(), amount));
            assertEquals(0, m.getTotalRecharge(), "充值失败后累计金额不应变化");
        }
    }

    // ==================== 输入域 3：信用分 [0, 1000] ====================

    @Nested
    @DisplayName("信用分输入域 [0, 1000]")
    class CreditDomain {

        @ParameterizedTest(name = "credit={0} 应设置成功")
        @ValueSource(ints = {0, 1, 500, 999, 1000})
        @DisplayName("TC-MB-06 有效边界值：下界、下界+1、内点、上界-1、上界")
        void validCredits(int credit) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertNoBizError(() -> {
                memberService.updateCredit(m.getId(), credit);
                assertEquals(credit, m.getCredit());
            });
        }

        @ParameterizedTest(name = "credit={0} 应被拒绝")
        @ValueSource(ints = {-1, 1001})
        @DisplayName("TC-MB-07 越界值：下界-1、上界+1")
        void outOfRangeCredits(int credit) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertBizError(ErrorCode.CREDIT_OUT_OF_RANGE,
                    () -> memberService.updateCredit(m.getId(), credit));
        }

        @Test
        @Order(2)
        @DisplayName("TC-MB-08 多条件规则边界：免押金阈值 800 与可租借阈值 500")
        void creditThresholdBoundaries() {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);

            // 799：未达免押金线，但可租借
            memberService.updateCredit(m.getId(), 799);
            assertFalse(m.isCreditExempt(), "信用分 799 不应免押金");
            assertTrue(m.canRent(), "信用分 799 应允许租借");

            // 800：恰好达到免押金线
            memberService.updateCredit(m.getId(), 800);
            assertTrue(m.isCreditExempt(), "信用分 800 应免押金（闭区间下界）");

            // 500：恰好达到可租借线
            memberService.updateCredit(m.getId(), 500);
            assertTrue(m.canRent(), "信用分 500 应允许租借（闭区间下界）");

            // 499：低于可租借线
            memberService.updateCredit(m.getId(), 499);
            assertFalse(m.canRent(), "信用分 499 不应允许租借");
        }
    }

    // ==================== 多条件业务规则：等级判定 ====================

    @Nested
    @DisplayName("会员等级判定（由累计充值金额决定的多分支规则）")
    class LevelRule {

        @ParameterizedTest(name = "累计充值 {0} 元 -> {1}")
        @CsvSource({
                // 各等级区间的下界、下界+1、上界-1、上界
                "0,   NORMAL",    // 普通 [0,99] 下界
                "99,  NORMAL",    // 普通上界
                "100, SILVER",    // 银卡下界（关键边界）
                "101, SILVER",    // 银卡下界+1
                "499, SILVER",    // 银卡上界
                "500, GOLD",      // 金卡下界（关键边界）
                "501, GOLD",      // 金卡下界+1
                "1999, GOLD",     // 金卡上界
                "2000, DIAMOND",  // 黑钻下界（关键边界）
                "2001, DIAMOND",  // 黑钻下界+1
        })
        @DisplayName("TC-MB-09 等级阈值边界：每个区间取下界与上界验证归属")
        void levelThresholds(int total, MemberLevel expected) {
            assertEquals(expected, MemberLevel.of(total),
                    "累计充值 " + total + " 元应为 " + expected);
        }

        @Test
        @Order(3)
        @DisplayName("TC-MB-10 等级升级链路：连续充值跨越每个阈值")
        void levelUpgradeChain() {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            assertEquals(MemberLevel.NORMAL, m.getLevel(), "初始应为普通会员");

            memberService.recharge(m.getId(), 99);
            assertEquals(MemberLevel.NORMAL, m.getLevel(), "累计 99 元仍为普通会员");

            memberService.recharge(m.getId(), 1);
            assertEquals(MemberLevel.SILVER, m.getLevel(), "累计 100 元应升为银卡");

            memberService.recharge(m.getId(), 400);
            assertEquals(MemberLevel.GOLD, m.getLevel(), "累计 500 元应升为金卡");

            memberService.recharge(m.getId(), 1500);
            assertEquals(MemberLevel.DIAMOND, m.getLevel(), "累计 2000 元应升为黑钻");
        }

        @ParameterizedTest(name = "{0} 折扣率应为 {1}")
        @CsvSource({"NORMAL,1.00", "SILVER,0.98", "GOLD,0.95", "DIAMOND,0.90"})
        @DisplayName("TC-MB-11 各等级折扣率与等级一一对应")
        void discountRates(MemberLevel level, double expected) {
            assertEquals(expected, level.getDiscountRate(), 0.0001);
        }
    }
}
