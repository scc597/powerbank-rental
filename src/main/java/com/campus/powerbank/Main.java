package com.campus.powerbank;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.model.Device;
import com.campus.powerbank.model.Member;
import com.campus.powerbank.model.RentalOrder;
import com.campus.powerbank.service.DeviceService;
import com.campus.powerbank.service.MemberService;
import com.campus.powerbank.service.RentalService;
import com.campus.powerbank.service.ReportService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 共享充电宝租借与计费系统 —— 交互式主程序。
 *
 * <p>菜单式演示四个功能模块，覆盖正常流程与各类异常输入，
 * 可直接用于手工执行边界值测试用例。</p>
 *
 * <p>运行方式：</p>
 * <ul>
 *   <li>{@code mvn exec:java}</li>
 *   <li>{@code java -cp target/classes com.campus.powerbank.Main}</li>
 * </ul>
 */
public class Main {

    private final MemberService memberService = new MemberService();

    private final DeviceService deviceService = new DeviceService();

    private final RentalService rentalService = new RentalService(memberService, deviceService);

    private final ReportService reportService = new ReportService(memberService, deviceService, rentalService);

    private final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    /** 当前登录会员 id，未登录为 -1。 */
    private long currentMemberId = -1L;

    public static void main(String[] args) {
        useUtf8Console();
        new Main().run();
    }

