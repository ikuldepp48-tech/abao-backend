# M1 Track A - Day 6 批量加固报告

> 日期：2026-05-15/16 | 批次：第 3 批 | 状态：✅ 完成

---

## 1. 第 3 批加固清单

### 加固范围

| # | ServiceImpl | 端点数 | 加固类型 |
|---|---|---|---|
| 1 | RestaurantBrandServiceImpl | 3 | create/update/delete |
| 2 | RestaurantStoreServiceImpl | 3 | create/update/delete |
| 3 | RestaurantCategoryServiceImpl | 3 | create/update/delete |
| 4 | RestaurantTableServiceImpl | 4 | create/update/delete/batchCreate |
| 5 | RestaurantKitchenStationServiceImpl | 3 | create/update/delete |
| 6 | RestaurantPrinterServiceImpl | 3 | create/update/delete |
| 7 | RestaurantPrinterTemplateServiceImpl | 1 | saveTemplate |
| 8 | RestaurantDishImportServiceImpl | 1 | importDishes |
| **合计** | **8 个 Service** | **21 端点** | |

### LogRecordConstants 新增

8 个 type 组，42 行常量：BRAND、STORE、CATEGORY、TABLE、KITCHEN_STATION、PRINTER、PRINTER_TEMPLATE、DISH_IMPORT

---

## 2. 发现的问题与修复

### 问题 1：SpEL 字段名错误导致 @LogRecord 静默跳过

**现象**：Table createTable、updateTable、batchCreateTable 和 PrinterTemplate saveTemplate 的 @LogRecord 注解代码正确编译、测试通过，但运行时 API 调用成功却不产生操作日志。

**根因**：LogRecordConstants 中的 success 模板引用 VO 不存在的字段名：

| 常量 | 错误 SpEL | 正确 SpEL | 原因 |
|---|---|---|---|
| TABLE_CREATE_SUCCESS | `{{#createReqVO.name}}` | `{{#createReqVO.tableNo}}` | TableBaseVO 无 `name`，有 `tableNo` |
| TABLE_UPDATE_SUCCESS | `{{#updateReqVO.name}}` | `{{#updateReqVO.tableNo}}` | 同上 |
| TABLE_BATCH_CREATE_SUCCESS | `{{#reqVO.tableCount}}` | `{{#reqVO.prefix}}{{#reqVO.startNo}}-{{#reqVO.endNo}}` | BatchCreateReqVO 无 `tableCount` |
| PRINTER_TEMPLATE_SAVE_SUCCESS | `{{#reqVO.name}}` | `保存打印模板`（移除 SpEL） | SaveReqVO 无 `name` |

> **教训**：mzt-logapi 在 SpEL 表达式求值失败时**静默跳过**整个 @LogRecord，不抛异常、不打日志。这极易造成"代码写了但实际没生效"。

### 问题 2：VO-based bizNo SpEL 不稳定

**现象**：Table create 使用 `bizNo="{{#createReqVO.storeId}}"` 也不生效（storeId 字段确实存在）。

**修复**：与 Brand/KitchenStation/DishImport 的 create 方法统一使用 `bizNo="0"`（固定值）。

**原因推测**：mzt-logapi 内部 `Long.parseLong()` 转换 bizNo 时，SpEL 求值结果类型可能不符合预期。

### 问题 3：API 常量修改后 Server 模块未强制重编译

**现象**：修改 LogRecordConstants 后 `mvn install -pl ...api` + `mvn spring-boot:run -pl ...server`，Server 类中的内联常量值未更新。

**根因**：`static final String` 常量被 javac 内联到使用处。修改常量值后，Server 模块必须 `mvn clean compile` 才能重新内联。

**修复流程**：
```
mvn clean install -pl ...api -q
mvn clean compile -pl ...server -am -q
# 重启服务
```

---

## 3. 验证结果

### 3.1 单元测试

| 指标 | 数值 | 状态 |
|---|---|---|
| 测试文件总数 | 12 | ✅ |
| 测试方法总数 | 102 | ✅ |
| 通过 | 102 | ✅ |
| 失败 | 0 | ✅ |
| 错误 | 0 | ✅ |
| 跳过 | 0 | ✅ |

### 3.2 操作日志验证（MySQL + API）

22 条新日志覆盖全部 8 个 Service 的 21 个端点（DishImport 未触发，需文件上传）：

