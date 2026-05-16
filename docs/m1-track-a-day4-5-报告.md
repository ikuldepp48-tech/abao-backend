# M1 Track A - Day 4-5 报告

> 日期：2026-05-15
> 范围：第 2 批 6 个中风险端点加固

---

## 一、改动的文件

| 文件 | 改动 | 行数 |
|------|------|------|
| `.../enums/LogRecordConstants.java` | 新增 ADDON + SKU_DELETE 常量（5 组 type/subType/success） | +5 |
| `.../service/dish/RestaurantDishSkuServiceImpl.java` | deleteSku 加 @LogRecord | +2 |
| `.../service/addon/RestaurantDishAddonServiceImpl.java` | 3 个方法加 @LogRecord + imports | +9 |
| `.../service/addon/RestaurantDishAddonServiceImplTest.java` | 新建单元测试 | +261 |
| `.../service/dish/RestaurantDishSkuServiceImplTest.java` | 补充 deleteSku 测试（4 个） | +42 |

**5 个文件，净增约 319 行。**

---

## 二、第 2 批 6 个端点加固明细

| # | 方法 | 文件 | type | subType | extra | 状态 |
|---|------|------|------|---------|-------|------|
| 1 | `deleteSku` | DishSkuServiceImpl | 菜品SKU | 删除SKU | — | ✅ |
| 2 | `createAddon` | DishAddonServiceImpl | 加料 | 创建加料 | JSON (extraPrice) | ✅ |
| 3 | `updateAddon` | DishAddonServiceImpl | 加料 | 更新加料 | JSON (extraPrice) | ✅ |
| 4 | `deleteAddon` | DishAddonServiceImpl | 加料 | 删除加料 | — | ✅ |
| 5 | `updateDishSpu` | DishSpuServiceImpl | 餐饮菜品 | 更新菜品 | {_DIFF} (已有，验证) | ✅ |
| 6 | `updateCombo` | ComboServiceImpl | 餐饮套餐 | 更新套餐 | {_DIFF} (已有，验证) | ✅ |

---

## 三、操作日志验证

### 3.1 MySQL 数据库验证

```sql
SELECT id, type, sub_type, biz_id, action, extra, create_time
FROM system_operate_log WHERE id IN (9218,9219,9220,9221) ORDER BY id;
```

| 日志 ID | type | subType | bizId | action | extra | 时间 |
|---------|------|---------|-------|--------|-------|------|
| #9218 | 加料 | 更新加料 | 13 | 更新加料【测试加料-鸡蛋】 | `{"extraPrice":"3.50"}` | 22:17:56 |
| #9219 | 菜品SKU | 删除SKU | 18 | 删除SKU【ID:18】 | — | 22:17:56 |
| #9220 | 加料 | 删除加料 | 13 | 删除加料【ID:13】 | — | 22:17:56 |
| #9221 | 加料 | 创建加料 | 1 | 创建加料【验证加料-培根】，加价 ¥3.00 | `{"extraPrice":"3.00"}` | 22:21:47 |

### 3.2 API 验证（admin-api）

| 日志 ID | requestUrl | requestMethod | userIp |
|---------|-----------|---------------|--------|
| #9218 | /admin-api/restaurant/dish-addon/update | PUT | 127.0.0.1 |
| #9219 | /admin-api/restaurant/dish-sku/delete | DELETE | 127.0.0.1 |
| #9220 | /admin-api/restaurant/dish-addon/delete | DELETE | 127.0.0.1 |
| #9221 | /admin-api/restaurant/dish-addon/create | POST | 127.0.0.1 |

### 3.3 截图

| 截图 | 文件 |
|------|------|
| 总览（4条一览） | `docs/screenshots/m1-day5-operatelog-overview.png` |
| #9218 updateAddon | `docs/screenshots/m1-day5-log9218-updateAddon.png` |
| #9219 deleteSku | `docs/screenshots/m1-day5-log9219-deleteSku.png` |
| #9220 deleteAddon | `docs/screenshots/m1-day5-log9220-deleteAddon.png` |
| #9221 createAddon | `docs/screenshots/m1-day5-log9221-createAddon.png` |

