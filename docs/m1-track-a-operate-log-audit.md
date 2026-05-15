# M1 Track A - @OperateLog 审计清单

> **扫描范围**: `yudao-module-restaurant/yudao-module-restaurant-server/src/main/java/`
>
> **扫描时间**: 2026-05-09
>
> **扫描方式**: 全量扫描所有 Controller 中的 POST / PUT / DELETE 端点
>
> **关键结论**: **整个餐饮模块没有任何一个 Controller 方法使用了 `@OperateLog` 注解。** 所有写方法均仅有 `@ApiAccessLog` 注解，二者的语义和行为完全不同。

---

## 一、总览统计

| 指标 | 数值 |
|------|------|
| 写端点总数 (POST/PUT/DELETE) | **32** |
| 已标注 @OperateLog | **0** (0%) |
| 未标注 @OperateLog | **32** (100%) |
| 含 @ApiAccessLog (仅API访问日志) | 32 (100%) |
| 含 @LogRecord (业务日志，在Service层) | 部分 (ORDER/DISH/COMBO) |
| 需要 record before/after 变更值 | **8** (涉及金额/状态变更) |

### @OperateLog vs @ApiAccessLog 说明

| 注解 | 用途 | 是否记录操作人/操作内容/变更 |
|------|------|------|
| `@ApiAccessLog` | 记录 HTTP 请求的访问日志（类似 Nginx access log） | 仅记录请求路径、IP、耗时等 |
| `@OperateLog` | 记录业务操作日志（谁、在什么时间、做了什么、改了什么） | 记录操作人、操作内容、before/after 值 |
| `@LogRecord` (mzt) | 业务日志注解，在 Service 层自动记录 | 可记录自定义内容，但不在 Controller 层 |

---

## 二、详细清单（按 Controller）

### 2.1 RestaurantBrandController — `/restaurant/brand`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 1 | `createBrand` | POST | /create | 创建餐饮品牌 | CREATE | 否 | 缺失 | 无 |
| 2 | `updateBrand` | PUT | /update | 更新餐饮品牌信息 | UPDATE | 否 | 缺失 | 无 |
| 3 | `deleteBrand` | DELETE | /delete | 删除餐饮品牌 | DELETE | 否 | 缺失 | 无 |

### 2.2 RestaurantStoreController — `/restaurant/store`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 4 | `createStore` | POST | /create | 创建门店 | CREATE | 否 | 缺失 | 无 |
| 5 | `updateStore` | PUT | /update | 更新门店信息 | UPDATE | 否 | 缺失 | 无 |
| 6 | `deleteStore` | DELETE | /delete | 删除门店 | DELETE | 否 | 缺失 | 无 |

### 2.3 RestaurantCategoryController — `/restaurant/category`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 7 | `createCategory` | POST | /create | 创建菜品分类 | CREATE | 否 | 缺失 | 无 |
| 8 | `updateCategory` | PUT | /update | 更新菜品分类 | UPDATE | 否 | 缺失 | 无 |
| 9 | `deleteCategory` | DELETE | /delete | 删除菜品分类 | DELETE | 否 | 缺失 | 无 |

### 2.4 RestaurantDishSpuController — `/restaurant/dish-spu`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 10 | `createDishSpu` | POST | /create | 创建菜品（SPU） | CREATE | 否 | 缺失 | Service层有 |
| 11 | `updateDishSpu` | PUT | /update | 更新菜品信息（含价格、分类等关键字段） | UPDATE | **是** (价格变更) | 缺失 | Service层有 |
| 12 | `deleteDishSpu` | DELETE | /delete | 删除菜品 | DELETE | 否 | 缺失 | Service层有 |

### 2.5 RestaurantDishSkuController — `/restaurant/dish-sku`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 13 | `createSku` | POST | /create | 创建菜品SKU（含价格） | CREATE | 否 | 缺失 | 无 |
| 14 | `updateSku` | PUT | /update | 更新菜品SKU（含价格、规格等） | UPDATE | **是** (价格变更) | 缺失 | 无 |
| 15 | `deleteSku` | DELETE | /delete | 删除菜品SKU | DELETE | 否 | 缺失 | 无 |

