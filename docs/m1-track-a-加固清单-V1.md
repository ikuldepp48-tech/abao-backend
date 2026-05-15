# M1 Track A - 加固清单 V1

> 版本：V1（2026-05-15）
> 依据：`m1-track-a-operate-log-audit.md` + `m1-track-a-注解调研.md`
> 策略：**统一在 Service 层加 @LogRecord（mzt）**，扩展现有 LogRecordConstants

---

## 加固总览

```
全量端点：32 个写操作
已覆盖：   9 个（ORDER 3 + COMBO 3 + DISH 3）
待加固：   23 个（分布在 12 个实体）
分 3 批：  🔴 第1批 8 个高风险 → 🟡 第2批 8 个中风险 → 🟢 第3批 7 个低风险
```

---

## 第 1 批：🔴 高风险（8 个端点）

> 涉及金额变更、订单状态变更、批量状态操作
> **每条必须记录前后值**

### 1.1 批量沽清 / 恢复 / 上下架（StoreDishService）

**文件**：`RestaurantStoreDishServiceImpl.java`

| # | 方法 | 操作 | @LogRecord 配置 |
|---|------|------|----------------|
| 1 | `batchSoldOut` | 一键沽清 | `type="门店菜品", subType="一键沽清", bizNo="{{#storeId}}", success="一键沽清完成，沽清 {{#ids.size()}} 个菜品"` |
| 2 | `batchRestore` | 批量恢复 | `type="门店菜品", subType="批量恢复", bizNo="{{#storeId}}", success="批量恢复供应 {{#ids.size()}} 个菜品"` |
| 3 | `batchUpdateStatus` | 批量上下架 | `type="门店菜品", subType="批量上下架", bizNo="{{#storeId}}", success="批量更新状态，目标状态={{#status}}，影响 {{#ids.size()}} 个菜品"` |

**注意**：这 3 个方法目前没有 updateReqVO 可用来自动 diff，需要记关键参数。

### 1.2 覆盖价格（StoreDishService）

| # | 方法 | 操作 | @LogRecord 配置 |
|---|------|------|----------------|
| 4 | `overridePrice` | 覆盖门店菜品价格 | `type="门店菜品", subType="覆盖价格", bizNo="{{#storeId}}", success="覆盖门店菜品价格，共更新 {{#priceMap.size()}} 个菜品"` |

**加强建议**：此方法应在方法体内计算旧总价/新总价，通过返回值或参数让 SpEL 记录。

### 1.3 KDS 状态变更（KdsService）

**文件**：需创建 `KdsService`（当前逻辑在 Controller）

| # | 方法 | 操作 | @LogRecord 配置 |
|---|------|------|----------------|
| 5 | `startItem` | 开始制作 | `type="KDS", subType="开始制作", bizNo="{{#itemId}}", success="订单项【{{#itemId}}】开始制作"` |
| 6 | `finishItem` | 完成出餐 | `type="KDS", subType="完成出餐", bizNo="{{#itemId}}", success="订单项【{{#itemId}}】完成出餐，耗时 {{#elapsed}} 秒"` |

**注意**：KDS 当前 Controller 直接操作，需先提取到 Service 层再加 @LogRecord。

### 1.4 订单取消（已有 @LogRecord，需补前后值）

**文件**：`RestaurantOrderServiceImpl.java`

| # | 方法 | 当前状态 | 加固 |
|---|------|---------|------|
| 7 | `cancelOrder` | 已有 @LogRecord | 补充 extra 字段：`extra="{\"orderNo\":\"{{#orderNo}}\",\"fromStatus\":\"{{#oldStatus}}\",\"toStatus\":\"CANCELLED\"}"` |

### 1.5 支付成功回调（已有 @LogRecord，需补前后值）

**文件**：`PayNotifyController.java`

| # | 方法 | 当前状态 | 加固 |
|---|------|---------|------|
| 8 | `onPaySuccess` | Controller 层已有 @LogRecord | 补充 extra 字段记录支付金额和状态变更 |

---

## 第 2 批：🟡 中风险（8 个端点）

> 涉及核心实体 CRUD（含价格字段）
> **基础 @LogRecord 即可，价格相关升级到 diff 记录**

### 2.1 菜品 SKU（含价格）

