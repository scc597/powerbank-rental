package com.campus.powerbank.boundary;

import com.campus.powerbank.TestSupport;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Member;
import com.campus.powerbank.model.MemberLevel;
import com.campus.powerbank.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 【方法二】健壮边界值分析法测试 —— 会员模块。
 *
 * <p>健壮边界值分析法在正常边界值的基础上，<b>向无效区间多取一个值</b>，
 * 即在每个边界内侧补一个「刚好无效」的点（下界-1、上界+1），
 * 用于检验系统的健壮性——无效输入必须被拒绝，而不是崩溃或错误接受。</p>
 *
 * <p>每个输入域取 6 个值：min-1、min、min+1、max-1、max、max+1。</p>
 */
@DisplayName("方法二：健壮边界值分析法 —— 会员模块")
class MemberRobustBoundaryTest {

    private MemberService memberService;

    private void setUp() {
        memberService = new MemberService();
    }

    // ==================== 年龄：6 点法 ====================

    @Nested
    @DisplayName("年龄健壮边界（下界 18，上界 60）")
    class AgeRobust {

        @ParameterizedTest(name = "age={0} -> 接受")
        @ValueSource(ints = {18, 19, 59, 60})
        @DisplayName("TC-MR-01 有效侧 4 点：min、min+1、max-1、max")
        void validPoints(int age) {
            setUp();
            TestSupport.assertNoBizError(() -> {
                Member m = memberService.register("13800138000", "张三", age);
                assertEquals(age, m.getAge());
            });
        }

        @ParameterizedTest(name = "age={0} -> 拒绝")
        @ValueSource(ints = {17, 61})
        @DisplayName("TC-MR-02 无效侧 2 点：min-1、max+1 必须被拒绝")
        void invalidPoints(int age) {
            setUp();
            TestSupport.assertBizError(ErrorCode.AGE_OUT_OF_RANGE,
                    () -> memberService.register("13800138000", "张三", age));
        }

        @Test
        @DisplayName("TC-MR-03 极端越界值：Integer 上下限也必须被拒绝而非溢出")
        void extremeValues() {
            setUp();
            TestSupport.assertBizError(ErrorCode.AGE_OUT_OF_RANGE,
                    () -> memberService.register("13800138000", "张三", Integer.MIN_VALUE));
            TestSupport.assertBizError(ErrorCode.AGE_OUT_OF_RANGE,
                    () -> memberService.register("13800138000", "张三", Integer.MAX_VALUE));
            // 拒绝后系统状态必须保持干净，可继续正常注册
            TestSupport.assertNoBizError(
                    () -> memberService.register("13800138000", "张三", 30));
            assertEquals(1, memberService.size(), "极端值不应产生脏数据");
        }
    }

    // ==================== 充值金额：6 点法 ====================

    @Nested
    @DisplayName("充值金额健壮边界（下界 1，上界 2000）")
    class RechargeRobust {

        @ParameterizedTest(name = "充值 {0} 元 -> 接受")
        @ValueSource(ints = {1, 2, 1999, 2000})
        @DisplayName("TC-MR-04 有效侧 4 点")
        void validPoints(int amount) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertNoBizError(() -> {
                memberService.recharge(m.getId(), amount);
                assertEquals(amount, m.getTotalRecharge());
            });
        }

