package com.campus.powerbank.model;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;

/**
 * 会员实体。
 *
 * <p>会员身上承载了两类具有明确有效范围的输入数据，是本系统边界值测试的主要对象：</p>
 * <ul>
 *   <li>年龄 age：有效范围 [18, 60]，属于必填且连续区间</li>
 *   <li>单次充值金额 rechargeAmount：有效范围 [1, 2000]，属于必填且连续区间</li>
 * </ul>
 */
public class Member {

    /** 年龄有效下界（含）。 */
    public static final int MIN_AGE = 18;

    /** 年龄有效上界（含）。 */
    public static final int MAX_AGE = 60;

    /** 单次充值金额有效下界（含）。 */
    public static final int MIN_RECHARGE = 1;

    /** 单次充值金额有效上界（含）。 */
    public static final int MAX_RECHARGE = 2000;

    /** 累计充值金额有效上界（含），超出则拒绝继续充值。 */
    public static final int MAX_TOTAL_RECHARGE = 20000;

    /** 信用分有效下界（含）。 */
    public static final int MIN_CREDIT = 0;

    /** 信用分有效上界（含）。 */
    public static final int MAX_CREDIT = 1000;

    private final long id;

    private String phone;

    private String name;

    private int age;

    /** 累计充值金额，决定 {@link #level}。 */
    private int totalRecharge;

    private MemberLevel level = MemberLevel.NORMAL;

    /** 信用分，影响租借押金与是否允许租借。 */
    private int credit = 800;

    public Member(long id, String phone, String name, int age) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        setAge(age);
        validatePhone(phone);
    }

    /**
     * 设置年龄并做有效范围校验（闭区间 [18, 60]）。
     *
     * @throws BizException 年龄越界
     */
    public final void setAge(int age) {
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BizException(ErrorCode.AGE_OUT_OF_RANGE, "当前值 " + age);
        }
        this.age = age;
    }

    /**
     * 设置信用分并做有效范围校验（闭区间 [0, 1000]）。
     *
     * @throws BizException 信用分越界
     */
    public void setCredit(int credit) {
        if (credit < MIN_CREDIT || credit > MAX_CREDIT) {
            throw new BizException(ErrorCode.CREDIT_OUT_OF_RANGE, "当前值 " + credit);
        }
        this.credit = credit;
    }

    /**
     * 校验手机号：必须为 11 位且首位是 1。
     *
     * @param phone 手机号
     * @throws BizException 格式非法
     */
    public static void validatePhone(String phone) {
        if (phone == null || phone.length() != 11
                || phone.charAt(0) != '1'
                || !phone.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new BizException(ErrorCode.PHONE_ILLEGAL, String.valueOf(phone));
        }
    }

    /**
     * 充值：累加累计充值金额并重新判定等级。
     *
     * @param amount 单次充值金额，有效范围 [1, 2000]
     * @throws BizException 金额越界或累计超出上限
     */
    public void recharge(int amount) {
        if (amount < MIN_RECHARGE || amount > MAX_RECHARGE) {
            throw new BizException(ErrorCode.RECHARGE_OUT_OF_RANGE, "当前值 " + amount);
        }
        long after = (long) this.totalRecharge + amount;
        if (after > MAX_TOTAL_RECHARGE) {
            throw new BizException(ErrorCode.RECHARGE_OUT_OF_RANGE,
                    "累计充值上限 " + MAX_TOTAL_RECHARGE + " 元，本次后为 " + after);
        }
        this.totalRecharge = (int) after;
        this.level = MemberLevel.of(this.totalRecharge);
    }

    /**
     * 信用分是否达到免押金门槛（>= 800 免押金）。
     *
     * @return true 表示免押金
     */
    public boolean isCreditExempt() {
        return credit >= 800;
    }

    /** 是否允许租借：信用分低于 500 视为受限用户。 */
    public boolean canRent() {
        return credit >= 500;
    }

    public long getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public int getTotalRecharge() {
        return totalRecharge;
    }

    public MemberLevel getLevel() {
        return level;
    }

    public int getCredit() {
        return credit;
    }

    @Override
    public String toString() {
        return String.format("Member[id=%d, name=%s, phone=%s, age=%d, totalRecharge=%d, level=%s, credit=%d]",
                id, name, phone, age, totalRecharge, level.getLabel(), credit);
    }
}
