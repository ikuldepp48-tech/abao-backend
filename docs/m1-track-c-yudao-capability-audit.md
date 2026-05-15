# M1 Track C — yudao 已有库存能力盘点

> 扫描时间：2026-05-09
> 扫描范围：yudao-module-erp / yudao-module-mes / yudao-module-mall
> 目的：为库存系统 PRD V0.5 提供"复用 vs 新建"的决策依据

---

## 1. 模块结构总览

| 模块 | 存在 | 库存相关目录 |
|------|------|------------|
| yudao-module-erp | YES | stock/, purchase/, sale/ |
| yudao-module-mes | YES | wm/ (warehouse management，23 个子模块) |
| yudao-module-mall | YES | product/sku/ProductSkuDO.stock (简单 int 字段) |

---

## 2. 详细能力盘点

### 2.1 ERP 模块（轻量进销存）

| 已有表 | 能力描述 | 我们能直接用吗？ | 需要扩展什么？ |
|--------|---------|----------------|---------------|
| erp_stock | 产品库存主表：productId + warehouseId + count | 部分可以 | 无批次/SN 维度，无库位维度，无冻结/质检状态 |
| erp_warehouse | 仓库定义：name, address, principal, status | 可以 | 单层仓库，缺少库区/库位分层 |
| erp_stock_record | 库存流水明细：出入库产品/仓库/数量/业务类型/关联单据 | 可以 | 缺批次 SN 和库位维度记录 |
| erp_stock_in / _item | 其它入库单：supplierId, inTime, totalCount/Price, 审核状态 | 可以 | 只有"其它入库"概念，无生产入库、委外入库 |
| erp_stock_out / _item | 其它出库单：customerId, outTime, totalCount/Price, 审核 | 可以 | 无细分业务出库类型 |
| erp_stock_move / _item | 库存调拨单：from/to warehouseId，审核状态 | 可以 | 仅仓库间调拨，不支持库区/库位间 |
| erp_stock_check / _item | 库存盘点单：stockCount(账面), actualCount(实际), count(盈亏) | 可以 | 缺盘点方案/任务/盲盘 |
| erp_product | ERP 产品主数据：name, barCode, category, unit, price, expiryDay, weight | 可以 | 物料属性不如 MES 丰富 |

**ERP 库存服务特点**：
- `ErpStockService.getStock(productId, warehouseId)` — 不支持 batch/SN 维度
- `ErpStockServiceImpl.updateStockCountIncrement` — CAS 并发更新，支持负库存开关
- 所有单据都有审核状态（ErpAuditStatus）
- 库存业务类型枚举：OTHER_IN/OUT, MOVE_IN/OUT, CHECK, SALE, PURCHASE

### 2.2 MES 模块（完整 WMS）

