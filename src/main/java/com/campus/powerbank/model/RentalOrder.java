package com.campus.powerbank.model;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;

/**
 * 租借订单。
 *
 * <p>订单承载了本系统最核心的一组多条件业务规则：<b>费用计算</b>。
 * 规则（按顺序依次判断，任一命中即短路）：</p>
 * <ol>
 *   <li><b>封顶规则</b>：阶梯费用达到 60 元封顶（单个充电宝）</li>
 *   <li><b>阶梯计费</b>：前 60 分钟免费；60~600 分钟按 0.1 元/分钟；超过 600 分钟按 0.08 元/分钟</li>
 *   <li><b>会员折扣</b>：按 {@link MemberLevel#getDiscountRate()} 对阶梯费用打折</li>
 *   <li><b>优惠券</b>：使用优惠券且折扣后费用 &gt;= 5 元，再减 2 元</li>
 *   <li><b>最低消费</b>：最终费用不足 0.5 元按 0.5 元收取</li>
 * </ol>
 *
 * <p>租借时长 useMinutes 的有效范围为 [1, 1440]（即最长 24 小时），是第四类具有明确有效范围的输入。</p>
 */
public class RentalOrder {

    /** 租借时长有效下界（含），单位分钟。 */
    public static final int MIN_USE_MINUTES = 1;

    /** 租借时长有效上界（含），单位分钟 —— 24 小时。 */
    public static final int MAX_USE_MINUTES = 1440;

    /** 免费时长，单位分钟。 */
    public static final int FREE_MINUTES = 60;

    /** 第一阶梯上限，单位分钟。 */
    public static final int FIRST_TIER_MINUTES = 600;

    /** 第一阶梯单价（元/分钟）。 */
    public static final double FIRST_TIER_RATE = 0.10;

    /** 第二阶梯单价（元/分钟）。 */
    public static final double SECOND_TIER_RATE = 0.08;

    /** 费用封顶（元）。 */
    public static final double FEE_CAP = 60.0;

    /** 优惠券面额（元）。 */
    public static final double COUPON_VALUE = 2.0;

    /** 使用优惠券的最低消费门槛（元）。 */
    public static final double COUPON_THRESHOLD = 5.0;

    /** 最低消费（元）。 */
    public static final double MIN_FEE = 0.5;

    private final String orderNo;

    private final long memberId;

    private final String deviceNo;

    private final int rentCount;

    private final int useMinutes;

    private final MemberLevel level;

    private final boolean useCoupon;

    private OrderStatus status = OrderStatus.RENTING;

    private double fee;

    public RentalOrder(String orderNo, long memberId, String deviceNo,
                       int rentCount, int useMinutes,
                       MemberLevel level, boolean useCoupon) {
        validateUseMinutes(useMinutes);
        if (rentCount <= 0) {
            throw new BizException(ErrorCode.PARAM_NULL, "租借数量必须为正数");
        }
        if (level == null) {
            throw new BizException(ErrorCode.PARAM_NULL, "会员等级不能为空");
        }
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.deviceNo = deviceNo;
        this.rentCount = rentCount;
        this.useMinutes = useMinutes;
        this.level = level;
        this.useCoupon = useCoupon;
    }

    /**
     * 校验租借时长是否落在有效范围 [1, 1440]（闭区间）。
     *
     * @param minutes 使用分钟数
     * @throws BizException 时长越界
     */
    public static void validateUseMinutes(int minutes) {
        if (minutes < MIN_USE_MINUTES || minutes > MAX_USE_MINUTES) {
            throw new BizException(ErrorCode.RENTAL_DURATION_OUT_OF_RANGE, "当前值 " + minutes);
        }
    }

    /**
     * 计算单个充电宝的租金（已考虑封顶、阶梯、折扣、优惠券与最低消费）。
     *
     * @return 单个租金，单位元
     */
    public double calcUnitFee() {
        // 1. 免费时长：前 60 分钟不收费
        int chargeable = useMinutes - FREE_MINUTES;
        if (chargeable <= 0) {
            return round2(MIN_FEE);
        }

        // 2. 阶梯计费
        double tierFee;
        if (chargeable <= FIRST_TIER_MINUTES - FREE_MINUTES) {
            // 单一分支：全部落在第一阶梯
            tierFee = chargeable * FIRST_TIER_RATE;
        } else {
            // 复合分支：拆成第一阶梯 + 第二阶梯两段累加
            int firstPart = FIRST_TIER_MINUTES - FREE_MINUTES;
            int secondPart = chargeable - firstPart;
            tierFee = firstPart * FIRST_TIER_RATE + secondPart * SECOND_TIER_RATE;
        }

        // 3. 封顶规则：按台计费的封顶上限为 50 元
        if (tierFee > FEE_CAP) {
            tierFee = FEE_CAP;
        }

        // 4. 会员折扣
        tierFee = tierFee * level.getDiscountRate();

        // 5. 优惠券：有门槛，达标则减 2 元
        if (useCoupon && tierFee >= COUPON_THRESHOLD) {
            tierFee -= COUPON_VALUE;
        }

        // 6. 最低消费：不足 0.5 元按 0.5 元收
        if (tierFee < MIN_FEE) {
            tierFee = MIN_FEE;
        }

        return round2(tierFee);
    }

    /**
     * 计算订单总费用 = 单个租金 × 租借数量。
     *
     * @return 总费用，单位元
     */
    public double calcTotalFee() {
        return round2(calcUnitFee() * rentCount);
    }

    /**
     * 归还并结算：计算费用并将状态置为已结算。
     *
     * @return 本次结算总费用
     * @throws BizException 订单状态不允许结算
     */
    public double settle() {
        if (status == OrderStatus.CANCELLED) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "订单已取消");
        }
        if (status == OrderStatus.SETTLED) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "订单已结算");
        }
        this.fee = calcTotalFee();
        this.status = OrderStatus.SETTLED;
        return this.fee;
    }

    /** 四舍五入保留两位小数，避免浮点累积误差。 */
    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public long getMemberId() {
        return memberId;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public int getRentCount() {
        return rentCount;
    }

    public int getUseMinutes() {
        return useMinutes;
    }

    public MemberLevel getLevel() {
        return level;
    }

    public boolean isUseCoupon() {
        return useCoupon;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getFee() {
        return fee;
    }

    @Override
    public String toString() {
        return String.format("RentalOrder[no=%s, member=%d, device=%s, count=%d, minutes=%d, level=%s, coupon=%s, fee=%.2f, status=%s]",
                orderNo, memberId, deviceNo, rentCount, useMinutes,
                level.name(), useCoupon, fee, status.getLabel());
    }
}