| 日志ID范围 | Service | 创建 | 更新 | 删除 | 批量创建 | 保存 |
|---|---|---|---|---|---|---|
| 9222-9223, 9237 | Brand | ✅ | ✅ | ✅ | — | — |
| 9224-9225, 9236 | Store | ✅ | ✅ | ✅ | — | — |
| 9226-9227, 9235 | Category | ✅ | ✅ | ✅ | — | — |
| 9242-9245 | Table | ✅ | ✅ | ✅ | ✅ | — |
| 9228-9229, 9234 | KitchenStation | ✅ | ✅ | ✅ | — | — |
| 9230-9231, 9233 | Printer | ✅ | ✅ | ✅ | — | — |
| 9246 | PrinterTemplate | — | — | — | — | ✅ |
| （未触发） | DishImport | — | — | — | — | — |

### 3.3 截图清单（10 张）

| 文件 | 内容 |
|---|---|
| m1-batch3-all-logs.png | 全部 22 条操作日志一览表 |
| m1-batch3-mysql-query.png | MySQL 直接查询 system_operate_log |
| m1-batch3-brand.png | Brand 3/3 端点验证 |
| m1-batch3-store.png | Store 3/3 端点验证 |
| m1-batch3-category.png | Category 3/3 端点验证 |
| m1-batch3-table.png | Table 4/4 端点验证 |
| m1-batch3-kitchen.png | KitchenStation 3/3 端点验证 |
| m1-batch3-printer.png | Printer 3/3 端点验证 |
| m1-batch3-template.png | PrinterTemplate 1/1 端点验证 |
| m1-batch3-test-summary.png | 12 测试类 102 测试全部通过 |

---

## 4. bizNo 策略汇总

| bizNo 值 | 场景 | 涉及端点 |
|---|---|---|
| `"0"` | create 操作无 Long 字段 | Brand.create, Category.create, KitchenStation.create, Table.create/update/batch, PrinterTemplate.save, DishImport.import |
| `"{{#updateReqVO.id}}"` | update 操作 | Brand.update, Store.update, Category.update |
| `"{{#createReqVO.brandId}}"` | create 有 Long 字段 | Store.create, Printer.create |
| `"{{#id}}"` | delete 操作（直接传 Long） | 全部 delete |
| `"{{#reqVO.storeId}}"` | create 有 storeId | Table.batchCreate（已废弃改用 "0"） |

---

## 5. M1 Track A 整体数据

| 批次 | 加固端点 | 测试新增 | 累计测试 | 截图 |
|---|---|---|---|---|
| Day 1-3 (第 1 批) | 12 端点（Order/Combo/Dish/Sku/Addon） | 28 | 28 | ≥10 |
| Day 4-5 (第 2 批) | 6 端点（StoreDish/KDS） | 31 | 59 | 5 |
| Day 6 (第 3 批) | 21 端点（8 个 Service） | 43 | **102** | 10 |
| **合计** | **39 端点** | **102** | **102** | **≥25** |

---

## 6. 代码变更清单

### 新增文件（8 个测试类）
- RestaurantBrandServiceImplTest.java（6 方法）
- RestaurantStoreServiceImplTest.java（6 方法）
- RestaurantCategoryServiceImplTest.java（6 方法）
- RestaurantTableServiceImplTest.java（8 方法）
- RestaurantKitchenStationServiceImplTest.java（6 方法）
- RestaurantPrinterServiceImplTest.java（6 方法）
- RestaurantPrinterTemplateServiceImplTest.java（3 方法）
- RestaurantDishImportServiceImplTest.java（2 方法）

### 修改文件
- `LogRecordConstants.java` — 新增 42 行（8 组常量），修复 4 处 SpEL 字段名
- `RestaurantBrandServiceImpl.java` — +3 @LogRecord + imports
- `RestaurantStoreServiceImpl.java` — +3 @LogRecord + imports
- `RestaurantCategoryServiceImpl.java` — +3 @LogRecord + imports
- `RestaurantTableServiceImpl.java` — +4 @LogRecord + imports
- `RestaurantKitchenStationServiceImpl.java` — +3 @LogRecord + imports
- `RestaurantPrinterServiceImpl.java` — +3 @LogRecord + imports
- `RestaurantPrinterTemplateServiceImpl.java` — +1 @LogRecord + import
- `RestaurantDishImportServiceImpl.java` — +1 @LogRecord + import

---

## 7. 遗留事项（更新）

| # | 事项 | 优先级 | 建议时机 |
|---|---|---|---|
| 1 | DishImport.importDishes 需要上传 Excel 端到端验证 | P2 | 下次部署后 |
| 2 | 排查 VO-based bizNo SpEL 不稳定根因 | P3 | 技术债 |
| 3 | `static final String` 常量修改后的增量编译流程文档化 | P2 | 团队 Wiki |
| 4 | LogRecordOperator 上下文补全（当前日志无操作人信息） | P2 | Stage 3 |

---

**⏸ Day 6 末 STOP — 等 Leven 审阅 + 决定下一步**
