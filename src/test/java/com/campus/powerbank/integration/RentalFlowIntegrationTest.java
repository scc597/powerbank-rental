package com.campus.powerbank.integration;

import com.campus.powerbank.TestSupport;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Device;
import com.campus.powerbank.model.Member;
import com.campus.powerbank.model.MemberLevel;
import com.campus.powerbank.model.RentalOrder;
import com.campus.powerbank.service.DeviceService;
import com.campus.powerbank.service.MemberService;
import com.campus.powerbank.service.RentalService;
import com.campus.powerbank.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：覆盖跨模块的完整业务路径。
 *
 * <p>通过「注册 → 充值 → 租借 → 结算 → 报表」的完整链路，
 * 验证模块之间的接口交互与数据传递是否正确。</p>
 */
@DisplayName("集成测试：跨模块业务路径")
class RentalFlowIntegrationTest {

    private MemberService memberService;
    private DeviceService deviceService;
    private RentalService rentalService;
    private ReportService reportService;

    private long memberId;
    private String deviceNo;

    @BeforeEach
    void setUp() {
        memberService = new MemberService();
        deviceService = new DeviceService();
        rentalService = new RentalService(memberService, deviceService);
        reportService = new ReportService(memberService, deviceService, rentalService);

        Member m = memberService.register("13800138000", "张三", 30);
        memberId = m.getId();
        Device d = deviceService.register(20);
        deviceNo = d.getDeviceNo();
    }

    @Test
    @DisplayName("TC-IT-01 完整主流程：注册→租借→结算→库存回补→报表统计")
    void fullHappyPath() {
        Device device = deviceService.findByNo(deviceNo);
        assertEquals(20, device.getAvailableStock(), "初始库存应为 20");

        // 租借 2 个，使用 100 分钟
        RentalOrder order = rentalService.rent(memberId, deviceNo, 2, 100, false);
        assertEquals("RT000001", order.getOrderNo());
        assertEquals(18, device.getAvailableStock(), "租借后库存应扣减为 18");

        // 结算：100 分钟 -> 40 分钟计费 * 0.1 = 4.00，x2 = 8.00
        RentalService.SettlementResult r = rentalService.settle(order.getOrderNo());
        TestSupport.assertMoney(8.00, r.fee());
        assertEquals(20, device.getAvailableStock(), "结算后库存应回补为 20");
        assertEquals(com.campus.powerbank.model.OrderStatus.SETTLED, order.getStatus());

        // 报表应统计到这笔收入
        var report = reportService.revenueReport();
        TestSupport.assertMoney(8.00, report.grossRevenue());
        assertEquals(1, report.settledCount());
        assertEquals(0, report.rentingCount());
    }

    @Test
    @DisplayName("TC-IT-02 信用分边界 499/500：影响能否租借")
    void creditGateForRenting() {
        // 信用分 499 -> 不可租借
        memberService.updateCredit(memberId, 499);
        TestSupport.assertBizError(ErrorCode.ORDER_STATUS_ILLEGAL,
                () -> rentalService.rent(memberId, deviceNo, 1, 60, false));

        // 信用分 500 -> 可租借
        memberService.updateCredit(memberId, 500);
        TestSupport.assertNoBizError(
                () -> rentalService.rent(memberId, deviceNo, 1, 60, false));
    }

    @Test
    @DisplayName("TC-IT-03 跨模块数据传递：订单内会员等级为下单时快照")
    void levelSnapshotInOrder() {
        // 先以普通会员下单
        RentalOrder before = rentalService.rent(memberId, deviceNo, 1, 300, false);
        assertEquals(MemberLevel.NORMAL, before.getLevel());

        // 下单后充值升为金卡
        memberService.recharge(memberId, 500);
        assertEquals(MemberLevel.GOLD, memberService.findById(memberId).getLevel());

        // 已生成的订单等级仍为快照值 NORMAL，不受后续充值影响
        assertEquals(MemberLevel.NORMAL, before.getLevel(),
                "订单等级应为下单时的快照，不随会员等级变化");

        // 新订单使用新等级
        RentalOrder after = rentalService.rent(memberId, deviceNo, 1, 300, false);
        assertEquals(MemberLevel.GOLD, after.getLevel(), "新订单应使用最新等级");
    }

    @Test
    @DisplayName("TC-IT-04 重复结算同一订单必须被拒绝")
    void doubleSettle() {
        RentalOrder order = rentalService.rent(memberId, deviceNo, 1, 60, false);
        rentalService.settle(order.getOrderNo());
        // 第二次结算 -> 拒绝
        TestSupport.assertBizError(ErrorCode.ORDER_STATUS_ILLEGAL,
                () -> rentalService.settle(order.getOrderNo()));
    }

    @Test
    @DisplayName("TC-IT-05 库存不足时租借被拒绝，库存不变")
    void insufficientStock() {
        Device device = deviceService.findByNo(deviceNo);
        // 租出 20 个后库存归零
        rentalService.rent(memberId, deviceNo, 20, 60, false);
        assertEquals(0, device.getAvailableStock());

        // 再租 1 个 -> 拒绝（设备状态为 RENTED）
        TestSupport.assertBizError(ErrorCode.DEVICE_UNAVAILABLE,
                () -> rentalService.rent(memberId, deviceNo, 1, 60, false));
        assertEquals(0, device.getAvailableStock(), "失败后库存不应变化");
    }

