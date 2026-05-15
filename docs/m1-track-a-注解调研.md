# M1 Track A - @LogRecord 与 @OperateLog 注解调研

> 调研时间：2026-05-15
> 调研人：Claude Code
> 结论：**统一使用 @LogRecord（mzt），不存在 @OperateLog 注解。**

---

## 一、核心发现

### 1.1 @OperateLog 注解不存在

经过全量代码扫描，yudao-cloud 项目中**不存在 `@OperateLog` 注解**。

存在的相关类：
- `OperateLogDO` — 操作日志数据表实体（system 模块）
- `OperateLogCreateReqDTO` — 创建操作日志的 DTO
- `OperateLogServiceImpl` — 操作日志存储服务
- `OperateLogController` — 管理后台查询操作日志的接口

这些都是**存储和查询**操作日志的类，不是注解。

### 1.2 实际在用的是 @LogRecord（mzt-logapi）

yudao 已集成 `mzt-biz-log`（`com.mzt.logapi`）库，通过 `YudaoOperateLogConfiguration` 配置：

```java
@EnableLogRecord(tenant = "")
@AutoConfiguration
public class YudaoOperateLogConfiguration {
    @Bean @Primary
    public ILogRecordService iLogRecordServiceImpl() {
        return new LogRecordServiceImpl();
    }
}
```

`LogRecordServiceImpl` 的核心逻辑：
1. 接收 mzt `LogRecord` 对象
2. 转换为 yudao `OperateLogCreateReqDTO`
3. 调用 `operateLogApi.createOperateLogAsync()` 异步写入 `system_operate_log` 表

**结论：`@LogRecord`（mzt）≠ 独立的第三方日志。它已经接入 yudao 的操作日志存储系统。**

---

## 二、@LogRecord（mzt）能力分析

### 2.1 注解参数

```java
@LogRecord(
    type = "餐饮订单",           // 模块类型（对应 OperateLog.type）
    subType = "创建订单",        // 操作子类型（对应 OperateLog.subType）
    bizNo = "{{#order.orderNo}}", // 业务编号（SpEL 表达式，对应 OperateLog.bizId）
    success = "创建了订单【{{#order.orderNo}}】，金额 ¥{{#order.payAmount}}", // 成功消息
    fail = "订单创建失败",        // 失败消息（可选）
    extra = "..."                // 额外信息 JSON（可选）
)
```

### 2.2 支持的功能

| 功能 | 支持情况 | 说明 |
|------|---------|------|
| 记录操作类型 | ✅ | type + subType |
| 记录业务编号 | ✅ | bizNo（SpEL 表达式，可引用方法参数） |
| 记录操作内容 | ✅ | success/fail 消息，支持 SpEL 模板 |
| 记录前后值 diff | ✅ | `{_DIFF{#updateReqVO}}` — 自动对比新旧值 |
| 记录操作人 | ✅ | LogRecordServiceImpl 自动获取当前登录用户 |
| 记录请求信息 | ✅ | LogRecordServiceImpl 自动获取 URL/IP/UA |
| 记录 TraceId | ✅ | 自动关联调用链 |
| 异步写入 | ✅ | 不阻塞业务流程 |
| 条件记录 | ✅ | condition 参数（SpEL 表达式） |
| 操作人类型 | ✅ | 自动区分 Admin/App 用户 |

### 2.3 现有覆盖情况

restaurant 模块中已使用 @LogRecord 的方法：

| 实体 | Service | 方法 | type | subType |
|------|---------|------|------|---------|
| 订单 | RestaurantOrderServiceImpl | createOrder | 餐饮订单 | 创建订单 |
| 订单 | RestaurantOrderServiceImpl | cancelOrder | 餐饮订单 | 取消订单 |
| 订单 | PayNotifyController | onPaySuccess | 餐饮订单 | 支付成功 |
| 套餐 | RestaurantComboServiceImpl | createCombo | 餐饮套餐 | 创建套餐 |
| 套餐 | RestaurantComboServiceImpl | updateCombo | 餐饮套餐 | 更新套餐 |
| 套餐 | RestaurantComboServiceImpl | deleteCombo | 删除套餐 | — |
| 菜品 | RestaurantDishSpuServiceImpl | createDishSpu | 餐饮菜品 | 创建菜品 |
| 菜品 | RestaurantDishSpuServiceImpl | updateDishSpu | 餐饮菜品 | 更新菜品 |
| 菜品 | RestaurantDishSpuServiceImpl | deleteDishSpu | 餐饮菜品 | 删除菜品 |

