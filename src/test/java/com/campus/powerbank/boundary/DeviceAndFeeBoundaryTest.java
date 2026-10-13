package com.campus.powerbank.boundary;

import com.campus.powerbank.TestSupport;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Device;
import com.campus.powerbank.model.DeviceStatus;
import com.campus.powerbank.model.MemberLevel;
import com.campus.powerbank.model.RentalOrder;
import com.campus.powerbank.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 【方法一 + 方法二】设备模块与费用计算的边界值测试。
 *
 * <p>费用计算规则含 4 个边界点，是本系统最有价值的边界值测试目标：</p>
 * <ol>
 *   <li>免费时长边界 60 分钟（60 分钟免费，61 分钟开始计费）</li>
 *   <li>第一阶梯上界 600 分钟（600 分钟按 0.1 元/分钟，601 分钟进入第二阶梯）</li>
 *   <li>费用封顶 50 元</li>
 *   <li>优惠券门槛 5 元</li>
 *   <li>最低消费 0.5 元</li>
 * </ol>
 */
@DisplayName("设备模块与费用计算的边界值测试")
class DeviceAndFeeBoundaryTest {

    // ==================== 设备容量 [10, 100] ====================

    @Nested
    @DisplayName("设备容量输入域 [10, 100]")
    class CapacityDomain {

        @ParameterizedTest(name = "容量={0} -> 登记成功")
        @ValueSource(ints = {10, 11, 55, 99, 100})
        @DisplayName("TC-DB-01 正常边界 5 点：min、min+1、内点、max-1、max")
        void validCapacities(int capacity) {
            DeviceService ds = new DeviceService();
            TestSupport.assertNoBizError(() -> {
                Device d = ds.register(capacity);
                assertEquals(capacity, d.getTotalCapacity());
                assertEquals(capacity, d.getAvailableStock(), "初始库存应等于容量");
            });
        }

        @ParameterizedTest(name = "容量={0} -> 拒绝")
        @ValueSource(ints = {9, 101})
        @DisplayName("TC-DB-02 健壮边界 2 点：min-1、max+1")
        void invalidCapacities(int capacity) {
            DeviceService ds = new DeviceService();
            TestSupport.assertBizError(ErrorCode.PARAM_NULL, () -> ds.register(capacity));
            assertEquals(0, ds.size(), "非法容量不得产生设备记录");
        }

        @Test
        @DisplayName("TC-DB-03 极端容量值也必须被拒绝")
        void extremeCapacities() {
            DeviceService ds = new DeviceService();
            TestSupport.assertBizError(ErrorCode.PARAM_NULL, () -> ds.register(Integer.MIN_VALUE));
            TestSupport.assertBizError(ErrorCode.PARAM_NULL, () -> ds.register(Integer.MAX_VALUE));
            assertEquals(0, ds.size());
        }
    }

    // ==================== 库存边界 ====================

    @Nested
    @DisplayName("库存变更与设备状态判定")
    class StockDomain {

        @Test
        @DisplayName("TC-DB-04 库存恰好耗尽：状态应从空闲变为已租出")
        void stockExhausted() {
            Device d = new Device("PB0001", 10);
            assertEquals(DeviceStatus.IDLE, d.getStatus());

            // 租出全部 10 个 -> 库存归零
            d.rentOut(10);
            assertEquals(0, d.getAvailableStock());
            assertEquals(DeviceStatus.RENTED, d.getStatus(), "库存为 0 应为已租出");
            assertFalse(d.isRentable(), "库存为 0 不可租");

            // 归还 1 个 -> 恢复可租
            d.returnIn(1);
            assertEquals(1, d.getAvailableStock());
            assertEquals(DeviceStatus.IDLE, d.getStatus(), "库存恢复后应可租");
        }

        @Test
        @DisplayName("TC-DB-05 租出超库存：数量 = 库存 + 1 必须被拒绝")
        void rentMoreThanStock() {
            Device d = new Device("PB0001", 10);
            d.rentOut(9);
            assertEquals(1, d.getAvailableStock());

            // 恰好等于剩余库存 -> 成功
            TestSupport.assertNoBizError(() -> d.rentOut(1));
            assertEquals(0, d.getAvailableStock());

            // 再租 1 个 -> 库存不足
            TestSupport.assertBizError(ErrorCode.STOCK_NOT_ENOUGH, () -> d.rentOut(1));
            assertEquals(0, d.getAvailableStock(), "失败后库存不应变化");
        }

        @ParameterizedTest(name = "租出数量={0} -> 拒绝")
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("TC-DB-06 租出数量非正数必须被拒绝")
        void rentNonPositive(int count) {
            Device d = new Device("PB0001", 10);
            TestSupport.assertBizError(ErrorCode.PARAM_NULL, () -> d.rentOut(count));
            assertEquals(10, d.getAvailableStock());
        }