### 2.6 RestaurantDishAddonController — `/restaurant/dish-addon`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 16 | `createAddon` | POST | /create | 创建加料（含加料价格） | CREATE | 否 | 缺失 | 无 |
| 17 | `updateAddon` | PUT | /update | 更新加料（含价格变更） | UPDATE | **是** (价格变更) | 缺失 | 无 |
| 18 | `deleteAddon` | DELETE | /delete | 删除加料 | DELETE | 否 | 缺失 | 无 |

### 2.7 RestaurantComboController — `/restaurant/combo`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 19 | `createCombo` | POST | /create | 创建套餐（含价格） | CREATE | 否 | 缺失 | Service层有 |
| 20 | `updateCombo` | PUT | /update | 更新套餐（含价格、明细变更） | UPDATE | **是** (价格/明细变更) | 缺失 | Service层有 |
| 21 | `deleteCombo` | DELETE | /delete | 删除套餐 | DELETE | 否 | 缺失 | Service层有 |

### 2.8 RestaurantStoreDishController — `/restaurant/store-dish`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 22 | `createStoreDish` | POST | /create | 创建门店菜品配置 | CREATE | 否 | 缺失 | 无 |
| 23 | `updateStoreDish` | PUT | /update | 更新门店菜品配置 | UPDATE | 否 | 缺失 | 无 |
| 24 | `deleteStoreDish` | DELETE | /delete | 删除门店菜品配置 | DELETE | 否 | 缺失 | 无 |
| **25** | **`batchSoldOut`** | **PUT** | **/batch-sold-out** | **一键沽清（状态变更为售罄）** | **STATUS_CHANGE** | **是 (状态变更)** | **缺失** | **无** |
| **26** | **`batchRestore`** | **PUT** | **/batch-restore** | **批量恢复供应（状态变更为在售）** | **STATUS_CHANGE** | **是 (状态变更)** | **缺失** | **无** |
| **27** | **`batchUpdateStatus`** | **PUT** | **/batch-update-status** | **批量上下架（状态切换）** | **STATUS_CHANGE** | **是 (状态变更)** | **缺失** | **无** |
| **28** | **`overridePrice`** | **PUT** | **/override-price** | **覆盖门店菜品价格** | **PRICE_CHANGE** | **是 (金额变更)** | **缺失** | **无** |
| **29** | `setDailyLimit` | PUT | /set-daily-limit | 设置每日限量 | UPDATE | 否 | 缺失 | 无 |

### 2.9 RestaurantTableController — `/restaurant/table`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 30 | `createTable` | POST | /create | 创建桌台 | CREATE | 否 | 缺失 | 无 |
| 31 | `batchCreateTable` | POST | /batch-create | 批量创建桌台 | CREATE | 否 | 缺失 | 无 |
| 32 | `updateTable` | PUT | /update | 更新桌台信息 | UPDATE | 否 | 缺失 | 无 |
| 33 | `deleteTable` | DELETE | /delete | 删除桌台 | DELETE | 否 | 缺失 | 无 |

### 2.10 RestaurantKitchenStationController — `/restaurant/kitchen-station`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 34 | `createStation` | POST | /create | 创建厨房档口 | CREATE | 否 | 缺失 | 无 |
| 35 | `updateStation` | PUT | /update | 更新厨房档口 | UPDATE | 否 | 缺失 | 无 |
| 36 | `deleteStation` | DELETE | /delete | 删除厨房档口 | DELETE | 否 | 缺失 | 无 |

### 2.11 RestaurantPrinterController — `/restaurant/printer`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 37 | `createPrinter` | POST | /create | 创建打印机配置 | CREATE | 否 | 缺失 | 无 |
| 38 | `updatePrinter` | PUT | /update | 更新打印机配置 | UPDATE | 否 | 缺失 | 无 |
| 39 | `deletePrinter` | DELETE | /delete | 删除打印机配置 | DELETE | 否 | 缺失 | 无 |

