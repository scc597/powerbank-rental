package com.campus.powerbank.model;

/**
 * 租借订单状态。
 */
public enum OrderStatus {

    /** 租借中。 */
    RENTING("租借中"),

    /** 已归还待结算。 */
    RETURNED("已归还"),

    /** 已结算。 */
    SETTLED("已结算"),

    /** 已取消。 */
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
