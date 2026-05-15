# M1 Track A - 金额变更类接口完整清单

> 扫描时间：2026-05-15
> 扫描范围：restaurant 模块所有 Controller + Service + DO
> 扫描方式：grep BigDecimal.*(amount|price|Amount|Price) + refund/discount/waive

---

## 一、扫描结论

### 退款（refund）
- **无独立退款接口**。只有 ErrorCode `REFUND_NOT_EXISTS` 预留。
- 退款逻辑合并在 `cancelOrder`（AppOrderController）和 `onPaySuccess`（PayNotifyController）中。

### 抹零/打折（discount/waive）
- **无独立抹零/打折接口**。
- `discountAmount` 字段在 `RestaurantOrderDO` 和 `AppOrderRespVO` 中存在。
- 当前 `createOrder` 硬编码 `discountAmount = BigDecimal.ZERO`。
- 未来如果需要打折/抹零功能，必须新增端点并**立即归入高风险**。

### 结论
> 3 种金额命脉（取消/退款/抹零）当前合并覆盖，第 1 批 8 个端点已足够。
> 未来新增 refund/discount/waive 独立接口时，必须按高风险标准加固。

---

## 二、所有含金额字段的写端点

| # | Controller | 方法 | 金额字段 | 当前日志 | 风险 |
|---|-----------|------|---------|---------|------|
| 1 | AppOrderController | createOrder | originalAmount, discountAmount, payAmount, unitPrice | Service @LogRecord | 🟢 已覆盖 |
| 2 | AppOrderController | cancelOrder | payAmount（退款涉及） | Service @LogRecord | 🟡 需补 extra |
| 3 | PayNotifyController | onPaySuccess | payAmount（确认收入） | Controller @LogRecord | 🟡 需补 extra |
| 4 | RestaurantStoreDishController | overridePrice | price（覆盖价格） | **无** | 🔴 第1批 |
| 5 | RestaurantDishSkuController | createSku | price, memberPrice, costPrice | **无** | 🔴 第1批 |
| 6 | RestaurantDishSkuController | updateSku | price, memberPrice, costPrice | **无** | 🔴 第2批 |
| 7 | RestaurantDishAddonController | createAddon | extraPrice | **无** | 🟡 第2批 |
| 8 | RestaurantDishAddonController | updateAddon | extraPrice | **无** | 🟡 第2批 |
| 9 | RestaurantDishSpuController | createDishSpu | price, minPrice, maxPrice | Service @LogRecord | 🟢 已覆盖 |
| 10 | RestaurantDishSpuController | updateDishSpu | price, minPrice, maxPrice | Service @LogRecord + diff | 🟢 已覆盖 |
| 11 | RestaurantComboController | createCombo | comboPrice, originalPrice | Service @LogRecord | 🟢 已覆盖 |
| 12 | RestaurantComboController | updateCombo | comboPrice, originalPrice | Service @LogRecord + diff | 🟢 已覆盖 |
| 13 | RestaurantDishImportController | importDishes | price（批量导入） | **无** | 🟡 第3批 |

---

## 三、金额字段分布（DO 层）

| DO | 金额字段 | 数量 |
|----|---------|------|
| RestaurantOrderDO | originalAmount, discountAmount, payAmount | 3 |
| RestaurantOrderItemDO | unitPrice | 1 |
| RestaurantDishSpuDO | price, minPrice, maxPrice | 3 |
| RestaurantDishSkuDO | price, memberPrice, costPrice | 3 |
| RestaurantDishAddonDO | extraPrice | 1 |
| RestaurantComboDO | comboPrice, originalPrice | 2 |
| RestaurantComboItemDO | extraPrice | 1 |
| RestaurantStoreDishDO | price | 1 |

**共 8 个 DO 含金额字段，其中 6 个的写操作已部分覆盖 @LogRecord。**

---

## 四、风险升级建议

以下端点含金额字段但在第 2 批中（原本归为中风险），**建议升级到第 1 批**：

| 端点 | 含金额字段 | 建议 |
|------|---------|------|
| createSku | price, memberPrice, costPrice | ⬆️ 升级到第 1 批 |
| updateSku | price, memberPrice, costPrice | ⬆️ 升级到第 1 批 |
| createAddon | extraPrice | 🟡 保留第 2 批（加料金额低） |
| updateAddon | extraPrice | 🟡 保留第 2 批 |

**理由**：SKU 的 price/memberPrice/costPrice 是核心定价字段，影响所有订单金额计算。createSku 和 updateSku 应该升级到第 1 批高风险。

→ **第 1 批从 8 个升级为 10 个端点**（新增 createSku + updateSku）
