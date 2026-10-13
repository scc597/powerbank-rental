package com.campus.powerbank.service;

import com.campus.powerbank.model.MemberLevel;
import com.campus.powerbank.model.RentalOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块四：运营报表服务。
 *
 * <p>聚合前三模块的数据，产出营收与会员分布报表。
 * 该模块内部使用循环与分支累加统计值，是系统中「变量定义—赋值—使用」关系
 * 最清晰的部分，便于做数据流覆盖分析。</p>
 */
public class ReportService {

    private final MemberService memberService;

    private final DeviceService deviceService;

    private final RentalService rentalService;

    public ReportService(MemberService memberService,
                         DeviceService deviceService,
                         RentalService rentalService) {
        this.memberService = memberService;
        this.deviceService = deviceService;
        this.rentalService = rentalService;
    }

    /**
     * 生成营收报表。
     *
     * @return 营收报表
     */
    public RevenueReport revenueReport() {
        double gross = 0.0;          // 定义：毛收入
        double discount = 0.0;       // 定义：会员折扣让利金额
        int settledCount = 0;        // 定义：已结算订单数
        int rentingCount = 0;        // 定义：租借中订单数

        for (RentalOrder order : rentalService.listAll()) {
            switch (order.getStatus()) {
                case SETTLED -> {
                    settledCount++;
                    gross += order.getFee();
                }
                case RENTING -> rentingCount++;
                default -> {
                    // RETURNED / CANCELLED 不计入营收
                }
            }
        }

        // 统计折扣让利：对每个已结算订单按等级折扣率反推原价与让利
        for (RentalOrder order : rentalService.listAll()) {
            if (order.getStatus() == com.campus.powerbank.model.OrderStatus.SETTLED) {
                double rate = order.getLevel().getDiscountRate();
                if (rate < 1.0) {
                    // 让利 = 折后价 × (1 - 折扣率) / 折扣率
                    discount += order.getFee() * (1 - rate) / rate;
                }
            }
        }

        return new RevenueReport(round2(gross), round2(discount),
                settledCount, rentingCount,
                deviceService.countRentable(), memberService.size());
    }

    /**
     * 生成会员等级分布报表（覆盖全部 4 个等级的分支路径）。
     *
     * @return key 为等级名、value 为人数的映射
     */
    public Map<MemberLevel, Integer> memberLevelDistribution() {
        Map<MemberLevel, Integer> dist = new LinkedHashMap<>();
        // 先把 4 个等级全部置 0，保证 key 顺序稳定
        for (MemberLevel level : MemberLevel.values()) {
            dist.put(level, 0);
        }
        for (var member : memberService.listAll()) {
            dist.merge(member.getLevel(), 1, Integer::sum);
        }
        return dist;
    }

    /**
     * 生成设备运行报表（覆盖设备 4 种状态）。
     *
     * @return 各状态设备数量
     */
    public Map<com.campus.powerbank.model.DeviceStatus, Integer> deviceStatusDistribution() {
        Map<com.campus.powerbank.model.DeviceStatus, Integer> dist = new LinkedHashMap<>();
        for (var status : com.campus.powerbank.model.DeviceStatus.values()) {
            dist.put(status, 0);
        }
        for (var device : deviceService.listAll()) {
            dist.merge(device.getStatus(), 1, Integer::sum);
        }
        return dist;
    }

    /**
     * 列出满足「免押金」条件的会员姓名（多条件：信用分 &gt;= 800）。
     *
     * @return 会员姓名列表
     */
    public List<String> creditExemptMembers() {
        List<String> names = new ArrayList<>();
        for (var member : memberService.listAll()) {
            if (member.isCreditExempt()) {
                names.add(member.getName());
            }
        }
        return names;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 营收报表（值对象）。
     *
     * @param grossRevenue      毛收入
     * @param discountAmount    会员折扣让利金额
     * @param settledCount      已结算订单数
     * @param rentingCount      租借中订单数
     * @param rentableDevices   可租设备数
     * @param memberCount       会员总数
     */
    public record RevenueReport(double grossRevenue, double discountAmount,
                                int settledCount, int rentingCount,
                                int rentableDevices, int memberCount) {
    }
}