    @ParameterizedTest(name = "设备容量={0}，租借 {1} 个 -> 剩余库存 {2}")
    @CsvSource({
            "10,  10, 0",    // 刚好租满，库存归零
            "100, 100, 0",   // 容量上界刚好租满
            "10,  9,  1",    // 下界容量，租出 9 个
            "10,  5,  5",    // 中间点
            "20,  1,  19",   // 下界数量 1
            "20,  20, 0",    // 全部租满
    })
    @DisplayName("TC-IT-06 库存边界：租借数量与容量相等 / 差 1 / 中间值")
    void stockBoundary(int capacity, int rentCount, int expectedStock) {
        DeviceService ds = new DeviceService();
        MemberService ms = new MemberService();
        RentalService rs = new RentalService(ms, ds);
        Member m = ms.register("13800138000", "张三", 30);
        Device d = ds.register(capacity);

        rs.rent(m.getId(), d.getDeviceNo(), rentCount, 60, false);
        assertEquals(1, rs.listAll().size(), "应产生 1 条订单");
        assertEquals(expectedStock, d.getAvailableStock(),
                "容量 " + capacity + " 租出 " + rentCount + " 后剩余库存应为 " + expectedStock);

        // 再租 1 个必须失败（若库存已为 0）或成功（若仍有剩余）
        if (expectedStock == 0) {
            TestSupport.assertBizError(ErrorCode.DEVICE_UNAVAILABLE,
                    () -> rs.rent(m.getId(), d.getDeviceNo(), 1, 60, false));
            assertEquals(0, d.getAvailableStock(), "失败后库存不应变化");
        }
    }

    @Test
    @DisplayName("TC-IT-07 报表统计：等级分布覆盖全部 4 个分支")
    void reportLevelDistribution() {
        // setUp 中已有张三（普通会员）
        Member a = memberService.register("13800138001", "甲", 20);
        memberService.recharge(a.getId(), 100);   // 累计 100 -> SILVER
        Member b = memberService.register("13800138002", "乙", 21);
        memberService.recharge(b.getId(), 500);   // 累计 500 -> GOLD
        Member c = memberService.register("13800138003", "丙", 22);
        memberService.recharge(c.getId(), 2000);  // 累计 2000 -> DIAMOND

        var dist = reportService.memberLevelDistribution();
        assertEquals(4, dist.size(), "等级分布应覆盖全部 4 个等级");
        assertEquals(1, dist.get(MemberLevel.NORMAL), "仅张三为普通会员");
        assertEquals(1, dist.get(MemberLevel.SILVER));
        assertEquals(1, dist.get(MemberLevel.GOLD));
        assertEquals(1, dist.get(MemberLevel.DIAMOND));
        assertEquals(4, reportService.revenueReport().memberCount(), "会员总数应为 4");
    }

    @Test
    @DisplayName("TC-IT-08 免押金会员列表：信用分 800 边界")
    void creditExemptList() {
        memberService.updateCredit(memberId, 800);  // 张三 -> 免押金
        Member b = memberService.register("13800138001", "乙", 20);
        memberService.updateCredit(b.getId(), 799);  // 乙 -> 不免押金

        var exempt = reportService.creditExemptMembers();
        assertTrue(exempt.contains("张三"), "信用分 800 应免押金");
        assertFalse(exempt.contains("乙"), "信用分 799 不应免押金");
    }

    @ParameterizedTest(name = "使用 {0} 分钟，费用 {1} 元时信用分变动 {2}")
    @CsvSource({
            "60,   0.5,  2",    // 按时 + 低消费
            "300,  24.0, 1",    // 按时 + 高消费（>10）
            "600,  54.0, 1",
    })
    @DisplayName("TC-IT-09 信用分变动规则（费用分档 10 元边界）")
    void creditDeltaRule(int minutes, double fee, int expectedDelta) {
        RentalOrder order = rentalService.rent(memberId, deviceNo, 1, minutes, false);
        int before = memberService.findById(memberId).getCredit();
        RentalService.SettlementResult r = rentalService.settle(order.getOrderNo());
        assertEquals(expectedDelta, r.creditDelta(),
                "费用 " + fee + " 元应带来 " + expectedDelta + " 分变动");
        assertEquals(before + expectedDelta, r.creditAfter());
    }

    @Test
    @DisplayName("TC-IT-10 订单号不存在时结算必须被拒绝")
    void settleNonExistentOrder() {
        TestSupport.assertBizError(ErrorCode.ORDER_NOT_FOUND,
                () -> rentalService.settle("RT999999"));
    }

    @Test
    @DisplayName("TC-IT-11 会员不存在时租借必须被拒绝")
    void rentNonExistentMember() {
        TestSupport.assertBizError(ErrorCode.MEMBER_NOT_FOUND,
                () -> rentalService.rent(99999L, deviceNo, 1, 60, false));
    }

    @Test
    @DisplayName("TC-IT-12 设备不存在时租借必须被拒绝")
    void rentNonExistentDevice() {
        TestSupport.assertBizError(ErrorCode.DEVICE_NOT_FOUND,
                () -> rentalService.rent(memberId, "PB9999", 1, 60, false));
    }
}
