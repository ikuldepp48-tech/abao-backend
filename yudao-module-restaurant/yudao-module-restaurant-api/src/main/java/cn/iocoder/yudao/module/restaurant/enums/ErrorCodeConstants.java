package cn.iocoder.yudao.module.restaurant.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * restaurant 模块错误码
 * <p>
 * 错误码范围：1-010-000-000 ~ 1-010-999-999
 */
public interface ErrorCodeConstants {

    // ========== 通用错误 ==========
    ErrorCode BRAND_NOT_EXISTS = new ErrorCode(1_010_000_001, "品牌不存在");
    ErrorCode BRAND_NAME_EXISTS = new ErrorCode(1_010_000_004, "品牌名称已存在");
    ErrorCode BRAND_CODE_EXISTS = new ErrorCode(1_010_000_005, "品牌编码已存在");
    ErrorCode STORE_NOT_EXISTS = new ErrorCode(1_010_000_002, "门店不存在");
    ErrorCode STORE_NAME_EXISTS = new ErrorCode(1_010_000_006, "门店名称已存在");
    ErrorCode STORE_CODE_EXISTS = new ErrorCode(1_010_000_007, "门店编码已存在");
    ErrorCode TABLE_NOT_EXISTS = new ErrorCode(1_010_000_003, "桌台不存在");

    // ========== 菜品相关 1-010-001-xxx ==========
    ErrorCode DISH_SPU_NOT_EXISTS = new ErrorCode(1_010_001_001, "菜品SPU不存在");
    ErrorCode DISH_SKU_NOT_EXISTS = new ErrorCode(1_010_001_002, "菜品SKU不存在");
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_010_001_003, "菜品分类不存在");
    ErrorCode CATEGORY_HAS_CHILDREN = new ErrorCode(1_010_001_004, "分类下存在子分类，无法删除");
    ErrorCode CATEGORY_HAS_DISHES = new ErrorCode(1_010_001_005, "分类下存在菜品，无法删除");
    ErrorCode DISH_ADDON_NOT_EXISTS = new ErrorCode(1_010_001_006, "加料不存在");
    ErrorCode DISH_SPU_MUST_HAVE_SKU = new ErrorCode(1_010_001_009, "菜品至少需要一个SKU");
    ErrorCode DISH_SKU_PRICE_INVALID = new ErrorCode(1_010_001_010, "SKU售价必须大于0");
    ErrorCode COMBO_NOT_EXISTS = new ErrorCode(1_010_001_007, "套餐不存在");
    ErrorCode STORE_DISH_NOT_AVAILABLE = new ErrorCode(1_010_001_008, "门店未售卖该菜品");

    // ========== 订单相关 1-010-002-xxx ==========
    ErrorCode ORDER_NOT_EXISTS = new ErrorCode(1_010_002_001, "订单不存在");
    ErrorCode ORDER_STATUS_ERROR = new ErrorCode(1_010_002_002, "订单状态不正确");
    ErrorCode ORDER_ITEM_NOT_EXISTS = new ErrorCode(1_010_002_003, "订单项不存在");
    ErrorCode REFUND_NOT_EXISTS = new ErrorCode(1_010_002_004, "退款单不存在");
    ErrorCode TABLE_OCCUPIED = new ErrorCode(1_010_002_005, "该桌台已被占用");

    // ========== 库存相关 1-010-003-xxx ==========
    ErrorCode MATERIAL_NOT_EXISTS = new ErrorCode(1_010_003_001, "原料不存在");
    ErrorCode BOM_NOT_EXISTS = new ErrorCode(1_010_003_002, "BOM配方不存在");
    ErrorCode INVENTORY_NOT_ENOUGH = new ErrorCode(1_010_003_003, "库存不足");
    ErrorCode INVENTORY_BATCH_NOT_EXISTS = new ErrorCode(1_010_003_004, "库存批次不存在");

    // ========== KDS / 厨打 1-010-004-xxx ==========
    ErrorCode KITCHEN_STATION_NOT_EXISTS = new ErrorCode(1_010_004_001, "厨房档口不存在");
    ErrorCode PRINTER_NOT_EXISTS = new ErrorCode(1_010_004_002, "打印机不存在");
    ErrorCode PRINT_TASK_FAILED = new ErrorCode(1_010_004_003, "打印任务失败");

}
