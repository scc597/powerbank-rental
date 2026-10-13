package com.campus.powerbank.service;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Device;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块二：设备服务。
 *
 * <p>负责充电宝柜机的登记、库存查询、补充与故障标记。
 * 设备容量有效范围 [10, 100]，可作为边界值测试对象。</p>
 */
public class DeviceService {

    /** 设备编号自增序列。 */
    private long noGenerator = 0L;

    private final Map<String, Device> devices = new LinkedHashMap<>();

    /**
     * 登记一台新设备。
     *
     * @param capacity 设备容量，有效范围 [10, 100]
     * @return 新登记的设备
     */
    public Device register(int capacity) {
        Device.validateCapacity(capacity);
        String deviceNo = String.format("PB%04d", ++noGenerator);
        Device device = new Device(deviceNo, capacity);
        devices.put(deviceNo, device);
        return device;
    }

    /**
     * 按编号查询设备。
     *
     * @param deviceNo 设备编号
     * @return 设备
     * @throws BizException 设备不存在
     */
    public Device findByNo(String deviceNo) {
        Device device = devices.get(deviceNo);
        if (device == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND, String.valueOf(deviceNo));
        }
        return device;
    }

    /**
     * 补充库存至满。
     *
     * @param deviceNo 设备编号
     * @return 实际补充数量
     */
    public int refill(String deviceNo) {
        return findByNo(deviceNo).refill();
    }

    /**
     * 标记设备故障 / 恢复。
     *
     * @param deviceNo 设备编号
     * @param faulty   是否故障
     */
    public void setFaulty(String deviceNo, boolean faulty) {
        findByNo(deviceNo).setFaulty(faulty);
    }

    /**
     * 标记设备在线 / 离线。
     *
     * @param deviceNo 设备编号
     * @param online   是否在线
     */
    public void setOnline(String deviceNo, boolean online) {
        findByNo(deviceNo).setOnline(online);
    }

    /**
     * 统计可租设备数量（循环 + 分支的典型实现）。
     *
     * @return 处于「空闲可租」状态的设备数
     */
    public int countRentable() {
        int count = 0;
        for (Device device : devices.values()) {
            if (device.isRentable()) {
                count++;
            }
        }
        return count;
    }

    /** 返回全部设备（只读副本列表）。 */
    public List<Device> listAll() {
        return new ArrayList<>(devices.values());
    }

    public void clear() {
        devices.clear();
        noGenerator = 0L;
    }

    public int size() {
        return devices.size();
    }
}