**共计 9 个方法，覆盖 3 个实体（订单/套餐/菜品）。**

### 2.4 缺失覆盖的实体（0 日志保护）

| 实体 | 缺失的写方法数 | 风险 |
|------|-------------|------|
| 品牌 (Brand) | 3 | 低 |
| 门店 (Store) | 3 | 低 |
| 分类 (Category) | 3 | 低 |
| SKU (DishSku) | 3 | 高（含价格） |
| 加料 (DishAddon) | 3 | 高（含价格） |
| 门店菜品 (StoreDish) | 9 | 高（价格覆盖/批量状态） |
| 桌台 (Table) | 4 | 低 |
| 厨房档口 (KitchenStation) | 3 | 低 |
| 打印机 (Printer) | 3 | 低 |
| 打印模板 (PrinterTemplate) | 1 | 低 |
| KDS | 2 | 高（订单状态变更） |
| 导入 (DishImport) | 1 | 中 |

---

## 三、推荐方案：统一用 @LogRecord

### 3.1 为什么不新建 @OperateLog 注解

1. **重复造轮子**：@LogRecord 已具备 @OperateLog 期望的所有能力
2. **已有集成**：@LogRecord → LogRecordServiceImpl → OperateLog 存储 → 管理后台可查
3. **已有先例**：ORDER/COMBO/DISH 已经在用，扩展即可
4. **risk 更低**：不引入新依赖，不改变现有架构

### 3.2 加固策略

**在 Service 层加 @LogRecord**（与现有 ORDER/COMBO/DISH 一致）：

```java
// Service 层（推荐，有事务上下文）
@Override
@LogRecord(type = "门店菜品", subType = "覆盖价格", bizNo = "{{#storeId}}",
        success = "覆盖门店菜品价格，共更新 {{#priceMap.size()}} 个菜品")
@Transactional
public void overridePrice(Long storeId, Map<Long, BigDecimal> priceMap) { ... }
```

**在 Controller 层加 @LogRecord**（仅当 Service 层不可行时，如 PayNotifyController 已有先例）：

```java
// Controller 层（备选，无事务上下文）
@PostMapping("/pay-success")
@LogRecord(type = ORDER_TYPE, subType = ORDER_PAY_SUCCESS_SUB_TYPE, ...)
public String onPaySuccess(...) { ... }
```

### 3.3 分层建议

| 层级 | 是否有 @LogRecord | 推荐 |
|------|-----------------|------|
| Controller 层 | 已有 @ApiAccessLog（访问日志） | 保留，不冲突 |
| Service 层 | 需加 @LogRecord（业务操作日志） | **主要加固位置** |
| Controller 层 | 可加 @LogRecord | 仅在 Service 不可行时 |

`@ApiAccessLog` 和 `@LogRecord` 互补：
- @ApiAccessLog = "谁在什么时候调了哪个接口"（Nginx access log 级别）
- @LogRecord = "谁在什么时候做了什么业务操作，改了什么东西"（审计日志级别）

---

## 四、@LogRecord 记录前后值的写法

### 4.1 自动 diff（推荐）

```java
@LogRecord(type = "门店菜品", subType = "更新菜品", bizNo = "{{#updateReqVO.id}}",
        success = "更新了菜品: {_DIFF{#updateReqVO}}")
public void updateStoreDish(StoreDishUpdateReqVO updateReqVO) {
    // {_DIFF{#updateReqVO}} 自动对比数据库中旧值与新值
}
```

### 4.2 手动记录（复杂场景）

```java
@LogRecord(type = "门店菜品", subType = "覆盖价格", bizNo = "{{#storeId}}",
        success = "覆盖门店菜品价格，旧总价=¥{{#oldTotal}}，新总价=¥{{#newTotal}}，影响 {{#count}} 个菜品")
public void overridePrice(Long storeId, Map<Long, BigDecimal> priceMap) {
    // 在方法内计算 oldTotal/newTotal/count，SpEL 自动取方法返回值/参数
}
```

---

## 五、决策

| 决策项 | 结论 |
|--------|------|
| 用什么注解 | **统一用 @LogRecord（mzt）** |
| 加在哪层 | **主要加 Service 层**（与 ORDER/COMBO/DISH 一致） |
| 是否新建 @OperateLog | **不建**，@LogRecord 已满足需求 |
| Controller 层是否加 | 已有 @ApiAccessLog 保留；特殊场景（如 PayNotifyController）可加 @LogRecord |
| 前后值记录方式 | 优先用 `{_DIFF{...}}` 自动 diff |
| 常量管理 | 扩展现有 `LogRecordConstants` 接口 |
