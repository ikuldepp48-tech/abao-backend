package cn.iocoder.yudao.module.restaurant.enums;

/**
 * 餐饮模块 操作日志枚举
 * 统一管理，减少 Service 里的字符串硬编码
 */
public interface LogRecordConstants {

    // ======================= ORDER 订单 =======================

    String ORDER_TYPE = "餐饮订单";
    String ORDER_CREATE_SUB_TYPE = "创建订单";
    String ORDER_CREATE_SUCCESS = "创建了订单【{{#order.orderNo}}】，金额 ¥{{#order.payAmount}}";
    String ORDER_CANCEL_SUB_TYPE = "取消订单";
    String ORDER_CANCEL_SUCCESS = "取消了订单【{{#orderNo}}】";
    String ORDER_PAY_SUCCESS_SUB_TYPE = "支付成功";
    String ORDER_PAY_SUCCESS = "订单【{{#orderNo}}】支付成功，payOrderId={{#payOrderId}}";

    // ======================= COMBO 套餐 =======================

    String COMBO_TYPE = "餐饮套餐";
    String COMBO_CREATE_SUB_TYPE = "创建套餐";
    String COMBO_CREATE_SUCCESS = "创建了套餐【{{#createReqVO.name}}】";
    String COMBO_UPDATE_SUB_TYPE = "更新套餐";
    String COMBO_UPDATE_SUCCESS = "更新了套餐: {_DIFF{#updateReqVO}}";
    String COMBO_DELETE_SUB_TYPE = "删除套餐";
    String COMBO_DELETE_SUCCESS = "删除了套餐【ID:{{#id}}】";

    // ======================= DISH 菜品 =======================

    String DISH_TYPE = "餐饮菜品";
    String DISH_CREATE_SUB_TYPE = "创建菜品";
    String DISH_CREATE_SUCCESS = "创建了菜品【{{#createReqVO.name}}】";
    String DISH_UPDATE_SUB_TYPE = "更新菜品";
    String DISH_UPDATE_SUCCESS = "更新了菜品: {_DIFF{#updateReqVO}}";
    String DISH_DELETE_SUB_TYPE = "删除菜品";
    String DISH_DELETE_SUCCESS = "删除了菜品【ID:{{#id}}】";

    // ======================= SKU 菜品SKU =======================

    String SKU_TYPE = "菜品SKU";
    String SKU_CREATE_SUB_TYPE = "创建SKU";
    String SKU_CREATE_SUCCESS = "创建SKU【{{#createReqVO.name}}】，售价 ¥{{#createReqVO.price}}，会员价 ¥{{#createReqVO.memberPrice}}，成本价 ¥{{#createReqVO.costPrice}}";
    String SKU_UPDATE_SUB_TYPE = "更新SKU";
    String SKU_UPDATE_SUCCESS = "更新SKU【{{#updateReqVO.name}}】: {_DIFF{#updateReqVO}}";
    String SKU_DELETE_SUB_TYPE = "删除SKU";
    String SKU_DELETE_SUCCESS = "删除SKU【ID:{{#id}}】";

    // ======================= ADDON 加料 =======================

    String ADDON_TYPE = "加料";
    String ADDON_CREATE_SUB_TYPE = "创建加料";
    String ADDON_CREATE_SUCCESS = "创建加料【{{#createReqVO.name}}】，加价 ¥{{#createReqVO.extraPrice}}";
    String ADDON_UPDATE_SUB_TYPE = "更新加料";
    String ADDON_UPDATE_SUCCESS = "更新加料【{{#updateReqVO.name}}】";
    String ADDON_DELETE_SUB_TYPE = "删除加料";
    String ADDON_DELETE_SUCCESS = "删除加料【ID:{{#id}}】";

    // ======================= STORE_DISH 门店菜品 =======================

    String STORE_DISH_TYPE = "门店菜品";
    String STORE_DISH_SOLD_OUT_SUB_TYPE = "一键沽清";
    String STORE_DISH_SOLD_OUT_SUCCESS = "一键沽清完成";
    String STORE_DISH_RESTORE_SUB_TYPE = "批量恢复";
    String STORE_DISH_RESTORE_SUCCESS = "批量恢复供应完成";
    String STORE_DISH_STATUS_SUB_TYPE = "批量上下架";
    String STORE_DISH_STATUS_SUCCESS = "批量更新上下架状态完成";
    String STORE_DISH_PRICE_OVERRIDE_SUB_TYPE = "覆盖价格";
    String STORE_DISH_PRICE_OVERRIDE_SUCCESS = "覆盖菜品价格，菜品ID={{#id}}，新价格 ¥{{#price}}";

    // ======================= KDS 厨房显示系统 =======================

    String KDS_TYPE = "KDS";
    String KDS_START_SUB_TYPE = "开始制作";
    String KDS_START_SUCCESS = "订单项【{{#itemId}}】开始制作";
    String KDS_FINISH_SUB_TYPE = "完成出餐";
    String KDS_FINISH_SUCCESS = "订单项【{{#itemId}}】完成出餐";