    /**
     * 强制标准输出使用 UTF-8，避免 Windows 默认 GBK 控制台出现中文乱码。
     * 必须在任何输出之前调用。
     *
     * <p>注意：输入侧无需在此处理，{@link #reader} 已使用 UTF-8 解码。</p>
     */
    private static void useUtf8Console() {
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                StandardCharsets.UTF_8));
    }

    /** 主循环。 */
    public void run() {
        printBanner();
        // 初始化一台演示设备，方便直接体验
        Device demo = deviceService.register(20);
        System.out.printf("已初始化演示设备：%s（容量 %d，初始库存 %d）%n",
                demo.getDeviceNo(), demo.getTotalCapacity(), demo.getAvailableStock());

        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("请选择操作：");
            if (choice == null) {
                System.out.println("输入流已关闭，程序退出。");
                break;
            }
            choice = choice.trim();
            // 多分支选择结构，覆盖 1~9 与非法输入
            switch (choice) {
                case "1" -> registerMember();
                case "2" -> listMembers();
                case "3" -> recharge();
                case "4" -> listDevices();
                case "5" -> rent();
                case "6" -> settle();
                case "7" -> updateCredit();
                case "8" -> report();
                case "9" -> {
                    running = false;
                    System.out.println("感谢使用，再见！");
                }
                default -> System.out.println("无效选项，请输入 1~9 之间的数字。");
            }
        }
    }

    /** 1. 注册会员。 */
    private void registerMember() {
        System.out.println("\n--- 会员注册 ---");
        String name = readLine("姓名：");
        String phone = readLine("手机号（1 开头的 11 位）：");
        Integer age = readInt("年龄（18~60）：");
        if (age == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        try {
            Member m = memberService.register(phone, name, age);
            currentMemberId = m.getId();
            System.out.printf("注册成功：%s（会员号 %d，当前等级 %s，免押金：%s）%n",
                    m.getName(), m.getId(), m.getLevel().getLabel(),
                    m.isCreditExempt() ? "是" : "否");
        } catch (BizException e) {
            System.out.printf("注册失败 [%s] %s%n", e.getErrorCode().getCode(), e.getMessage());
        }
    }

    /** 2. 会员列表。 */
    private void listMembers() {
        List<Member> all = memberService.listAll();
        System.out.println("\n--- 会员列表（共 " + all.size() + " 人）---");
        if (all.isEmpty()) {
            System.out.println("暂无会员，请先注册。");
            return;
        }
        System.out.printf("%-8s%-10s%-14s%-6s%-10s%-12s%-8s%n",
                "会员号", "姓名", "手机号", "年龄", "累计充值", "等级", "信用分");
        for (Member m : all) {
            System.out.printf("%-10d%-12s%-16s%-8d%-12d%-14s%-8d%n",
                    m.getId(), m.getName(), m.getPhone(), m.getAge(),
                    m.getTotalRecharge(), m.getLevel().getLabel(), m.getCredit());
        }
    }

    /** 3. 充值。 */
    private void recharge() {
        System.out.println("\n--- 会员充值 ---");
        Long id = readMemberId("会员号（直接回车用当前登录会员）：");
        if (id == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        if (id == -1L) {
            id = currentMemberId;
        }
        if (id == -1L) {
            System.out.println("尚未登录任何会员，请先执行注册。");
            return;
        }
        Integer amount = readInt("充值金额（1~2000）：");
        if (amount == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        try {
            Member m = memberService.recharge(id, amount);
            System.out.printf("充值成功：%s 累计充值 %d 元，等级升为 %s（折扣率 %.2f）%n",
                    m.getName(), m.getTotalRecharge(),
                    m.getLevel().getLabel(), m.getLevel().getDiscountRate());
        } catch (BizException e) {
            System.out.printf("充值失败 [%s] %s%n", e.getErrorCode().getCode(), e.getMessage());
        }
    }

    /** 4. 设备列表。 */
    private void listDevices() {
        List<Device> all = deviceService.listAll();
        System.out.println("\n--- 设备列表（共 " + all.size() + " 台）---");
        if (all.isEmpty()) {
            System.out.println("暂无设备。");
            return;
        }
        System.out.printf("%-10s%-10s%-10s%-12s%n", "设备号", "总容量", "可用库存", "状态");
        for (Device d : all) {
            System.out.printf("%-12s%-12d%-12d%-14s%n",
                    d.getDeviceNo(), d.getTotalCapacity(),
                    d.getAvailableStock(), d.getStatus().getLabel());
        }
    }

    /** 5. 租借。 */
    private void rent() {
        System.out.println("\n--- 发起租借 ---");
        Long memberId = readMemberId("会员号（直接回车用当前登录会员）：");
        if (memberId == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        if (memberId == -1L) {
            memberId = currentMemberId;
        }
        if (memberId == -1L) {
            System.out.println("尚未登录任何会员，请先执行注册。");
            return;
        }
        String deviceNo = readLine("设备编号（如 PB0001）：");
        Integer count = readInt("租借数量：");
        Integer minutes = readInt("预计使用分钟数（1~1440）：");
        String coupon = readLine("是否使用优惠券（y/n）：");
        if (count == null || minutes == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        boolean useCoupon = coupon != null && coupon.trim().toLowerCase().startsWith("y");
        try {
            RentalOrder order = rentalService.rent(memberId, deviceNo, count, minutes, useCoupon);
            System.out.printf("租借成功：订单号 %s%n", order.getOrderNo());
            System.out.printf("预计费用：%.2f 元（按此金额结算，实际以归还时为准）%n",
                    rentalService.previewFee(order.getOrderNo()));
        } catch (BizException e) {
            System.out.printf("租借失败 [%s] %s%n", e.getErrorCode().getCode(), e.getMessage());
        }
    }

    /** 6. 归还结算。 */
    private void settle() {
        System.out.println("\n--- 归还结算 ---");
        String orderNo = readLine("订单号（如 RT000001）：");
        if (orderNo == null || orderNo.isBlank()) {
            System.out.println("输入无效，已取消。");
            return;
        }
        try {
            RentalService.SettlementResult r = rentalService.settle(orderNo.trim());
            System.out.printf("结算成功：订单 %s%n", r.orderNo());
            System.out.printf("应收费用：%.2f 元%n", r.fee());
            System.out.printf("信用分变动：%+d，结算后信用分：%d%n",
                    r.creditDelta(), r.creditAfter());
        } catch (BizException e) {
            System.out.printf("结算失败 [%s] %s%n", e.getErrorCode().getCode(), e.getMessage());
        }
    }

    /** 7. 修改信用分。 */
    private void updateCredit() {
        System.out.println("\n--- 修改信用分 ---");
        Long id = readMemberId("会员号（直接回车用当前登录会员）：");
        if (id == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        if (id == -1L) {
            id = currentMemberId;
        }
        if (id == -1L) {
            System.out.println("尚未登录任何会员，请先执行注册。");
            return;
        }
        Integer credit = readInt("新信用分（0~1000）：");
        if (credit == null) {
            System.out.println("输入无效，已取消。");
            return;
        }
        try {
            Member m = memberService.updateCredit(id, credit);
            System.out.printf("修改成功：%s 信用分 %d，免押金：%s，可租借：%s%n",
                    m.getName(), m.getCredit(),
                    m.isCreditExempt() ? "是" : "否",
                    m.canRent() ? "是" : "否");
        } catch (BizException e) {
            System.out.printf("修改失败 [%s] %s%n", e.getErrorCode().getCode(), e.getMessage());
        }
    }

    /** 8. 运营报表。 */
    private void report() {
        System.out.println("\n--- 运营报表 ---");
        var r = reportService.revenueReport();
        System.out.printf("会员总数：%d    可租设备数：%d%n", r.memberCount(), r.rentableDevices());
        System.out.printf("已结算订单：%d    租借中订单：%d%n", r.settledCount(), r.rentingCount());
        System.out.printf("毛收入：%.2f 元    会员折扣让利：%.2f 元%n",
                r.grossRevenue(), r.discountAmount());

        System.out.println("\n会员等级分布：");
        reportService.memberLevelDistribution()
                .forEach((k, v) -> System.out.printf("  %-10s %d 人%n", k.getLabel(), v));

        System.out.println("\n设备状态分布：");
        reportService.deviceStatusDistribution()
                .forEach((k, v) -> System.out.printf("  %-10s %d 台%n", k.getLabel(), v));

        List<String> exempt = reportService.creditExemptMembers();
        System.out.println("\n免押金会员（信用分 >= 800）："
                + (exempt.isEmpty() ? "无" : String.join("、", exempt)));
    }

    /* ==================== 打印辅助 ==================== */

    private void printBanner() {
        System.out.println("======================================================");
        System.out.println("      共享充电宝租借与计费系统  v1.0");
        System.out.println("      软件测试概论 · 实验一 · 边界值测试被测系统");
        System.out.println("======================================================");
    }

    private void printMenu() {
        System.out.println("\n------------------ 主 菜 单 ------------------");
        System.out.println("  1. 会员注册");
        System.out.println("  2. 会员列表");
        System.out.println("  3. 会员充值");
        System.out.println("  4. 设备列表");
        System.out.println("  5. 发起租借");
        System.out.println("  6. 归还结算");
        System.out.println("  7. 修改信用分");
        System.out.println("  8. 运营报表");
        System.out.println("  9. 退出系统");
        System.out.println("--------------------------------------------");
    }

    /**
     * 读取一行输入，EOF 时返回 null。
     *
     * @param prompt 提示语
     * @return 输入内容，EOF 返回 null
     */
    private String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("读取输入失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 读取整数输入，非整数返回 null。
     *
     * @param prompt 提示语
     * @return 整数，非法返回 null
     */
    private Integer readInt(String prompt) {
        String line = readLine(prompt);
        if (line == null) {
            return null;
        }
        try {
            return Integer.parseInt(line.trim());
        } catch (NumberFormatException e) {
            System.out.println("输入不是合法的整数。");
            return null;
        }
    }

    /**
     * 读取会员号：允许直接回车，此时返回 -1 表示「使用当前登录会员」。
     *
     * @param prompt 提示语
     * @return 会员号，输入为空返回 -1，非法输入返回 null
     */
    private Long readMemberId(String prompt) {
        String line = readLine(prompt);
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        // 分支：空输入代表使用当前登录会员，与提示语保持一致
        if (trimmed.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            System.out.println("输入不是合法的整数。");
            return null;
        }
    }
}
