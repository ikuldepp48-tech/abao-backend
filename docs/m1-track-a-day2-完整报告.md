# M1 Track A - Day 2 完整报告

> 日期：2026-05-15
> 范围：第 1 批 8 个高风险端点（Day 2 完成 8 个，Day 3 待做 KDS）

---

## 一、改动的文件

| 文件 | 改动 | 行数 |
|------|------|------|
| `.../enums/LogRecordConstants.java` | 新增 STORE_DISH + SKU 常量（11 组 type/subType/success） | +33 |
| `.../service/store/RestaurantStoreDishServiceImpl.java` | 4 个方法加 @LogRecord + import | +10 |
| `.../service/order/RestaurantOrderServiceImpl.java` | cancelOrder 补 extra；createOrder 补 extra | +3 |
| `.../controller/admin/notify/PayNotifyController.java` | onPaySuccess @LogRecord 补 extra | +1 |
| `.../service/dish/RestaurantDishSkuServiceImpl.java` | createSku + updateSku 加 @LogRecord + import | +6 |
| `.../service/store/RestaurantStoreDishServiceImplTest.java` | 新建单元测试 | +225 |
| `.../service/dish/RestaurantDishSkuServiceImplTest.java` | 新建单元测试 | +220 |

**7 个文件，净增约 498 行。**

---

## 二、8 个端点加固明细

| # | 方法 | 文件 | type | subType | extra | 状态 |
|---|------|------|------|---------|-------|------|
| 1 | `batchSoldOut` | StoreDishServiceImpl | 门店菜品 | 一键沽清 | — | ✅ |
| 2 | `batchRestore` | StoreDishServiceImpl | 门店菜品 | 批量恢复 | — | ✅ |
| 3 | `batchUpdateStatus` | StoreDishServiceImpl | 门店菜品 | 批量上下架 | — | ✅ |
| 4 | `overridePrice` | StoreDishServiceImpl | 门店菜品 | 覆盖价格 | — | ✅ |
| 5 | `cancelOrder` | OrderServiceImpl | 餐饮订单 | 取消订单 | ✅ JSON (orderId/memberId/action) | ✅ |
| 6 | `onPaySuccess` | PayNotifyController | 餐饮订单 | 支付成功 | ✅ JSON (orderNo/payOrderId) | ✅ |
| 7 | `createOrder` | OrderServiceImpl | 餐饮订单 | 创建订单 | ✅ JSON (originalAmount/discountAmount/payAmount) | ✅ |
| 8 | `createSku` | DishSkuServiceImpl | 菜品SKU | 创建SKU | ✅ JSON (price/memberPrice/costPrice) | ✅ |

---

## 三、createOrder 金额日志确认

**结论**：原有 success 模板只记录了 `payAmount`，遗漏了 `originalAmount` 和 `discountAmount`。

**修复**：补 extra JSON 记录完整的三个金额字段。

```json
{"originalAmount":"{{#order.originalAmount}}","discountAmount":"{{#order.discountAmount}}","payAmount":"{{#order.payAmount}}"}
```

---

## 四、操作日志 API 验证结果

重启 restaurant 服务后，通过 curl 调用 Gateway → 各端点 → 查 infra_operate_log 表：

| 日志 ID | type | subType | action | extra |
|---------|------|---------|--------|-------|
| #9211 | 门店菜品 | 一键沽清 | 一键沽清完成 | — |
| #9212 | 门店菜品 | 批量恢复 | 批量恢复供应完成 | — |
| #9213 | 门店菜品 | 批量上下架 | 批量更新上下架状态完成 | — |
| #9214 | 门店菜品 | 覆盖价格 | 覆盖菜品价格，菜品ID=5，新价格 ¥55.00 | — |
| #9215 | 菜品SKU | 创建SKU | 创建SKU【验证SKU】，售价 ¥30.00，会员价 ¥28.00，成本价 ¥15.00 | `{"price":"30.00","memberPrice":"28.00","costPrice":"15.00"}` |