**文件**：`RestaurantDishSkuServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 9 | `createSku` | `type="菜品SKU", subType="创建SKU", bizNo="{{#createReqVO.spuId}}", success="创建SKU【{{#createReqVO.name}}】，价格 ¥{{#createReqVO.price}}"` |
| 10 | `updateSku` | `type="菜品SKU", subType="更新SKU", bizNo="{{#updateReqVO.id}}", success="更新SKU: {_DIFF{#updateReqVO}}"` |
| 11 | `deleteSku` | `type="菜品SKU", subType="删除SKU", bizNo="{{#id}}", success="删除SKU【ID:{{#id}}】"` |

### 2.2 加料（含价格）

**文件**：`RestaurantDishAddonServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 12 | `createAddon` | `type="加料", subType="创建加料", bizNo="{{#createReqVO.spuId}}", success="创建加料【{{#createReqVO.name}}】，价格 ¥{{#createReqVO.price}}"` |
| 13 | `updateAddon` | `type="加料", subType="更新加料", bizNo="{{#updateReqVO.id}}", success="更新加料: {_DIFF{#updateReqVO}}"` |
| 14 | `deleteAddon` | `type="加料", subType="删除加料", bizNo="{{#id}}", success="删除加料【ID:{{#id}}】"` |

### 2.3 菜品 SPU 更新（已有 @LogRecord，确认 diff 覆盖价格字段）

| # | 方法 | 当前状态 | 加固 |
|---|------|---------|------|
| 15 | `updateDishSpu` | 已有 `{_DIFF{#updateReqVO}}` | **验证** diff 是否包含 price 字段 |

### 2.4 套餐更新（已有 @LogRecord，确认 diff 覆盖价格字段）

| # | 方法 | 当前状态 | 加固 |
|---|------|---------|------|
| 16 | `updateCombo` | 已有 `{_DIFF{#updateReqVO}}` | **验证** diff 是否包含价格和明细变更 |

---

## 第 3 批：🟢 低风险（7 个端点）

> 涉及配置/辅助数据的 CRUD
> **基础 @LogRecord，不强制 diff**

### 3.1 品牌

**文件**：`RestaurantBrandServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 17 | `createBrand` | `type="品牌", subType="创建品牌", bizNo="{{#createReqVO.name}}", success="创建品牌【{{#createReqVO.name}}】"` |
| 18 | `updateBrand` | `type="品牌", subType="更新品牌", bizNo="{{#updateReqVO.id}}", success="更新品牌【{{#updateReqVO.name}}】"` |
| 19 | `deleteBrand` | `type="品牌", subType="删除品牌", bizNo="{{#id}}", success="删除品牌【ID:{{#id}}】"` |

### 3.2 门店

**文件**：`RestaurantStoreServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 20 | `createStore` | `type="门店", subType="创建门店", bizNo="{{#createReqVO.name}}", success="创建门店【{{#createReqVO.name}}】"` |
| 21 | `updateStore` | `type="门店", subType="更新门店", bizNo="{{#updateReqVO.id}}", success="更新门店【{{#updateReqVO.name}}】"` |
| 22 | `deleteStore` | `type="门店", subType="删除门店", bizNo="{{#id}}", success="删除门店【ID:{{#id}}】"` |

### 3.3 分类

**文件**：`RestaurantCategoryServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 23 | `createCategory` | `type="分类", subType="创建分类", bizNo="{{#createReqVO.name}}", success="创建分类【{{#createReqVO.name}}】"` |
| 24 | `updateCategory` | `type="分类", subType="更新分类", bizNo="{{#updateReqVO.id}}", success="更新分类【{{#updateReqVO.name}}】"` |
| 25 | `deleteCategory` | `type="分类", subType="删除分类", bizNo="{{#id}}", success="删除分类【ID:{{#id}}】"` |

### 3.4 桌台

**文件**：`RestaurantTableServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 26 | `createTable` | `type="桌台", subType="创建桌台", bizNo="{{#createReqVO.storeId}}", success="创建桌台【{{#createReqVO.name}}】"` |
| 27 | `batchCreateTable` | `type="桌台", subType="批量创建桌台", bizNo="{{#createReqVO.storeId}}", success="批量创建 {{#createReqVO.count}} 个桌台"` |
| 28 | `updateTable` | `type="桌台", subType="更新桌台", bizNo="{{#updateReqVO.id}}", success="更新桌台【{{#updateReqVO.name}}】"` |
| 29 | `deleteTable` | `type="桌台", subType="删除桌台", bizNo="{{#id}}", success="删除桌台【ID:{{#id}}】"` |

### 3.5 厨房档口

**文件**：`RestaurantKitchenStationServiceImpl.java`

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 30 | `createStation` | `type="厨房档口", subType="创建档口", bizNo="{{#createReqVO.name}}", success="创建厨房档口【{{#createReqVO.name}}】"` |
| 31 | `updateStation` | `type="厨房档口", subType="更新档口", bizNo="{{#updateReqVO.id}}", success="更新厨房档口【{{#updateReqVO.name}}】"` |
| 32 | `deleteStation` | `type="厨房档口", subType="删除档口", bizNo="{{#id}}", success="删除厨房档口【ID:{{#id}}】"` |

### 3.6 打印机

**文件**：需确认 Service 实现类

