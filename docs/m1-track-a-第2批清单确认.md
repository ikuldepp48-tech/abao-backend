# M1 Track A - 第 2 批清单确认

> 日期：2026-05-15
> 范围：6 个中风险端点（4 个新加固 + 2 个验证）

---

## 一、清单总览

| # | 方法 | 文件 | 当前状态 | 操作 | type |
|---|------|------|---------|------|------|
| 1 | `deleteSku` | DishSkuServiceImpl | ❌ 无 @LogRecord | **新加固** | 菜品SKU |
| 2 | `createAddon` | DishAddonServiceImpl | ❌ 无 @LogRecord | **新加固** | 加料 |
| 3 | `updateAddon` | DishAddonServiceImpl | ❌ 无 @LogRecord | **新加固** | 加料 |
| 4 | `deleteAddon` | DishAddonServiceImpl | ❌ 无 @LogRecord | **新加固** | 加料 |
| 5 | `updateDishSpu` | DishSpuServiceImpl | ✅ 已有 {_DIFF} | **验证** | 餐饮菜品 |
| 6 | `updateCombo` | ComboServiceImpl | ✅ 已有 {_DIFF} | **验证** | 餐饮套餐 |

---

## 二、新加固 4 个端点

### 2.1 deleteSku（DishSkuServiceImpl）

```
@LogRecord(type = SKU_TYPE, subType = SKU_DELETE_SUB_TYPE, bizNo = "{{#id}}",
        success = SKU_DELETE_SUCCESS)
```

### 2.2 DishAddon CRUD（3 个端点）

**注意**：加料字段是 `extraPrice`（加价），不是 `price`。Addon 没有 `spuId`（通过关联表 `restaurant_dish_spu_addon_rel` 与 SPU 关联）。

**⚠️ bizNo 必须为数字**：yudao 框架 `LogRecordServiceImpl.fillModuleFields()` 对 bizNo 执行 `Long.parseLong()`，非数字会抛 NumberFormatException 导致 action/extra 字段丢失。createAddon 的 bizNo 改用 `brandId`（数字）。

```
① createAddon:
  type = ADDON_TYPE ("加料")
  subType = ADDON_CREATE_SUB_TYPE ("创建加料")
  bizNo = "{{#createReqVO.name}}"
  success = "创建加料【{{#createReqVO.name}}】，加价 ¥{{#createReqVO.extraPrice}}"
  extra = "{\"extraPrice\":\"{{#createReqVO.extraPrice}}\"}"

② updateAddon:
  type = ADDON_TYPE ("加料")
  subType = ADDON_UPDATE_SUB_TYPE ("更新加料")
  bizNo = "{{#updateReqVO.id}}"
  success = "更新加料【{{#updateReqVO.name}}】"
  （价格变更通过 extra 手动捕获）

③ deleteAddon:
  type = ADDON_TYPE ("加料")
  subType = ADDON_DELETE_SUB_TYPE ("删除加料")
  bizNo = "{{#id}}"
  success = "删除加料【ID:{{#id}}】"
```

### 2.3 LogRecordConstants 新增

```java
String ADDON_TYPE = "加料";
String ADDON_CREATE_SUB_TYPE = "创建加料";
String ADDON_CREATE_SUCCESS = "创建加料【{{#createReqVO.name}}】，加价 ¥{{#createReqVO.extraPrice}}";
String ADDON_UPDATE_SUB_TYPE = "更新加料";
String ADDON_UPDATE_SUCCESS = "更新加料【{{#updateReqVO.name}}】";
String ADDON_DELETE_SUB_TYPE = "删除加料";
String ADDON_DELETE_SUCCESS = "删除加料【ID:{{#id}}】";

String SKU_DELETE_SUB_TYPE = "删除SKU";
String SKU_DELETE_SUCCESS = "删除SKU【ID:{{#id}}】";
```

---

## 三、验证 2 个已有端点

### 3.1 updateDishSpu（DishSpuServiceImpl）

**当前配置**：
```java
@LogRecord(type = DISH_TYPE, subType = DISH_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
        success = DISH_UPDATE_SUCCESS)  // "更新了菜品: {_DIFF{#updateReqVO}}"
```

**DishSpuUpdateReqVO 含价格字段**：
- `price` (BigDecimal)
- `minPrice` (BigDecimal)
- `maxPrice` (BigDecimal)
- `skus` (List<DishSkuBaseVO> — 含各自 price)

**已知限制**：mzt-logapi v3.0.6 的 `{_DIFF{#updateReqVO}}` 需自定义 `LogRecordOperator` 实现，当前版本空输出。价格变更不会被 DIFF 自动捕获。

**验证结论**：DIFF 模板已就位，但实际不输出。建议保持现有配置，待后续实现 Operator 后自动生效。额外记录：updateDishSpu 内部 `updateSkus()` 方法处理 SKU 级价格变更，该变更也不在日志中可见。

### 3.2 updateCombo（ComboServiceImpl）

**当前配置**：
```java
@LogRecord(type = COMBO_TYPE, subType = COMBO_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
        success = COMBO_UPDATE_SUCCESS)  // "更新了套餐: {_DIFF{#updateReqVO}}"
```

**ComboUpdateReqVO 含价格字段**：
- `comboPrice` (BigDecimal)
- `originalPrice` (BigDecimal)
- `items` (List<ComboItemBaseVO> — 含各明细)

**已知限制**：同 updateDishSpu，DIFF 在当前版本不输出。

**验证结论**：DIFF 模板已就位。建议保持现有配置，待后续实现 Operator 后自动生效。

---

## 四、不覆盖的端点

根据加固清单 V1，第 2 批原定 8 个端点中：
- #9-10 (createSku/updateSku) 已在第 1 批完成 ✅
- 本次实际新增：deleteSku + DishAddon×3 = 4 个
- DishSpu + Combo 为验证型，不需要新加注解

---

## 五、预估改动量

| 文件 | 改动 | 行数 |
|------|------|------|
| LogRecordConstants.java | 新增 ADDON + SKU_DELETE 常量 | +7 |
| DishSkuServiceImpl.java | deleteSku 加 @LogRecord + import | +3 |
| DishAddonServiceImpl.java | 3 个方法加 @LogRecord + imports | +7 |
| DishAddonServiceImplTest.java | 新建单元测试 | ~250 |
| **合计** | **4 个文件** | **~267 行** |