### 2.12 RestaurantPrinterTemplateController — `/restaurant/printer-template`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 40 | `saveTemplate` | PUT | /save | 保存打印模板 | UPDATE | 否 | 缺失 | 无 |

### 2.13 RestaurantDishImportController — `/restaurant/dish-import`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 41 | `importDishes` | POST | /upload | 通过Excel批量导入菜品数据 | IMPORT | **是** (批量导入可能覆盖价格/状态) | 缺失 | 无 |

### 2.14 KdsController — `/restaurant/kds`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| **42** | **`startItem`** | **PUT** | **/start** | **KDS开始制作菜品（订单项状态变更）** | **STATUS_CHANGE** | **是 (订单状态变更)** | **缺失** | **无** |
| **43** | **`finishItem`** | **PUT** | **/finish** | **KDS完成出餐（订单项状态变更）** | **STATUS_CHANGE** | **是 (订单状态变更)** | **缺失** | **无** |

### 2.15 AppOrderController (顾客端) — `/restaurant/order`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 44 | `createOrder` | POST | /create | 顾客创建订单（含金额计算） | CREATE | 否 | 缺失 | Service层有 |
| **45** | **`cancelOrder`** | **POST** | **/cancel** | **顾客取消订单（订单状态变更）** | **STATUS_CHANGE** | **是 (订单状态变更)** | **缺失** | **Service层有** |

### 2.16 PayNotifyController (支付回调) — `/restaurant/notify`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| **46** | **`onPaySuccess`** | **POST** | **/pay-success** | **支付成功回调（订单金额确认+状态变更）** | **PAYMENT** | **是 (金额+状态变更)** | **缺失** | Controller层有 @LogRecord |

### 2.17 AppRestaurantMenuController (菜单缓存) — `/restaurant/menu`

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| 47 | `refreshCache` | DELETE | /refresh-cache | 刷新门店菜单缓存 | OTHER | 否 | 缺失 | 无 |

### 2.18 AdminRestaurantOrderController (管理端订单)

| # | 方法 | HTTP | URL | 业务动作 | 操作类型 | 需要before/after | @OperateLog | @LogRecord |
|---|------|------|-----|---------|---------|----------------|-------------|-----------|
| — | (仅含 GET 查询) | — | — | 订单管理端仅提供查询分页和详情 | — | — | — | — |

> 注：`AdminRestaurantOrderController` 和 `AdminRestaurantDashboardController` 及 `AppRestaurantTableController` 均仅有 GET 端点，不在本清单审计范围内。

---

## 三、工程级风险标注

### 🔴 高风险（8个端点 — 必须立即添加 @OperateLog + 记录变更前后值）

这些端点涉及**金额、状态、订单生命周期**等关键业务变更，一旦出问题需要完整操作追踪：

| # | Controller | 方法 | URL | 风险说明 |
|---|-----------|------|-----|---------|
| 1 | `RestaurantStoreDishController` | `batchSoldOut` | PUT /batch-sold-out | 批量修改菜品上下架状态，影响营业 |
| 2 | `RestaurantStoreDishController` | `batchRestore` | PUT /batch-restore | 同上，批量恢复供应状态 |
| 3 | `RestaurantStoreDishController` | `batchUpdateStatus` | PUT /batch-update-status | 批量上下架，状态批量变更风险高 |
| 4 | `RestaurantStoreDishController` | `overridePrice` | PUT /override-price | **直接覆盖菜品价格**，涉及金额且无before/after则无法回查 |
| 5 | `KdsController` | `startItem` | PUT /start | 订单项开始制作，状态从"待制作"→"制作中"，影响出餐流程 |
| 6 | `KdsController` | `finishItem` | PUT /finish | 订单项完成出餐，状态从"制作中"→"已完成" |
| 7 | `AppOrderController` | `cancelOrder` | POST /cancel | 取消订单，涉及订单状态和可能的退款流程 |
| 8 | `PayNotifyController` | `onPaySuccess` | POST /pay-success | **支付成功回调**，涉及订单金额确认+状态变更+财务对账 |

