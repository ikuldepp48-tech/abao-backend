package com.geihou.module.supplychain.production.service;

import com.geihou.common.pojo.PageResult;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderCreateReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderStageTransitionReqVO;
import com.geihou.module.supplychain.production.controller.admin.vo.ProductionOrderUpdateReqVO;
import com.geihou.module.supplychain.production.dal.dataobject.ProductionOrderDO;

/**
 * 生产工单 Service 接口。
 *
 * <p>Source: TASK-G2-02K, PRD-组2-02 §10.1。
 */
public interface ProductionOrderService {

    /**
     * 创建生产工单。
     * 校验: product_id 的 product_type = SEMI_FINISHED; recipe_id 的 status = ACTIVE; location_id 的 is_active = true。
     * 初始状态: production_stage = CREATED。
     */
    ProductionOrderDO createProductionOrder(ProductionOrderCreateReqVO reqVO);

    /**
     * 查询生产工单详情（租户隔离）。
     */
    ProductionOrderDO getProductionOrder(Long id, Long tenantId);

    /**
     * 分页查询生产工单（租户隔离，可选状态筛选）。
     */
    PageResult<ProductionOrderDO> listProductionOrders(Long tenantId, String productionStage,
                                                       Integer pageNo, Integer pageSize);

    /**
     * 更新生产工单（仅允许在 CREATED 状态下更新部分字段）。
     */
    ProductionOrderDO updateProductionOrder(ProductionOrderUpdateReqVO reqVO);

    /**
     * 状态流转（租户隔离，乐观锁）。
     * 支持: CREATED→MATERIAL_REQUEST, MATERIAL_REQUEST→IN_PROGRESS,
     * IN_PROGRESS→QUALITY_CHECK, QUALITY_CHECK→COMPLETED, QUALITY_CHECK→REWORK,
     * REWORK→IN_PROGRESS, IN_PROGRESS→COMPLETED(直通完工), 非终态→CANCELLED。
     * QUALITY_CHECK/REWORK 不写 stock_event; COMPLETED 写 PRODUCTION_OUT+PRODUCTION_IN。
     */
    ProductionOrderDO transitionStage(ProductionOrderStageTransitionReqVO reqVO);
}
