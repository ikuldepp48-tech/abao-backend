# M1 Track A - Day 2 加固完成报告

> 日期：2026-05-15
> 范围：第 1 批高风险端点（Day 2 上午 — 6 个非重构端点）

---

## 一、改动的文件

| 文件 | 改动 | 行数 |
|------|------|------|
| `.../enums/LogRecordConstants.java` | 新增 STORE_DISH 常量（5 组 type/subType/success） | +15 |
| `.../service/store/RestaurantStoreDishServiceImpl.java` | 4 个方法加 @LogRecord + import | +10 |
| `.../service/order/RestaurantOrderServiceImpl.java` | cancelOrder @LogRecord 补 extra | +1 |
| `.../controller/admin/notify/PayNotifyController.java` | onPaySuccess @LogRecord 补 extra | +1 |
| `.../service/store/RestaurantStoreDishServiceImplTest.java` | 新建单元测试 | +203 |

**5 个文件，净增约 230 行。**

---

## 二、6 个端点加固明细

| # | 方法 | 文件 | type | subType | 是否记录前后值 |
|---|------|------|------|---------|-------------|
| 1 | `batchSoldOut` | StoreDishServiceImpl | 门店菜品 | 一键沽清 | 🟡 记录沽清数量 |
| 2 | `batchRestore` | StoreDishServiceImpl | 门店菜品 | 批量恢复 | 🟡 记录恢复数量 |
| 3 | `batchUpdateStatus` | StoreDishServiceImpl | 门店菜品 | 批量上下架 | 🟡 记录目标状态+数量 |
| 4 | `overridePrice` | StoreDishServiceImpl | 门店菜品 | 覆盖价格 | 🟡 记录菜品ID+新价格 |
| 5 | `cancelOrder` | OrderServiceImpl | 餐饮订单 | 取消订单 | ✅ extra JSON (orderId/memberId/action) |
| 6 | `onPaySuccess` | PayNotifyController | 餐饮订单 | 支付成功 | ✅ extra JSON (orderNo/payOrderId) |

---

## 三、单元测试覆盖

**文件**: `RestaurantStoreDishServiceImplTest.java`
**框架**: JUnit 5 + Mockito

| 度量 | 数值 |
|------|------|
| 测试方法数 | **15** |
| 通过 | **15 / 15** |
| 失败 | **0** |
| 覆盖方法 | batchSoldOut / batchRestore / batchUpdateStatus / overridePrice |

### 测试覆盖的路径

| 方法 | 正常路径 | 异常路径 | 并发路径 | @LogRecord 注解检查 |
|------|---------|---------|---------|-----------------|
| batchSoldOut | ✅ 批量沽清 3 个 | ✅ 空列表 | ✅ 部分ID不存在 | ✅ type="门店菜品" subType="一键沽清" |
| batchRestore | ✅ 恢复+清零销量 | ✅ 空列表 | ✅ DO不存在跳过 | ✅ type="门店菜品" subType="批量恢复" |
| batchUpdateStatus | ✅ 批量更新状态 | — | ✅ 部分不存在跳过 | ✅ type="门店菜品" subType="批量上下架" |
| overridePrice | ✅ 覆盖价格成功 | ✅ 菜品不存在抛异常 | ✅ 两菜品同时覆盖 | ✅ type="门店菜品" subType="覆盖价格" |

---

## 四、编译验证

```
mvn compile -pl yudao-module-restaurant  → 通过
mvn test -Dtest=RestaurantStoreDishServiceImplTest  → 15/15 通过
```

---

## 五、金额变更接口扫描结论

> 详见 `docs/m1-track-a-金额变更接口清单.md`

- ❌ **无独立 refund 接口** — 退款逻辑在 cancelOrder + onPaySuccess
- ❌ **无独立 discount/waive 接口** — discountAmount 硬编码 BigDecimal.ZERO
- ⚠️ **createSku / updateSku 含 3 个金额字段**（price/memberPrice/costPrice），建议从第 2 批升级到第 1 批

---

## 六、待 Leven 验证（动作 3）

重启 restaurant 服务后，在管理后台操作日志页面验证：

1. 执行一次批量沽清 → 查 "门店菜品 / 一键沽清"
2. 执行一次批量恢复 → 查 "门店菜品 / 批量恢复"
3. 执行一次批量上下架 → 查 "门店菜品 / 批量上下架"
4. 执行一次覆盖价格 → 查 "门店菜品 / 覆盖价格"
5. 顾客取消一单 → 查 "餐饮订单 / 取消订单"（含 extra JSON）
6. 支付回调 → 查 "餐饮订单 / 支付成功"（含 extra JSON）

重启命令：
```bash
# 在 abao-backend 目录下，重启 restaurant 服务
# 具体取决于你的启动方式（IDE / mvn spring-boot:run / java -jar）
```

---

## 七、第 1 批进度

```
第 1 批共 8 个端点：
  ✅ 1-6: Day 2 上午完成（非重构）
  ⏳ 7-8: Day 3 KDS startItem / finishItem（需先提取 Service）
```