| 已有表 | 能力描述 | 我们能直接用吗？ | 需要扩展什么？ |
|--------|---------|----------------|---------------|
| mes_wm_material_stock | 库存台账（核心！）：itemId + batchId + warehouseId + locationId + areaId + vendorId + quantity + frozen | **非常完善** | 可直接替代 erp_stock 用于精细化管理 |
| mes_wm_warehouse | MES 仓库：code, name, address, area, chargeUserId, frozen, 含 WIP 线边库常量 | **非常完善** | 仓库+虚拟线边库设计，可直接使用 |
| mes_wm_warehouse_location | MES 库区：code, name, warehouseId, area, frozen | **非常完善** | 仓库→库区→库位三级结构 |
| mes_wm_warehouse_area | MES 库位：code, name, locationId, maxLoad, XYZ 坐标, allowItem/BatchMixing, status | **非常完善** | 支持 XYZ 坐标、混放规则 |
| mes_wm_batch | MES 批次管理：code, itemId, produceDate, expireDate, receiptDate, vendorId, workOrderId, qualityStatus | **非常完善** | 批次全生命周期管理 |
| mes_wm_transaction | MES 库存事务流水：type, bizType/bizId/bizCode, itemId, quantity, batchId, warehouseId/locationId/areaId, transactionTime | **非常完善** | 完善的审计流水，含关联事务 ID 用于调拨配对 |
| mes_wm_stock_taking_plan | 盘点方案：code, name, type, blindFlag, frozen, status；参数支持按仓库/库区/库位/物料/批次 | **非常完善** | 可配置盘点方案 |
| mes_wm_stock_taking_task + line + result | 盘点任务：盲盘、冻结盘点、盘盈盘亏结果 | **非常完善** | 完整盘点执行闭环 |
| mes_wm_transfer | MES 调拨单：code, type, deliveryFlag, recipient, carrier, shippingNumber | **可以直接用** | 比 ERP 调拨更完整 |
| mes_wm_product_produce | MES 生产入库单：workOrderId, feedbackId, taskId, workstationId, processId | **全新能力** | ERP 无此能力 |
| mes_wm_product_receipt | MES 产品收货单（成品入库）：workOrderId, itemId, receiptDate | **全新能力** | 关联生产工单 |
| mes_wm_product_issue | MES 产品出库单（成品出库） | **全新能力** | 销售发货场景 |
| mes_wm_item_receipt | MES 物料收货单（采购入库） | **全新能力** | 替代 ERP 采购入库 |
| mes_wm_item_consume | MES 物料消耗单（生产领料） | **全新能力** | MES 特有 |
| mes_wm_arrival_notice | MES 到货通知单 | **全新能力** | 采购到货通知流程 |
| mes_wm_misc_issue / misc_receipt | MES 杂发/杂收单 | **全新能力** | 替代 ERP "其它入库/出库" |
| mes_wm_return_vendor | MES 退供应商单 | **全新能力** | 采购退货流程 |
| mes_wm_return_issue | MES 退料单 | **全新能力** | 生产退料 |
| mes_wm_outsource_receipt / issue | MES 委外收发单 | **全新能力** | 委外加工 |
| mes_wm_barcode / barcode_config | MES 条码管理 | **全新能力** | 条码生成打印 |
| mes_wm_sn | MES 序列号管理 | **全新能力** | 单品追踪 |
| mes_wm_package / package_line | MES 装箱管理 | **全新能力** | 包装管理 |

**MES 库存服务特点**：
- `getOrCreateMaterialStock(key 组合唯一)` — 物料+批次+仓库+库区+库位+供应商
- `updateMaterialStockQuantity(id, quantity, checkFlag)` — 带库存校验
- `checkAreaMixingRule` — 库位混放规则校验
- `updateMaterialStockFrozen` — 库存冻结/解冻
- 库存事务流水完整审计追溯

### 2.3 MALL 商城模块

| 已有表 | 能力描述 | 我们能直接用吗？ |
|--------|---------|----------------|
| product_sku.stock (int) | 商城 SKU 库存字段 | 仅用于电商前台下单扣减 |
| ProductSkuApi.updateSkuStock | RPC 更新 SKU 库存 | 电商订单扣库存用，和 ERP/MES 独立 |

---

## 3. 关键发现

### A. ERP vs MES 库存差异

| 维度 | ERP | MES |
|------|-----|-----|
| 仓库模型 | 单层 | 仓库→库区→库位 三级 + XYZ |
| 批次管理 | 无 | 完整生命周期 |
| 序列号 | 无 | 有 |
| 盘点 | 简单单据 | 方案→任务→结果 闭环 |
| 生产入库 | 无 | 有 |
| 条码 | 无 | 有 |
| 委外 | 无 | 有 |

**结论：MES 的 wm 子模块是一个完整的 WMS。对于"进销存+生产"场景，应优先基于 MES wm 构建库存能力，而非基于 ERP。**

### B. 两模块完全独立

ERP 和 MES 目前完全独立，没有互相引用。ErpProductDO 和 MesMdItemDO 是两套产品数据模型，需要做映射或统一。

### C. MALL 商城库存独立

商城库存（product_sku.stock）和 ERP/MES 完全独立，两套体系。继续保留，只用于线上商城前台下单。

---

## 4. 建议

| 场景 | 使用方式 | 说明 |
|------|---------|------|
| 仓库-库区-库位 | MES wm_warehouse 系列 | ERP 只有单层 |
| 批次管理 | MES wm_batch | ERP 完全没有 |
| 库存台账 | MES wm_material_stock | 维度丰富，支持冻结 |
| 库存流水 | MES wm_transaction | 审计追溯能力强 |
| 盘点 | MES wm_stock_taking 系列 | 方案/任务/结果闭环 |
| 采购入库 | MES wm_item_receipt + wm_arrival_notice | 更完善 |
| 生产入库 | MES wm_product_produce / wm_product_receipt | ERP 无此能力 |
| 简单出入库/调拨 | 可用 MES 或 ERP | 推荐 MES 对应模块 |
| 商城库存 | product_sku.stock | 继续保留，独立体系 |
