package com.campus.powerbank.common;

/**
 * 业务错误码。
 *
 * <p>每个错误码都对应一条可被边界值测试用例直接断言的规则，
 * 便于在 {@code @DisplayName} 中直接引用业务语义。</p>
 */
public enum ErrorCode {

    /** 参数为 null。 */
    PARAM_NULL("P001", "参数不能为空"),

    /** 年龄超出 [18, 60] 有效范围。 */
    AGE_OUT_OF_RANGE("A001", "年龄必须在 18~60 岁之间"),

    /** 手机号不满足 11 位且首位为 1 的格式。 */
    PHONE_ILLEGAL("A002", "手机号必须是 1 开头的 11 位数字"),

    /** 会员不存在。 */
    MEMBER_NOT_FOUND("M001", "会员不存在"),

    /** 手机号已被注册。 */
    PHONE_DUPLICATED("M002", "手机号已被注册"),

    /** 单次充值金额超出 [1, 2000] 有效范围。 */
    RECHARGE_OUT_OF_RANGE("R001", "单次充值金额必须在 1~2000 元之间"),

    /** 设备编号不存在。 */
    DEVICE_NOT_FOUND("D001", "充电宝设备不存在"),

    /** 设备当前不可租借（故障 / 离线）。 */
    DEVICE_UNAVAILABLE("D002", "设备当前不可租借"),

    /** 设备可用库存不足。 */
    STOCK_NOT_ENOUGH("D003", "设备可用库存不足"),

    /** 租借时长超出 [1, 1440] 分钟有效范围。 */
    RENTAL_DURATION_OUT_OF_RANGE("T001", "租借时长必须在 1~1440 分钟之间"),

    /** 订单不存在。 */
    ORDER_NOT_FOUND("O001", "租借订单不存在"),

    /** 订单当前状态不允许执行该操作。 */
    ORDER_STATUS_ILLEGAL("O002", "订单状态不允许执行该操作"),

    /** 会员信用分超出 [0, 1000] 有效范围。 */
    CREDIT_OUT_OF_RANGE("C001", "信用分必须在 0~1000 之间"),

    /** 优惠券抵扣金额计算结果非法（不应出现，防御性校验）。 */
    COUPON_AMOUNT_ILLEGAL("C002", "优惠券抵扣金额不合法");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
