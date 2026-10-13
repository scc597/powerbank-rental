package com.campus.powerbank.model;

/**
 * 会员等级。
 *
 * <p>等级由「累计充值金额」这一有效范围 [0, 20000] 的输入决定，
 * 属于典型的可做边界值分析的枚举分支。</p>
 *
 * <p>等级门槛（闭区间下界）：</p>
 * <ul>
 *   <li>NORMAL  普通会员：[0, 99]</li>
 *   <li>SILVER  银卡会员：[100, 499]</li>
 *   <li>GOLD    金卡会员：[500, 1999]</li>
 *   <li>DIAMOND 黑钻会员：[2000, 20000]</li>
 * </ul>
 */
public enum MemberLevel {

    NORMAL("普通会员", 0),
    SILVER("银卡会员", 100),
    GOLD("金卡会员", 500),
    DIAMOND("黑钻会员", 2000);

    private final String label;

    /** 进入该等级所需的累计充值金额下界（含）。 */
    private final int threshold;

    MemberLevel(String label, int threshold) {
        this.label = label;
        this.threshold = threshold;
    }

    public String getLabel() {
        return label;
    }

    public int getThreshold() {
        return threshold;
    }

    /**
     * 依据累计充值金额判定会员等级（多分支条件判断）。
     *
     * @param totalRecharge 累计充值金额，单位元
     * @return 对应的会员等级
     */
    public static MemberLevel of(int totalRecharge) {
        // 自高向低匹配，保证落在正确区间
        if (totalRecharge >= DIAMOND.threshold) {
            return DIAMOND;
        }
        if (totalRecharge >= GOLD.threshold) {
            return GOLD;
        }
        if (totalRecharge >= SILVER.threshold) {
            return SILVER;
        }
        return NORMAL;
    }

    /**
     * 折扣率，与 {@link #of(int)} 的判定顺序保持一致。
     *
     * @return 折扣系数，如 0.95 表示 95 折
     */
    public double getDiscountRate() {
        return switch (this) {
            case NORMAL -> 1.00;
            case SILVER -> 0.98;
            case GOLD -> 0.95;
            case DIAMOND -> 0.90;
        };
    }
}