        @ParameterizedTest(name = "充值 {0} 元 -> 拒绝")
        @ValueSource(ints = {0, -1, 2001})
        @DisplayName("TC-MR-05 无效侧：min-1、min-2、max+1")
        void invalidPoints(int amount) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertBizError(ErrorCode.RECHARGE_OUT_OF_RANGE,
                    () -> memberService.recharge(m.getId(), amount));
            assertEquals(0, m.getTotalRecharge(), "被拒绝的充值不得污染累计金额");
        }

        @Test
        @DisplayName("TC-MR-06 累计上限健壮性：超过 20000 上限必须拒绝")
        void totalRechargeCap() {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            // 连续充值至 19000 元（9 次 2000 + 1 次 1000）
            for (int i = 0; i < 9; i++) {
                memberService.recharge(m.getId(), 2000);
            }
            memberService.recharge(m.getId(), 1000);
            assertEquals(19000, m.getTotalRecharge());
            assertEquals(MemberLevel.DIAMOND, m.getLevel(), "19000 元应为黑钻会员");

            // 再充 1000 -> 20000，恰好等于上限，应成功
            TestSupport.assertNoBizError(() -> memberService.recharge(m.getId(), 1000));
            assertEquals(20000, m.getTotalRecharge(), "累计 20000 应为上限值");

            // 再充 1 元 -> 20001，超过上限，必须拒绝
            TestSupport.assertBizError(ErrorCode.RECHARGE_OUT_OF_RANGE,
                    () -> memberService.recharge(m.getId(), 1));
            assertEquals(20000, m.getTotalRecharge(), "越界充值后累计金额必须回滚");
        }
    }

    // ==================== 信用分：6 点法 ====================

    @Nested
    @DisplayName("信用分健壮边界（下界 0，上界 1000）")
    class CreditRobust {

        @ParameterizedTest(name = "credit={0} -> 接受")
        @ValueSource(ints = {0, 1, 999, 1000})
        @DisplayName("TC-MR-07 有效侧 4 点")
        void validPoints(int credit) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertNoBizError(() -> {
                memberService.updateCredit(m.getId(), credit);
                assertEquals(credit, m.getCredit());
            });
        }

        @ParameterizedTest(name = "credit={0} -> 拒绝")
        @ValueSource(ints = {-1, 1001})
        @DisplayName("TC-MR-08 无效侧 2 点")
        void invalidPoints(int credit) {
            setUp();
            Member m = memberService.register("13800138000", "张三", 25);
            TestSupport.assertBizError(ErrorCode.CREDIT_OUT_OF_RANGE,
                    () -> memberService.updateCredit(m.getId(), credit));
            assertEquals(800, m.getCredit(), "被拒绝的修改不得改变原信用分");
        }
    }

    // ==================== 手机号格式健壮性 ====================

    @Nested
    @DisplayName("手机号格式健壮性")
    class PhoneRobust {

        @ParameterizedTest(name = "phone=\"{0}\" -> 接受")
        @ValueSource(strings = {"13800138000", "19999999999", "10000000000"})
        @DisplayName("TC-MR-09 有效手机号：11 位且首位为 1")
        void validPhones(String phone) {
            setUp();
            TestSupport.assertNoBizError(
                    () -> memberService.register(phone, "张三", 25));
        }

        @ParameterizedTest(name = "phone=\"{0}\" -> 拒绝")
        @ValueSource(strings = {
                "1380013800",      // 10 位，少 1 位
                "138001380000",    // 12 位，多 1 位
                "23800138000",     // 首位不是 1
                "1380013800a",     // 含非数字字符
                "",                // 空字符串
        })
        @DisplayName("TC-MR-10 边界位数的非法手机号必须被拒绝")
        void invalidPhones(String phone) {
            setUp();
            TestSupport.assertBizError(ErrorCode.PHONE_ILLEGAL,
                    () -> memberService.register(phone, "张三", 25));
        }

        @Test
        @DisplayName("TC-MR-11 null 输入健壮性：不得抛出 NullPointerException")
        void nullPhone() {
            setUp();
            TestSupport.assertBizError(ErrorCode.PHONE_ILLEGAL,
                    () -> memberService.register(null, "张三", 25));
        }

        @Test
        @DisplayName("TC-MR-12 唯一性边界：同一手机号重复注册必须被拒绝")
        void duplicatePhone() {
            setUp();
            memberService.register("13800138000", "张三", 25);
            TestSupport.assertBizError(ErrorCode.PHONE_DUPLICATED,
                    () -> memberService.register("13800138000", "李四", 30));
            assertEquals(1, memberService.size(), "注册失败不得产生重复记录");
            assertFalse(memberService.listAll().isEmpty());
        }

        @Test
        @DisplayName("TC-MR-13 空姓名与空姓名串必须被拒绝")
        void blankName() {
            setUp();
            TestSupport.assertBizError(ErrorCode.PARAM_NULL,
                    () -> memberService.register("13800138000", null, 25));
            TestSupport.assertBizError(ErrorCode.PARAM_NULL,
                    () -> memberService.register("13800138000", "   ", 25));
        }
    }

    // ==================== 等级判定健壮边界 ====================

    @ParameterizedTest(name = "累计 {0} 元 -> {1}")
    @CsvSource({
            "0,    NORMAL",   // 普通下界
            "100,  SILVER",   // 银卡下界
            "500,  GOLD",     // 金卡下界
            "2000, DIAMOND",  // 黑钻下界
    })
    @DisplayName("TC-MR-14 等级阈值健壮边界：每个下界点都应准确落到新等级")
    void levelRobustBoundaries(int total, MemberLevel expected) {
        assertEquals(expected, MemberLevel.of(total));
    }

    @ParameterizedTest(name = "累计 {0} 元 -> 应为 {1}（下界-1 侧）")
    @CsvSource({
            "99,   NORMAL",
            "499,  SILVER",
            "1999, GOLD",
    })
    @DisplayName("TC-MR-15 等级下界-1：必须仍停留在低一等级")
    void levelBelowThreshold(int total, MemberLevel expected) {
        assertEquals(expected, MemberLevel.of(total),
                "累计 " + total + " 元应为 " + expected);
    }
}
