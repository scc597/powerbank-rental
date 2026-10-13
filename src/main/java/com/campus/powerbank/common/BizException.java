package com.campus.powerbank.common;

/**
 * 业务异常。
 *
 * <p>被测系统在业务规则不满足时统一抛出该异常，携带 {@link ErrorCode} 便于测试用例断言，
 * 也便于主程序把错误友好地呈现给用户。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + "：" + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
