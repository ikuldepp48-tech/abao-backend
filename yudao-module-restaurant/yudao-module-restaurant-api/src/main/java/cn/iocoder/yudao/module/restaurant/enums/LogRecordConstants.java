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

}
