package com.campus.powerbank.service;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Device;
import com.campus.powerbank.model.Member;
import com.campus.powerbank.model.RentalOrder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块三：租借服务 —— 系统的业务中枢。
 *
 * <p>该模块同时依赖 {@link MemberService} 与 {@link DeviceService}，
 * 完整覆盖了「模块之间存在函数调用、数据传递与接口交互」的要求。
 * 租借流程包含多重校验分支，是边界值测试的主要目标路径。</p>
 */
public class RentalService {

    private final MemberService memberService;

    private final DeviceService deviceService;

    /** 订单号自增序列。 */
    private long orderGenerator = 0L;

    private final Map<String, RentalOrder> orders = new LinkedHashMap<>();

    public RentalService(MemberService memberService, DeviceService deviceService) {
        this.memberService = memberService;
        this.deviceService = deviceService;
    }

    /**
     * 发起租借。
     *
     * <p>依次执行 5 项校验（每项都是独立的边界值测试点）：</p>
     * <ol>
     *   <li>会员是否存在</li>
     *   <li>会员信用分是否允许租借（&gt;= 500）</li>
     *   <li>设备是否存在且可租（未故障、在线、有库存）</li>
     *   <li>租借数量 [1, 设备剩余库存]</li>
     *   <li>预计使用时长 [1, 1440]</li>
     * </ol>
     *
     * @param memberId   会员 id
     * @param deviceNo   设备编号
     * @param rentCount  租借数量
     * @param useMinutes 预计使用分钟数，有效范围 [1, 1440]
     * @param useCoupon  是否使用优惠券
     * @return 创建的租借订单
     * @throws BizException 任一校验不通过
     */
    public RentalOrder rent(long memberId, String deviceNo, int rentCount,
                            int useMinutes, boolean useCoupon) {
        // 校验 1：会员存在性（跨模块调用）
        Member member = memberService.findById(memberId);

        // 校验 2：信用分是否允许租借（>= 500 放行）
        if (!member.canRent()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "信用分 " + member.getCredit() + " 低于 500，暂不可租借");
        }

        // 校验 3：设备存在性与可租状态（跨模块调用）
        Device device = deviceService.findByNo(deviceNo);
        if (device.getStatus() != com.campus.powerbank.model.DeviceStatus.IDLE) {
            throw new BizException(ErrorCode.DEVICE_UNAVAILABLE,
                    "设备当前状态：" + device.getStatus().getLabel());
        }

        // 校验 4：租借数量范围（受剩余库存上界约束）
        if (rentCount <= 0) {
            throw new BizException(ErrorCode.PARAM_NULL, "租借数量必须为正数");
        }
        if (rentCount > device.getAvailableStock()) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "剩余 " + device.getAvailableStock() + "，需求 " + rentCount);
        }

        // 校验 5：预计使用时长有效范围 [1, 1440]
        RentalOrder.validateUseMinutes(useMinutes);

        // 全部校验通过，扣减库存并生成订单
        device.rentOut(rentCount);
        String orderNo = String.format("RT%06d", ++orderGenerator);
        // 会员等级随订单快照传入，保证计费规则在订单生命周期内稳定
        RentalOrder order = new RentalOrder(orderNo, member.getId(), deviceNo,
                rentCount, useMinutes, member.getLevel(), useCoupon);
        orders.put(orderNo, order);
        return order;
    }

    /**
     * 归还并结算。
     *
     * @param orderNo 订单号
     * @return 结算明细
     * @throws BizException 订单不存在或状态不允许
     */
    public SettlementResult settle(String orderNo) {
        RentalOrder order = findOrder(orderNo);
        double fee = order.settle();
        // 跨模块调用：把充电宝还回设备
        Device device = deviceService.findByNo(order.getDeviceNo());
        device.returnIn(order.getRentCount());
        Member member = memberService.findById(order.getMemberId());

        // 多条件规则：按信用分与费用决定信用分奖惩
        int creditDelta = calcCreditDelta(order.getUseMinutes(), fee);
        int creditAfter = clampCredit(member.getCredit() + creditDelta);

        return new SettlementResult(orderNo, fee, creditDelta, creditAfter);
    }

    /**
     * 计算信用分变动（多条件共同决定）：
     * <ul>
     *   <li>按时归还（&lt;= 1440 分钟）且费用 &lt;= 10 元：+2</li>
     *   <li>按时归还但费用 &gt; 10 元：+1</li>
     *   <li>超时归还（&gt; 1440 分钟，实际不会发生，做防御性分支）：-5</li>
     * </ul>
     *
     * @param useMinutes 使用分钟数
     * @param fee        费用
     * @return 信用分变动值
     */
    private int calcCreditDelta(int useMinutes, double fee) {
        if (useMinutes > RentalOrder.MAX_USE_MINUTES) {
            return -5;
        }
        if (fee <= 10.0) {
            return 2;
        }
        return 1;
    }

    /** 信用分钳制到有效范围 [0, 1000]。 */
    private int clampCredit(int credit) {
        if (credit < Member.MIN_CREDIT) {
            return Member.MIN_CREDIT;
        }
        if (credit > Member.MAX_CREDIT) {
            return Member.MAX_CREDIT;
        }
        return credit;
    }

    /**
     * 按订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单
     * @throws BizException 订单不存在
     */
    public RentalOrder findOrder(String orderNo) {
        RentalOrder order = orders.get(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, String.valueOf(orderNo));
        }
        return order;
    }

    /**
     * 预览费用（不改变订单状态），用于交互式查询。
     *
     * @param orderNo 订单号
     * @return 订单总费用
     */
    public double previewFee(String orderNo) {
        return findOrder(orderNo).calcTotalFee();
    }

    /** 返回全部订单（只读副本列表）。 */
    public List<RentalOrder> listAll() {
        return new ArrayList<>(orders.values());
    }

    public void clear() {
        orders.clear();
        orderGenerator = 0L;
    }

    /**
     * 结算结果（值对象）。
     *
     * @param orderNo    订单号
     * @param fee        结算费用
     * @param creditDelta 信用分变动
     * @param creditAfter 变动后信用分
     */
    public record SettlementResult(String orderNo, double fee, int creditDelta, int creditAfter) {
    }
}