**加固要求**: 这些端点必须添加 `@OperateLog` 注解并设置 `enableParamRecord = true` 来记录请求参数。相关 Service 方法需手动记录 before/after 变更值（通过 `@LogRecord` 的 `{_DIFF{...}}` 或手动 diff）。

---

### 🟡 中风险（12个端点 — 必须添加 @OperateLog，before/after 按需）

这些端点涉及**核心业务实体的 CRUD**，虽不直接涉及金额/状态变更，但对业务运营至关重要：

| # | Controller | 方法 | HTTP | URL |
|---|-----------|------|------|-----|
| 1 | `RestaurantDishSpuController` | `updateDishSpu` | PUT | /update (菜品信息含价格 — 此实为高风险) |
| 2 | `RestaurantDishSkuController` | `updateSku` | PUT | /update (SKU含价格 — 此实为高风险) |
| 3 | `RestaurantDishAddonController` | `updateAddon` | PUT | /update (加料含价格 — 此实为高风险) |
| 4 | `RestaurantComboController` | `updateCombo` | PUT | /update (套餐含价格 — 此实为高风险) |
| 5 | `RestaurantStoreController` | `createStore` | POST | /create |
| 6 | `RestaurantStoreController` | `updateStore` | PUT | /update |
| 7 | `RestaurantBrandController` | `createBrand` | POST | /create |
| 8 | `RestaurantBrandController` | `updateBrand` | PUT | /update |
| 9 | `RestaurantCategoryController` | `createCategory` | POST | /create |
| 10 | `RestaurantCategoryController` | `updateCategory` | PUT | /update |
| 11 | `RestaurantTableController` | `createTable` / `batchCreateTable` | POST | /create, /batch-create |
| 12 | `RestaurantTableController` | `updateTable` | PUT | /update |

**注意**: #1-#4 虽归为中风险，但均涉及**价格字段的修改**。如果 CRUD ReqVO 中包含 price 字段，应升级为高风险。

---

### 🟢 低风险（12个端点 — 建议添加 @OperateLog）

这些端点涉及**配置管理、辅助数据、模板设置**等，问题影响范围相对可控：

| # | Controller | 方法 | HTTP | URL |
|---|-----------|------|------|-----|
| 1 | `RestaurantDishSpuController` | `createDishSpu` | POST | /create |
| 2 | `RestaurantDishSkuController` | `createSku` | POST | /create |
| 3 | `RestaurantDishAddonController` | `createAddon` | POST | /create |
| 4 | `RestaurantComboController` | `createCombo` | POST | /create |
| 5 | `RestaurantStoreDishController` | `createStoreDish` | POST | /create |
| 6 | `RestaurantStoreDishController` | `updateStoreDish` | PUT | /update |
| 7 | `RestaurantStoreDishController` | `setDailyLimit` | PUT | /set-daily-limit |
| 8 | `RestaurantKitchenStationController` | `createStation` | POST | /create |
| 9 | `RestaurantKitchenStationController` | `updateStation` | PUT | /update |
| 10 | `RestaurantPrinterController` | `createPrinter` / `updatePrinter` | POST+PUT | /create, /update |
| 11 | `RestaurantPrinterTemplateController` | `saveTemplate` | PUT | /save |
| 12 | `RestaurantDishImportController` | `importDishes` | POST | /upload |

**注意**: `importDishes` (Excel导入) 虽为低风险但因批量操作，实际影响面较大，建议至少记录 import 结果摘要。

---

## 四、工程现状总结

### 现有日志覆盖

| 日志类型 | 覆盖实体 | 位置 |
|---------|---------|------|
| `@ApiAccessLog` | **全部** Controller 写方法 | Controller 层 |
| `@LogRecord` (mzt) | **ORDER** - 创建/取消/支付成功 | Service 层 |
| `@LogRecord` (mzt) | **COMBO** - 创建/更新/删除 | Service 层 |
| `@LogRecord` (mzt) | **DISH** - 创建/更新/删除 | Service 层 |
| `@OperateLog` | **无** — 0 使用 | 无 |

