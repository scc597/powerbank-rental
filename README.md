# 共享充电宝租借与计费系统

> 软件测试概论 · 实验一 · 边界值测试被测系统
> Java 17 + Maven + JUnit 5，**开箱即编译运行**

---

## 一、系统概述

本项目实现了一个**共享充电宝租借与计费系统**，用于支撑边界值测试实验。系统具备明确的应用背景、多个相对独立的功能模块、多类具有有效范围的输入数据，以及由多条件共同决定的业务规则。

### 1.1 功能模块

| 模块 | 包路径 | 职责 |
|------|--------|------|
| 会员模块 | `service/MemberService` | 会员注册、查重、充值、等级判定、信用分维护 |
| 设备模块 | `service/DeviceService` | 柜机登记、库存查询、补充库存、故障/离线标记 |
| 租借模块 | `service/RentalService` | 租借下单、归还结算、信用分奖惩（业务中枢） |
| 报表模块 | `service/ReportService` | 营收统计、等级分布、设备状态分布、免押金名单 |

模块间依赖关系：

```
        MemberService  ──────┐
              ▲              ▼
              │      RentalService ──────┐
        DeviceService ──────▲            │
              ▲              │            ▼
              └──────────────┴────  ReportService
```

`RentalService` 同时调用 `MemberService` 与 `DeviceService`，
`ReportService` 聚合三者数据，构成完整的跨模块接口交互与数据传递。

### 1.2 有效输入范围（边界值测试对象）

| 输入变量 | 类型 | 有效范围 | 边界点数 |
|---------|------|---------|---------|
| 会员年龄 `age` | int | **[18, 60]** 闭区间 | 18, 60 |
| 单次充值金额 | int | **[1, 2000]** 闭区间 | 1, 2000 |
| 累计充值金额 | int | **[0, 20000]** 闭区间 | 0, 20000 |
| 信用分 `credit` | int | **[0, 1000]** 闭区间 | 0, 1000 |
| 设备容量 | int | **[10, 100]** 闭区间 | 10, 100 |
| 租借时长 | 分钟 | **[1, 1440]** 闭区间 | 1, 1440 |
| 租借数量 | int | **[1, 设备剩余库存]** | 1, stock |
| 会员等级阈值 | 元 | 100 / 500 / 2000 | 99/100, 499/500, 1999/2000 |
| 信用分阈值 | 分 | 500（可租借）、800（免押金） | 499/500, 799/800 |
| 费用封顶 | 元 | **60.0** | 674/675/676 分钟处 |
| 免费时长 | 分钟 | **60** | 60/61 |
| 阶梯切换 | 分钟 | **600** | 600/601 |
| 优惠券门槛 | 元 | **5.0** | 109/110 分钟处 |
| 最低消费 | 元 | **0.5** | 0.5 |

### 1.3 核心业务规则：费用计算（多条件依次判定）

```
输入：租借时长 minutes、会员等级 level、是否用券 useCoupon

1. 免费段     minutes ≤ 60                        → 0.50（最低消费）
2. 阶梯计费   60 < minutes ≤ 600                  → (minutes-60) × 0.10
              minutes > 600                      → 54.00 + (minutes-600) × 0.08
3. 费用封顶   阶梯费用 > 60.00                    → 60.00
4. 会员折扣   普通1.00 银卡0.98 金卡0.95 黑钻0.90
5. 优惠券     折后 ≥ 5.00 且用券                  → 再减 2.00
6. 最低消费   最终 < 0.50                        → 0.50

总费用 = 单台费用 × 租借数量（保留 2 位小数，HALF_UP）
```

### 1.4 控制结构

- **分支**：菜单 `switch`（9 个菜单项 + default）、费用计算的 6 级 `if` 短路、等级判定的 4 级分支、设备状态 4 级优先级判定
- **循环**：`Device.refill()` 逐个补货循环、`ReportService` 遍历聚合循环
- **多条可执行路径**：正常路径 + 6 类异常路径（A001/A002/M002/D002/D003/T001/O001/O002）
- **可分析的变量数据流**：`gross`/`discount`/`settledCount` 等局部变量在循环中定义→赋值→使用，路径清晰