        @Test
        @DisplayName("TC-DB-07 故障与离线优先级：故障 > 离线 > 库存")
        void statusPriority() {
            Device d = new Device("PB0001", 10);

            // 故障优先级最高
            d.setFaulty(true);
            assertEquals(DeviceStatus.FAULT, d.getStatus());
            d.setFaulty(false);

            // 离线优先于库存
            d.setOnline(false);
            assertEquals(DeviceStatus.OFFLINE, d.getStatus());
            TestSupport.assertBizError(ErrorCode.DEVICE_UNAVAILABLE, () -> d.rentOut(1));
            d.setOnline(true);

            // 恢复正常
            assertEquals(DeviceStatus.IDLE, d.getStatus());
        }

        @Test
        @DisplayName("TC-DB-08 补充库存：已满时补充量为 0，未满时补到满")
        void refillBoundaries() {
            Device d = new Device("PB0001", 20);
            // 已满 -> 无需补充
            assertEquals(0, d.refill(), "满库存补充量应为 0");

            // 租出 5 个后补充
            d.rentOut(5);
            assertEquals(15, d.getAvailableStock());
            assertEquals(5, d.refill(), "应补充 5 个");
            assertEquals(20, d.getAvailableStock());

            // 归还超容量数据不应导致库存越界
            d.returnIn(10);
            assertEquals(20, d.getAvailableStock(), "库存不应超过总容量");
        }
    }

    // ==================== 费用计算边界 ====================

    @Nested
    @DisplayName("费用计算规则的多条件边界")
    class FeeDomain {

        /**
         * 便捷方法：构造普通会员、不使用优惠券的订单。
         */
        private RentalOrder order(int minutes, MemberLevel level, boolean coupon) {
            return new RentalOrder("RT000001", 1L, "PB0001", 1, minutes, level, coupon);
        }

        @ParameterizedTest(name = "{0} 分钟 -> {1} 元（免费时长边界 60）")
        @CsvSource({
                "1,  0.5",   // 下界 1 分钟
                "59, 0.5",   // 59 分钟，仍在免费段内 -> 最低消费
                "60, 0.5",   // 60 分钟，免费时长上界 -> 仍免费
                "61, 0.5",   // 61 分钟，超出 1 分钟 -> 0.1 元，低于最低消费 -> 0.5
                "70, 1.0",   // 70 分钟 -> 10 * 0.1 = 1.0
                "600, 54.0", // 第一阶梯上界 -> 540 * 0.1 = 54.0
        })
        @DisplayName("TC-FB-01 免费时长与第一阶梯上界边界")
        void freeAndFirstTier(int minutes, double expected) {
            TestSupport.assertMoney(expected, order(minutes, MemberLevel.NORMAL, false).calcUnitFee());
        }

        @Test
        @DisplayName("TC-FB-02 第一/第二阶梯切换边界：600 与 601 分钟")
        void tierSwitchBoundary() {
            // 600 分钟：全部落在第一阶梯，540 * 0.1 = 54.00
            double at600 = order(600, MemberLevel.NORMAL, false).calcUnitFee();
            TestSupport.assertMoney(54.00, at600);

            // 601 分钟：540 * 0.1 + 1 * 0.08 = 54.08
            double at601 = order(601, MemberLevel.NORMAL, false).calcUnitFee();
            TestSupport.assertMoney(54.08, at601);

            // 跨越边界后费用必须单调递增
            assertTrue(at601 > at600, "601 分钟费用应高于 600 分钟");
        }

        @Test
        @DisplayName("TC-FB-03 费用封顶边界 60 元：第 674 分钟未封顶，第 675 分钟恰好触及，第 676 分钟被封顶")
        void feeCapBoundary() {
            // 第一阶梯满额：540 分钟 * 0.1 = 54.00 元（未触及封顶）
            TestSupport.assertMoney(54.00, order(600, MemberLevel.NORMAL, false).calcUnitFee());

            // 第二阶梯公式：54.00 + (minutes - 600) * 0.08
            // 第 674 分钟：54.00 + 74 * 0.08 = 59.92，未封顶
            double at674 = order(674, MemberLevel.NORMAL, false).calcUnitFee();
            TestSupport.assertMoney(59.92, at674);

            // 第 675 分钟：54.00 + 75 * 0.08 = 60.00，恰好等于封顶值（封顶判定为 > 60）
            double at675 = order(675, MemberLevel.NORMAL, false).calcUnitFee();
            TestSupport.assertMoney(60.00, at675);

            // 第 676 分钟：54.00 + 76 * 0.08 = 60.08 -> 超出后被封顶为 60.00
            double at676 = order(676, MemberLevel.NORMAL, false).calcUnitFee();
            TestSupport.assertMoney(60.00, at676);

            // 更长时长仍然是 60 元（封顶生效，费用不再增长）
            TestSupport.assertMoney(60.00, order(677, MemberLevel.NORMAL, false).calcUnitFee());
            TestSupport.assertMoney(60.00, order(1440, MemberLevel.NORMAL, false).calcUnitFee());

            // 封顶后费用必须保持单调不减
            assertTrue(at675 >= at674, "第 675 分钟费用不应低于第 674 分钟");
            assertTrue(at676 >= at675, "被封顶后费用不应下降");
        }