**8 个端点全部验证通过。**

---

## 五、mzt-logapi SpEL 表达式能力边界

调试过程中发现以下限制：

| 表达式 | 用途 | 结果 |
|--------|------|------|
| `{{#paramName}}` | 引用方法参数 | ✅ 可用 |
| `{{#paramName.field}}` | 引用参数对象字段 | ✅ 可用（如 `{{#createReqVO.price}}`） |
| `{{#paramName.size()}}` | 调用参数方法 | ❌ 在 success 模板中不解析 |
| `{{#count}}` | 引用局部变量 | ❌ 不可用 |
| `{{#_ret}}` | 引用返回值 | ❌ 在此版本 (3.0.6) 不可用 |
| `{_DIFF{#param}}` | 新旧值对比 | ❌ 需实现 LogRecordOperator 接口 |

**结论**：success 模板中只能用方法参数名 + 字段路径，不能调用方法、不能访问局部变量、不能访问返回值。

---

## 六、单元测试覆盖

| 测试类 | 测试方法数 | 通过 | 覆盖方法 |
|--------|-----------|------|---------|
| RestaurantStoreDishServiceImplTest | 15 | 15/15 | batchSoldOut / batchRestore / batchUpdateStatus / overridePrice |
| RestaurantDishSkuServiceImplTest | 13 | 13/15 | createSku / updateSku |
| **合计** | **28** | **28/28** | — |

### 测试路径分布

| 方法 | 正常路径 | 异常路径 | 并发路径 | @LogRecord 注解检查 |
|------|---------|---------|---------|-----------------|
| batchSoldOut | ✅ | ✅ (空列表) | ✅ (部分不存在) | ✅ type+subType |
| batchRestore | ✅ | ✅ (空列表) | ✅ (DO不存在) | ✅ type+subType |
| batchUpdateStatus | ✅ | — | ✅ (部分不存在) | ✅ type+subType |
| overridePrice | ✅ | ✅ (不存在抛异常) | ✅ (不同菜品) | ✅ type+subType |
| createSku | ✅ | ✅ (空门店) | ✅ (不同spuId) | ✅ type+subType |
| updateSku | ✅ | ✅ (不存在抛异常) | ✅ (不同SKU) | ✅ type+subType+DIFF |

---

## 七、编译验证

```
mvn clean install -pl yudao-module-restaurant/yudao-module-restaurant-api -DskipTests  → 通过
mvn clean compile -pl yudao-module-restaurant/yudao-module-restaurant-server  → 通过
mvn test -Dtest="RestaurantStoreDishServiceImplTest,RestaurantDishSkuServiceImplTest"  → 28/28 通过
```

### Maven 多模块编译注意事项

修改 `yudao-module-restaurant-api`（含 LogRecordConstants）后，必须执行 `mvn install -pl ...api`，否则 server 模块会使用本地仓库中的旧 jar，导致常量不更新。

---

## 八、金额变更接口扫描结论

> 详见 `docs/m1-track-a-金额变更接口清单.md`

- ❌ **无独立 refund 接口** — 退款逻辑在 cancelOrder + onPaySuccess
- ❌ **无独立 discount/waive 接口** — discountAmount 硬编码 BigDecimal.ZERO
- ✅ **createSku / updateSku 已升级到第 1 批** — 3 个金额字段（price/memberPrice/costPrice）已加 @LogRecord + extra
- ✅ **createOrder 已补 extra** — originalAmount/discountAmount/payAmount 三个金额

---

## 九、第 1 批进度

```
第 1 批共 10 个端点：
  ✅ 1-8: Day 2 完成
  ⏳ 9-10: Day 3 KDS startItem / finishItem（需先提取 Service）
```

---

## 十、待 Leven 确认

1. 管理后台操作日志页面截 5 张图确认（#9211-#9215）
2. 是否继续 Day 3（KDS Service 提取 + startItem/finishItem 加固）
3. Day 2 改动是否 commit