### 缺失日志的实体（Controller 无 @LogRecord 且 无 @OperateLog）

以下实体在 Controller 层面**没有任何业务操作日志**（既不通过 `@LogRecord` 也不通过 `@OperateLog`）：

- **品牌** (Brand) — create / update / delete
- **门店** (Store) — create / update / delete
- **分类** (Category) — create / update / delete
- **SKU** (Sku) — create / update / delete
- **加料** (Addon) — create / update / delete
- **门店菜品配置** (StoreDish) — create / update / delete / sold-out / restore / status-change / price-override / daily-limit
- **桌台** (Table) — create / batch-create / update / delete
- **厨房档口** (KitchenStation) — create / update / delete
- **打印机** (Printer) — create / update / delete
- **打印模板** (PrinterTemplate) — save
- **KDS** — start-item / finish-item
- **导入** (DishImport) — upload

---

## 五、加固建议优先级（批次顺序）

### 第1批：🔴 立即修复（高风险，涉及金额/状态）
1. `overridePrice` — 金额直接操作，必须记录 before/after
2. `batchSoldOut` / `batchRestore` / `batchUpdateStatus` — 批量状态变更
3. `onPaySuccess` — 支付回调，财务对账关键点
4. `cancelOrder` — 订单取消，涉及状态流转
5. `startItem` / `finishItem` — 订单烹饪状态流转

### 第2批：🟡 尽快修复（中风险，核心CRUD）
6. `updateDishSpu` / `updateSku` / `updateAddon` / `updateCombo` — 核心实体更新（含价格字段，考虑升级到第1批）
7. `createStore` / `updateStore` / `deleteStore` — 门店管理
8. `createBrand` / `updateBrand` / `deleteBrand` — 品牌管理
9. `createTable` / `batchCreateTable` / `updateTable` / `deleteTable` — 桌台管理

### 第3批：🟢 建议修复（低风险，配置/模板/辅助）
10. 所有 `create*` 端点
11. `updateStoreDish` / `setDailyLimit`
12. `createStation` / `updateStation` / `deleteStation`
13. `createPrinter` / `updatePrinter` / `deletePrinter`
14. `saveTemplate`
15. `importDishes` (Excel导入)

---

## 六、配置方式示例

```java
// 1. 基础用法 — 仅记录操作类型
@OperateLog(operateType = UPDATE)

// 2. 记录请求参数（enableParamRecord = true）
@OperateLog(operateType = UPDATE, enableParamRecord = true)

// 3. 注意：@OperateLog 不能替代 @ApiAccessLog，两者应共存
@ApiAccessLog(operateType = UPDATE)
@OperateLog(operateType = UPDATE)
```

---

## 七、附录：扫描到的所有 Controller 文件清单

```
yudao-module-restaurant/yudao-module-restaurant-server/src/main/java/cn/iocoder/yudao/module/restaurant/controller/
├── admin/
│   ├── brand/RestaurantBrandController.java
│   ├── store/RestaurantStoreController.java
│   ├── store/RestaurantStoreDishController.java
│   ├── category/RestaurantCategoryController.java
│   ├── dish/RestaurantDishSpuController.java
│   ├── dish/RestaurantDishSkuController.java
│   ├── dish/RestaurantDishImportController.java
│   ├── addon/RestaurantDishAddonController.java
│   ├── combo/RestaurantComboController.java
│   ├── table/RestaurantTableController.java
│   ├── kitchen/RestaurantKitchenStationController.java
│   ├── printer/RestaurantPrinterController.java
│   ├── printer/template/RestaurantPrinterTemplateController.java
│   ├── kds/KdsController.java
│   ├── order/AdminRestaurantOrderController.java
│   ├── notify/PayNotifyController.java
│   ├── dashboard/AdminRestaurantDashboardController.java
│   └── verify/RestaurantDataVerifyController.java
└── app/
    ├── order/AppOrderController.java
    ├── table/AppRestaurantTableController.java
    └── menu/AppRestaurantMenuController.java
```