    // ======================= BRAND 品牌 =======================

    String BRAND_TYPE = "品牌";
    String BRAND_CREATE_SUB_TYPE = "创建品牌";
    String BRAND_CREATE_SUCCESS = "创建品牌【{{#createReqVO.name}}】";
    String BRAND_UPDATE_SUB_TYPE = "更新品牌";
    String BRAND_UPDATE_SUCCESS = "更新品牌【{{#updateReqVO.name}}】";
    String BRAND_DELETE_SUB_TYPE = "删除品牌";
    String BRAND_DELETE_SUCCESS = "删除品牌【ID:{{#id}}】";

    // ======================= STORE 门店 =======================

    String STORE_TYPE = "门店";
    String STORE_CREATE_SUB_TYPE = "创建门店";
    String STORE_CREATE_SUCCESS = "创建门店【{{#createReqVO.name}}】";
    String STORE_UPDATE_SUB_TYPE = "更新门店";
    String STORE_UPDATE_SUCCESS = "更新门店【{{#updateReqVO.name}}】";
    String STORE_DELETE_SUB_TYPE = "删除门店";
    String STORE_DELETE_SUCCESS = "删除门店【ID:{{#id}}】";

    // ======================= CATEGORY 分类 =======================

    String CATEGORY_TYPE = "分类";
    String CATEGORY_CREATE_SUB_TYPE = "创建分类";
    String CATEGORY_CREATE_SUCCESS = "创建分类【{{#createReqVO.name}}】";
    String CATEGORY_UPDATE_SUB_TYPE = "更新分类";
    String CATEGORY_UPDATE_SUCCESS = "更新分类【{{#updateReqVO.name}}】";
    String CATEGORY_DELETE_SUB_TYPE = "删除分类";
    String CATEGORY_DELETE_SUCCESS = "删除分类【ID:{{#id}}】";

    // ======================= TABLE 桌台 =======================

    String TABLE_TYPE = "桌台";
    String TABLE_CREATE_SUB_TYPE = "创建桌台";
    String TABLE_CREATE_SUCCESS = "创建桌台【{{#createReqVO.tableNo}}】";
    String TABLE_BATCH_CREATE_SUB_TYPE = "批量创建桌台";
    String TABLE_BATCH_CREATE_SUCCESS = "批量创建桌台【{{#reqVO.prefix}}{{#reqVO.startNo}}-{{#reqVO.endNo}}】";
    String TABLE_UPDATE_SUB_TYPE = "更新桌台";
    String TABLE_UPDATE_SUCCESS = "更新桌台【{{#updateReqVO.tableNo}}】";
    String TABLE_DELETE_SUB_TYPE = "删除桌台";
    String TABLE_DELETE_SUCCESS = "删除桌台【ID:{{#id}}】";

    // ======================= KITCHEN_STATION 厨房档口 =======================

    String KITCHEN_STATION_TYPE = "厨房档口";
    String KITCHEN_STATION_CREATE_SUB_TYPE = "创建档口";
    String KITCHEN_STATION_CREATE_SUCCESS = "创建档口【{{#reqVO.name}}】";
    String KITCHEN_STATION_UPDATE_SUB_TYPE = "更新档口";
    String KITCHEN_STATION_UPDATE_SUCCESS = "更新档口【{{#reqVO.name}}】";
    String KITCHEN_STATION_DELETE_SUB_TYPE = "删除档口";
    String KITCHEN_STATION_DELETE_SUCCESS = "删除档口【ID:{{#id}}】";

    // ======================= PRINTER 打印机 =======================

    String PRINTER_TYPE = "打印机";
    String PRINTER_CREATE_SUB_TYPE = "创建打印机";
    String PRINTER_CREATE_SUCCESS = "创建打印机【{{#reqVO.name}}】";
    String PRINTER_UPDATE_SUB_TYPE = "更新打印机";
    String PRINTER_UPDATE_SUCCESS = "更新打印机【{{#reqVO.name}}】";
    String PRINTER_DELETE_SUB_TYPE = "删除打印机";
    String PRINTER_DELETE_SUCCESS = "删除打印机【ID:{{#id}}】";

    // ======================= PRINTER_TEMPLATE 打印模板 =======================

    String PRINTER_TEMPLATE_TYPE = "打印模板";
    String PRINTER_TEMPLATE_SAVE_SUB_TYPE = "保存模板";
    String PRINTER_TEMPLATE_SAVE_SUCCESS = "保存打印模板";

    // ======================= DISH_IMPORT 菜品导入 =======================

    String DISH_IMPORT_TYPE = "菜品导入";
    String DISH_IMPORT_SUB_TYPE = "Excel导入";
    String DISH_IMPORT_SUCCESS = "Excel导入完成，文件名：{{#file.originalFilename}}";

}