---

## 二、运行环境

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | **17（LTS）** | 实测最低 16，见下方「版本下限说明」 |
| Maven | 3.6+ | 首次构建需联网下载依赖 |
| 操作系统 | Windows / macOS / Linux | 跨平台 |

验证环境：OpenJDK 17.0.12 (Microsoft) + Apache Maven 3.9.16 + Windows。

### 版本下限说明（为何是 17，最低 16）

代码实际用到的语言特性及对应版本：

| 特性 | 引入版本 | 是否必需 |
|------|---------|---------|
| `record` 记录类 | Java 16 | **是（硬性下限）** |
| `switch` 表达式箭头分支 | Java 14 | 是 |
| `var` | Java 10 | 是 |
| `sealed` / 文本块 / `switch` 模式匹配 | 17 / 15 / 21 | 否，未使用 |

实测结果：`--release 15` 编译失败（`'record' 不是内部或外部命令`），`--release 16` 通过。

因此 **16 是功能下限，17 是推荐版本**——差异不在功能，而在于：

1. **17 是 LTS**，16 已停止免费安全更新
2. 本机 IDEA 仅注册了 `ms-17` 与 `corretto-1.8`，无 16
3. `IdeaProjects` 下 13/18 个项目均用 `JDK_17`，与之一致便于统一维护

若因特殊需要降到 16，只需将 `pom.xml` 中 `<release>` 改为 `16`，
并把 `.idea/misc.xml` 的 `languageLevel` 改为 `JDK_16`，代码本身无需改动。

### 项目位置与打开方式

**项目根目录**：本仓库所在目录（即含 `pom.xml` 的那一层）。

以 IntelliJ IDEA 打开：`File → Open` → 选择项目根目录，
IDEA 会自动识别为 Maven 项目并导入 JUnit 5 依赖。

项目已附带 IDEA 配置：`.idea/misc.xml`（语言级别 `JDK_17`、输出 `out/`）、
`.idea/compiler.xml`（字节码 17、源码 UTF-8）、`.idea/encodings.xml`（全项目 UTF-8）。

> `.idea/misc.xml` 中 `project-jdk-name` 默认写为本机的 `ms-17`。
> 若你的 IDEA 中没有该名称，首次打开时在
> `File → Project Structure → Project` 选择任一 JDK 17 即可。

---

## 三、编译与运行

### 3.1 一键运行测试（推荐）

```bash
cd powerbank-rental
mvn clean test
```

预期输出结尾：

```
[INFO] Tests run: 141, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.2 运行交互式主程序

```bash
# 方式一：Maven 方式
mvn compile
mvn exec:java

# 方式二：纯 Java 方式（不依赖 exec 插件）
mvn compile
java -cp target/classes com.campus.powerbank.Main
```

### 3.3 完全不依赖 Maven（仅用 JDK）

若 Maven 不可用或依赖未下载，可直接用 JDK 原生命令：

```bash
# 1) 编译主程序
javac -encoding UTF-8 -d target/classes $(find src/main/java -name "*.java")

# 2) 运行交互式主程序
java -cp target/classes com.campus.powerbank.Main

# 3)（可选）打包为可执行 jar
jar --create --file powerbank-rental.jar --main-class com.campus.powerbank.Main -C target/classes .
java -jar powerbank-rental.jar
```

> 主程序已在启动时强制标准输出为 UTF-8，**无需**额外设置控制台编码或加 `-Dfile.encoding`。

### 3.4 离线构建

若依赖已缓存在本地仓库，可加 `-o` 离线执行：

```bash
mvn -o clean test
```

> 注：`mvn package` 需要 `maven-jar-plugin` 及其依赖，该插件在部分环境的本地仓库中
> 未缓存。测试（`mvn test`）与运行（`mvn compile` + `java`）不受影响；
> 需要打包时可用 3.3 节的 JDK 原生命令。

---

## 四、程序操作说明

启动后进入九项菜单：

```
  1. 会员注册      2. 会员列表      3. 会员充值
  4. 设备列表      5. 发起租借      6. 归还结算
  7. 修改信用分    8. 运营报表      9. 退出系统
