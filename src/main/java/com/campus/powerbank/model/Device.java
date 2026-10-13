package com.campus.powerbank.model;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;

/**
 * 充电宝设备（柜机）。
 *
 * <p>设备容量 totalCapacity 的有效范围为 [10, 100]，是第三类具有明确有效范围的输入数据；
 * {@link #rentOut(int)} / {@link #returnIn(int)} 通过分支与循环控制库存，
 * 形成多条可执行路径，便于做数据流分析。</p>
 */
public class Device {

    /** 设备容量有效下界（含）。 */
    public static final int MIN_CAPACITY = 10;

    /** 设备容量有效上界（含）。 */
    public static final int MAX_CAPACITY = 100;

    private final String deviceNo;

    /** 设备总容量，有效范围 [10, 100]。 */
    private final int totalCapacity;

    /** 当前可用库存，有效范围 [0, totalCapacity]。 */
    private int availableStock;

    private boolean faulty;

    private boolean online = true;

    public Device(String deviceNo, int totalCapacity) {
        validateCapacity(totalCapacity);
        this.deviceNo = deviceNo;
        this.totalCapacity = totalCapacity;
        this.availableStock = totalCapacity;
    }

    /**
     * 校验设备容量是否落在有效范围 [10, 100]。
     *
     * @param capacity 容量
     * @throws BizException 容量越界
     */
    public static void validateCapacity(int capacity) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new BizException(ErrorCode.PARAM_NULL, "设备容量 " + capacity + " 超出 [10,100]");
        }
    }

    /**
     * 租出指定数量的充电宝。
     *
     * @param count 租出数量，必须为正
     * @throws BizException 设备故障、离线或库存不足
     */
    public void rentOut(int count) {
        if (count <= 0) {
            throw new BizException(ErrorCode.PARAM_NULL, "租出数量必须为正数");
        }
        // 多条件分支：故障 / 离线 / 库存不足分别给出不同错误
        if (faulty) {
            throw new BizException(ErrorCode.DEVICE_UNAVAILABLE, "设备 " + deviceNo + " 故障中");
        }
        if (!online) {
            throw new BizException(ErrorCode.DEVICE_UNAVAILABLE, "设备 " + deviceNo + " 离线");
        }
        if (availableStock < count) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH,
                    "剩余 " + availableStock + "，需求 " + count);
        }
        this.availableStock -= count;
    }

    /**
     * 归还指定数量的充电宝，超出容量的部分视为异常数据被丢弃（防御性处理）。
     *
     * @param count 归还数量，必须为正
     */
    public void returnIn(int count) {
        if (count <= 0) {
            throw new BizException(ErrorCode.PARAM_NULL, "归还数量必须为正数");
        }
        this.availableStock += count;
        // 防御性分支：库存不应超过总容量
        if (this.availableStock > this.totalCapacity) {
            this.availableStock = this.totalCapacity;
        }
    }

    /**
     * 批量补充库存至满（循环结构）。
     *
     * @return 本次实际补充的数量
     */
    public int refill() {
        int need = totalCapacity - availableStock;
        if (need <= 0) {
            return 0;
        }
        // 循环逐个入库，覆盖 need == 0 / > 0 两条路径
        for (int i = 0; i < need; i++) {
            availableStock++;
        }
        return need;
    }

    /** 设备当前状态，由库存与故障、在线标记共同决定。 */
    public DeviceStatus getStatus() {
        return DeviceStatus.of(availableStock, faulty, online);
    }

    /** 是否允许租借：必须处于空闲可租状态。 */
    public boolean isRentable() {
        return getStatus() == DeviceStatus.IDLE;
    }

    public String getDeviceNo() {
        return deviceNo;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public boolean isFaulty() {
        return faulty;
    }

    public void setFaulty(boolean faulty) {
        this.faulty = faulty;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    @Override
    public String toString() {
        return String.format("Device[no=%s, capacity=%d, available=%d, status=%s]",
                deviceNo, totalCapacity, availableStock, getStatus().getLabel());
    }
}
