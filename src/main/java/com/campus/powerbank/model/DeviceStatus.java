package com.campus.powerbank.model;

/**
 * 充电宝设备状态。
 *
 * <p>设备状态由「可用库存数」和「是否故障」共同决定，构成一组多条件业务规则。</p>
 */
public enum DeviceStatus {

    /** 故障停用。 */
    FAULT("故障停用"),

    /** 离线（未联网）。 */
    OFFLINE("离线"),

    /** 空闲可租。 */
    IDLE("空闲可租"),

    /** 已被租出。 */
    RENTED("已租出");

    private final String label;

    DeviceStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 依据库存与故障标记判定设备状态（多条件共同决定结果）。
     *
     * <p>规则优先级：故障 &gt; 离线 &gt; 库存为 0 &gt; 可租 &gt; 已租出。</p>
     *
     * @param availableStock 可用库存数
     * @param faulty         是否故障
     * @param online         是否在线
     * @return 设备状态
     */
    public static DeviceStatus of(int availableStock, boolean faulty, boolean online) {
        if (faulty) {
            return FAULT;
        }
        if (!online) {
            return OFFLINE;
        }
        if (availableStock <= 0) {
            return RENTED;
        }
        return IDLE;
    }
}