```

### 演示脚本（约 3 分钟）

| 步骤 | 操作 | 输入 | 预期 |
|-----|------|------|------|
| 1 | 选择 `1` 注册 | 姓名`张三`、手机号`13800138000`、年龄`30` | 注册成功，会员号 1001，普通会员 |
| 2 | 选择 `3` 充值 | 会员号直接回车、金额 `1500` | 累计 1500 元，升为金卡（折扣 0.95） |
| 3 | 选择 `5` 租借 | 会员号回车、设备 `PB0001`、数量 `3`、分钟 `200`、用券 `y` | 订单 RT000001，预计 33.90 元 |
| 4 | 选择 `6` 结算 | 订单号 `RT000001` | 应收 33.90 元，信用分 +1 |
| 5 | 选择 `8` 报表 | — | 毛收入 33.90 元，等级分布正确 |

> 费用验算：(200−60)×0.10 = 14.00 → ×0.95 = 13.30 → 用券 −2 = 11.30 → ×3 = **33.90 元**

### 异常输入演示

| 操作 | 输入 | 预期提示 |
|------|------|---------|
| 注册 | 手机号 `23800138000` | 注册失败 [A002] 手机号必须是 1 开头的 11 位数字 |
| 注册 | 年龄 `61` | 注册失败 [A001] 年龄必须在 18~60 岁之间 |
| 租借 | 设备 `PB9999` | 租借失败 [D001] 充电宝设备不存在 |
| 租借 | 时长 `1441` | 租借失败 [T001] 租借时长必须在 1~1440 分钟之间 |
| 租借 | 数量 `0` | 租借失败 [P001] 参数不能为空：租借数量必须为正数 |

所有业务异常均被捕获并以 `[错误码] 说明` 格式友好输出，程序不会崩溃。

---

## 五、测试代码说明

### 5.1 测试类清单

| 测试类 | 方法数 | 测试方法 |
|--------|-------|---------|
| `boundary/MemberBoundaryTest` | 39 | **方法一** 正常边界值（5 点法） |
| `boundary/MemberRobustBoundaryTest` | 39 | **方法二** 健壮边界值（6 点法） |
| `boundary/DeviceAndFeeBoundaryTest` | 44 | 设备容量、库存、费用多条件边界 |
| `integration/RentalFlowIntegrationTest` | 19 | 跨模块集成路径 |

合计 **141** 个测试用例（参数化测试展开后）。
### 5.2 两种方法的差异

| 维度 | 正常边界值（5 点法） | 健壮边界值（6 点法） |
|------|---------------------|---------------------|
| 取点 | min、min+1、内点、max−1、max | 增加 **min−1、max+1** |
| 覆盖输入域 | 年龄、充值金额、信用分 | 年龄、充值金额、信用分、手机号 |
| 数值型输入点 | 22 个 | 19 个 |
| 用例总数 | **39** | **39** |
| 关注点 | 边界内侧功能是否正确 | 额外验证无效输入的健壮性 |
| 缺陷发现 | 未发现被测程序缺陷 | **发现 1 个被测程序缺陷（DEF-01）** |

> 说明：两种方法用例数相同但覆盖面不同——健壮边界值用 3 个"格式/唯一性"
> 输入域（手机号）替换了部分数值点，换来了对无效输入的更强验证能力。

### 5.3 单独运行某个测试类

```bash
mvn test -Dtest=MemberBoundaryTest
mvn test -Dtest=MemberRobustBoundaryTest
mvn test -Dtest=DeviceAndFeeBoundaryTest
mvn test -Dtest=RentalFlowIntegrationTest
```

### 5.4 查看测试报告

```bash
mvn test
# HTML 报告：target/surefire-reports/index.html
```

---

## 六、缺陷记录与修正

### DEF-01（严重）空输入导致功能不可用

| 项目 | 内容 |
|------|------|
| **发现方法** | 健壮边界值测试 + 手工交互验证 |
| **模块** | `Main` 交互层 |
| **现象** | 菜单 3/5/7 提示"会员号（直接回车用当前登录会员）"，但直接回车却报"输入不是合法的整数"并取消操作，**提示语与实际行为矛盾** |
| **根因** | `readLong()` 直接对空串调用 `Long.parseLong("")`，抛出 `NumberFormatException`；缺少对空输入的分支处理 |
| **影响** | 用户按提示操作必然失败，主流程不可达 |
| **修改前** | 见 `docs/before-fix/Main-readLong.java.txt` |
| **修正措施** | 新增 `readMemberId(String)`：空输入返回 `-1L`（使用当前登录会员），非数字返回 `null` 并提示 |
| **修改后验证** | 三个菜单项均可直接回车；141 个测试全部通过 |
| **状态** | ✅ 已修复并回归 |

> 说明：测试执行过程中共发现并修正 3 处缺陷，DEF-01 为被测程序缺陷，
> 另 2 处（费用封顶预期值计算错误、库存边界用例数据错误）属于**测试用例自身的预期值错误**，
> 修正过程同样记录在 `docs/实验报告.md` 第五节。

---

## 七、项目结构

```
powerbank-rental/
├── pom.xml                                  Maven 构建配置
├── README.md                                本文档
├── docs/
│   ├── 实验报告.md                          完整实验报告
│   ├── 测试用例表.md                        全部测试用例明细
│   └── before-fix/                          修改前的原始程序版本
└── src/
    ├── main/java/com/campus/powerbank/
    │   ├── Main.java                        交互式主程序（9 项菜单）
    │   ├── common/
    │   │   ├── BizException.java            业务异常
    │   │   └── ErrorCode.java               15 个业务错误码
    │   ├── model/
    │   │   ├── Member.java                  会员实体 + 范围校验
    │   │   ├── MemberLevel.java             会员等级 + 阈值判定
    │   │   ├── Device.java                  设备实体 + 库存控制
    │   │   ├── DeviceStatus.java            设备状态 + 多条件优先级
    │   │   ├── RentalOrder.java             订单实体 + 费用计算
    │   │   └── OrderStatus.java             订单状态
    │   └── service/
    │       ├── MemberService.java           模块一：会员
    │       ├── DeviceService.java           模块二：设备
    │       ├── RentalService.java           模块三：租借（业务中枢）
    │       └── ReportService.java           模块四：报表
    └── test/java/com/campus/powerbank/
        ├── TestSupport.java                 断言辅助（金额比较等）
        ├── boundary/
        │   ├── MemberBoundaryTest.java              方法一：正常边界值
        │   ├── MemberRobustBoundaryTest.java       方法二：健壮边界值
        │   └── DeviceAndFeeBoundaryTest.java       设备与费用边界
        └── integration/
            └── RentalFlowIntegrationTest.java      跨模块集成测试
```

---

## 八、常见问题

**Q1：提示 `Unknown lifecycle phase "test"` 或依赖下载失败？**
首次构建需联网。执行 `mvn clean test`（不加 `-o`）让 Maven 下载依赖。

**Q2：控制台中文乱码？**
主程序启动时已将标准输出强制设为 UTF-8，**正常情况下不会出现乱码**。
若在极老的控制台中仍有问题，可执行 `chcp 65001` 后重试。
pom.xml 也已为 surefire 配置 `-Dfile.encoding=UTF-8`，保证测试报告中文正常。

**Q3：`exec-maven-plugin` 或 `maven-jar-plugin` 无法解析？**
这两个插件需要联网下载。若不可用，改用 3.3 节的 JDK 原生命令：
`javac` 编译 + `java -cp target/classes com.campus.powerbank.Main`。

**Q4：如何修改业务规则重新测试？**
所有阈值与费率均为 `RentalOrder` / `Member` / `Device` 中的 `public static final` 常量，
修改后测试用例会立即暴露规则变化带来的差异，便于回归验证。