| # | 方法 | @LogRecord 配置 |
|---|------|----------------|
| 33 | `createPrinter` | `type="打印机", subType="创建打印机", bizNo="{{#createReqVO.name}}", success="创建打印机【{{#createReqVO.name}}】"` |
| 34 | `updatePrinter` | `type="打印机", subType="更新打印机", bizNo="{{#updateReqVO.id}}", success="更新打印机配置"` |
| 35 | `deletePrinter` | `type="打印机", subType="删除打印机", bizNo="{{#id}}", success="删除打印机【ID:{{#id}}】"` |

### 3.7 打印模板 + 导入

| # | 方法 | @LogRecord 配置 | 位置 |
|---|------|----------------|------|
| 36 | `saveTemplate` | `type="打印模板", subType="保存模板", bizNo="{{#reqVO.id}}", success="保存打印模板"` | PrinterTemplateService |
| 37 | `importDishes` | `type="菜品导入", subType="Excel导入", bizNo="{{#file.getOriginalFilename()}}", success="Excel导入完成，共 {{#result.total}} 条，成功 {{#result.success}} 条"` | DishImportService |

---

## 加固前置工作

### 需要新建的 Service（当前逻辑在 Controller 中）

| 实体 | 当前状态 | 需要做 |
|------|---------|--------|
| KDS | Controller 直接操作 Mapper | **新建 KdsService**，迁移 `startItem`/`finishItem` 逻辑 |
| Printer | Controller 直接操作 Mapper | 已有 Service？需验证 |
| PrinterTemplate | Controller 直接操作 Mapper | 已有 Service？需验证 |
| DishImport | Controller 直接操作 Mapper | 已有 Service？需验证 |

### 需要扩展的 LogRecordConstants

```java
// 新增常量（在现有 LogRecordConstants 中追加）

// ======================= STORE_DISH 门店菜品 =======================
String STORE_DISH_TYPE = "门店菜品";
String STORE_DISH_SOLD_OUT_SUB_TYPE = "一键沽清";
String STORE_DISH_RESTORE_SUB_TYPE = "批量恢复";
String STORE_DISH_STATUS_SUB_TYPE = "批量上下架";
String STORE_DISH_PRICE_OVERRIDE_SUB_TYPE = "覆盖价格";

// ======================= SKU =======================
String SKU_TYPE = "菜品SKU";
String SKU_CREATE_SUB_TYPE = "创建SKU";
String SKU_UPDATE_SUB_TYPE = "更新SKU";
String SKU_DELETE_SUB_TYPE = "删除SKU";

// ======================= ADDON 加料 =======================
String ADDON_TYPE = "加料";
String ADDON_CREATE_SUB_TYPE = "创建加料";
String ADDON_UPDATE_SUB_TYPE = "更新加料";
String ADDON_DELETE_SUB_TYPE = "删除加料";

// ======================= BRAND 品牌 =======================
String BRAND_TYPE = "品牌";
// ... (以此类推)

// ======================= KDS =======================
String KDS_TYPE = "KDS";
String KDS_START_SUB_TYPE = "开始制作";
String KDS_FINISH_SUB_TYPE = "完成出餐";
```

---

## 执行顺序

```
第 1 批（Day 2-3）：8 个高风险端点
  1. StoreDish: batchSoldOut / batchRestore / batchUpdateStatus
  2. StoreDish: overridePrice
  3. KDS: startItem / finishItem（需先提取 Service）
  4. Order: cancelOrder（补充 extra）
  5. PayNotify: onPaySuccess（补充 extra）

  预计改动文件：3-4 个 Service 文件 + 1 个 LogRecordConstants

第 2 批（Day 4-5）：8 个中风险端点
  6. DishSku: create/update/delete
  7. DishAddon: create/update/delete
  8. DishSpu: updateDishSpu（验证 diff）
  9. Combo: updateCombo（验证 diff）

  预计改动文件：3 个 Service 文件 + LogRecordConstants 扩展

第 3 批（Day 6）：7 类低风险端点
  10. Brand: create/update/delete
  11. Store: create/update/delete
  12. Category: create/update/delete
  13. Table: create/batchCreate/update/delete
  14. KitchenStation: create/update/delete
  15. Printer: create/update/delete
  16. PrinterTemplate: saveTemplate
  17. DishImport: importDishes

  预计改动文件：7+ 个 Service 文件 + LogRecordConstants 扩展
```

---

## 验收标准（每批）

```
☐ 所有目标方法 100% 有 @LogRecord
☐ 高风险端点有前后值记录（diff 或 extra）
☐ 编译通过（mvn compile -pl yudao-module-restaurant）
☐ 管理后台操作日志页面可查
☐ 单元测试覆盖（至少高风险 8 个）
☐ Code Review 无问题
```