**4 个新加固端点全部验证通过（MySQL + API + 截图三重验证）。**

---

## 四、验证型端点结论

### updateDishSpu（DishSpuServiceImpl）

- 已有 `@LogRecord` + `{_DIFF{#updateReqVO}}`
- DishSpuUpdateReqVO 含：price、minPrice、maxPrice、skus(含各自 price)
- **已知限制**：mzt-logapi v3.0.6 的 `{_DIFF}` 需自定义 `LogRecordOperator` 实现，当前 DIFF 为空
- **结论**：DIFF 模板已就位，后续实现 Operator 后自动生效。不做额外改动。

### updateCombo（ComboServiceImpl）

- 已有 `@LogRecord` + `{_DIFF{#updateReqVO}}`
- ComboUpdateReqVO 含：comboPrice、originalPrice、items(含明细)
- **已知限制**：同上
- **结论**：同上

---

## 五、新增发现：bizNo 必须为数字

yudao 框架 `LogRecordServiceImpl.fillModuleFields()` (L64) 对 bizNo 执行 `Long.parseLong()`：

```java
reqDTO.setBizId(Long.parseLong(logRecord.getBizNo()));
```

非数字 bizNo 导致 NumberFormatException → action/extra 字段全部丢失。

| bizNo 模板 | 结果 |
|-----------|------|
| `{{#createReqVO.spuId}}` (数字) | ✅ |
| `{{#createReqVO.brandId}}` (数字) | ✅ |
| `{{#createReqVO.id}}` (数字) | ✅ |
| `{{#createReqVO.name}}` (字符串) | ❌ NumberFormatException |

**已修正**：createAddon 的 bizNo 从 `name` 改为 `brandId`。

**遗留风险**：Combo create 和 DishSpu create 的 bizNo 也使用 name（字符串），属于已有代码。建议后续批量修正。

---

## 六、单元测试覆盖

| 测试类 | 测试数 | 通过 | 新增 |
|--------|--------|------|:---:|
| RestaurantDishAddonServiceImplTest | 14 | 14/14 | +14 |
| RestaurantDishSkuServiceImplTest | 17 | 17/17 | +4 |
| 第 1 批保留 | 28 | 28/28 | — |
| **合计** | **59** | **59/59** | **+18** |

### 新增测试路径分布

| 方法 | 正常 | 异常 | 并发 | @LogRecord |
|------|:---:|:---:|:---:|:---:|
| createAddon | ✅ (含不同加价) | — | ✅ | ✅ |
| updateAddon | ✅ + SPU关联 | ✅ | ✅ | ✅ |
| deleteAddon | ✅ + SPU关联 | ✅ | ✅ | ✅ |
| deleteSku | ✅ | ✅ | ✅ | ✅ |

---

## 七、编译验证

```
mvn clean install -pl yudao-module-restaurant/yudao-module-restaurant-api -DskipTests  → 通过
mvn clean compile -pl yudao-module-restaurant/yudao-module-restaurant-server  → 通过
mvn test -Dtest="RestaurantStoreDishServiceImplTest,RestaurantDishSkuServiceImplTest,KdsServiceImplTest,RestaurantDishAddonServiceImplTest"  → 59/59 通过
```

---

## 八、累计进度

```
Track A 加固全量端点：32 个写操作
  第 1 批（高风险）：10/10 ✅
  第 2 批（中风险）： 6/6  ✅
  第 3 批（低风险）： 0/16  ⏳
  累计完成：         16/32  (50%)
```

| 指标 | 第 1 批 | 第 2 批 | 累计 |
|------|:------:|:------:|:----:|
| 加固端点 | 10 | 6 | 16 |
| 单元测试 | 41 | 18 | 59 |
| 操作日志验证 | 7 | 4 | 11 |
| 改动文件 | 9 | 5 | 14 |
| 净增代码 | ~785 | ~319 | ~1104 |

---

## 九、待 Leven 确认

1. 是否 commit Day 4-5 改动
2. 是否继续第 3 批（16 个低风险端点：品牌/门店/分类/桌台/档口/打印机/模板/导入）