        @ParameterizedTest(name = "{0} 分钟（{1}，优惠券={2}）-> {3} 元")
        @CsvSource({
                // 优惠券门槛 5 元边界
                "160, NORMAL, false, 10.00",  // 100 分钟 * 0.1 = 10.00
                "160, NORMAL, true,  8.00",   // 10.00 >= 5，抵扣 2 元
                "109, NORMAL, false, 4.90",   // 49 分钟 * 0.1 = 4.90
                "109, NORMAL, true,  4.90",   // 4.90 < 5，优惠券不生效
                "110, NORMAL, false, 5.00",   // 50 * 0.1 = 5.00
                "110, NORMAL, true,  3.00",   // 5.00 >= 5，抵扣 2 元
        })
        @DisplayName("TC-FB-04 优惠券门槛边界 5 元")
        void couponThreshold(int minutes, MemberLevel level, boolean coupon, double expected) {
            TestSupport.assertMoney(expected, order(minutes, level, coupon).calcUnitFee());
        }

        @ParameterizedTest(name = "{0} 分钟（{1}）-> {2} 元")
        @CsvSource({
                // 会员折扣边界：银卡 0.98、金卡 0.95、黑钻 0.90
                "600, SILVER,  52.92",  // 54.00 * 0.98 = 52.92
                "600, GOLD,    51.30",  // 54.00 * 0.95 = 51.30
                "600, DIAMOND, 48.60",  // 54.00 * 0.90 = 48.60
        })
        @DisplayName("TC-FB-05 会员折扣对封顶前费用的作用")
        void memberDiscount(int minutes, MemberLevel level, double expected) {
            TestSupport.assertMoney(expected, order(minutes, level, false).calcUnitFee());
        }

        @Test
        @DisplayName("TC-FB-06 最低消费边界 0.5 元")
        void minimumFee() {
            // 免费段内 -> 0.5 元
            TestSupport.assertMoney(0.50, order(1, MemberLevel.NORMAL, false).calcUnitFee());
            TestSupport.assertMoney(0.50, order(60, MemberLevel.NORMAL, false).calcUnitFee());
            // 优惠券把费用扣到 0.5 元以下时，应回抬到 0.5 元
            TestSupport.assertMoney(0.50, order(30, MemberLevel.NORMAL, true).calcUnitFee());
        }

        @Test
        @DisplayName("TC-FB-07 租借数量为总费用的线性乘数")
        void totalFeeIsLinear() {
            RentalOrder one = new RentalOrder("RT000001", 1L, "PB0001", 1, 100, MemberLevel.NORMAL, false);
            RentalOrder three = new RentalOrder("RT000002", 1L, "PB0001", 3, 100, MemberLevel.NORMAL, false);
            TestSupport.assertMoney(4.0, one.calcTotalFee());
            TestSupport.assertMoney(12.0, three.calcTotalFee());
        }

        @ParameterizedTest(name = "租借时长={0} -> 拒绝")
        @ValueSource(ints = {0, -1, 1441, 100000})
        @DisplayName("TC-FB-08 租借时长输入域 [1, 1440] 的无效侧")
        void invalidDuration(int minutes) {
            TestSupport.assertBizError(ErrorCode.RENTAL_DURATION_OUT_OF_RANGE,
                    () -> order(minutes, MemberLevel.NORMAL, false));
        }

        @ParameterizedTest(name = "租借时长={0} -> 接受")
        @ValueSource(ints = {1, 2, 719, 720, 1440})
        @DisplayName("TC-FB-09 租借时长输入域 [1, 1440] 的有效侧边界")
        void validDuration(int minutes) {
            TestSupport.assertNoBizError(() -> {
                RentalOrder o = order(minutes, MemberLevel.NORMAL, false);
                assertEquals(minutes, o.getUseMinutes());
            });
        }

        @Test
        @DisplayName("TC-FB-10 租借数量非正数必须被拒绝")
        void invalidRentCount() {
            TestSupport.assertBizError(ErrorCode.PARAM_NULL,
                    () -> new RentalOrder("RT000001", 1L, "PB0001", 0, 60, MemberLevel.NORMAL, false));
            TestSupport.assertBizError(ErrorCode.PARAM_NULL,
                    () -> new RentalOrder("RT000001", 1L, "PB0001", -1, 60, MemberLevel.NORMAL, false));
        }
    }
}
