package cn.iocoder.yudao.module.restaurant.service.order;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.restaurant.controller.admin.order.vo.RestaurantOrderPageReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderCreateReqVO;
import cn.iocoder.yudao.module.restaurant.controller.app.order.vo.AppOrderRespVO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;

public interface RestaurantOrderService {

    /** 创建订单 */
    AppOrderRespVO createOrder(Long memberId, String userIp, AppOrderCreateReqVO reqVO);

    /** 订单详情 */
    AppOrderRespVO getOrderDetail(Long orderId);

    /** 订单详情（含权限校验） */
    AppOrderRespVO getOrderDetail(Long orderId, Long memberId);

    /** 用户订单分页 */
    PageResult<RestaurantOrderDO> getOrderPage(Long memberId, Integer pageNo, Integer pageSize, Integer status);

    /** 管理后台订单分页 */
    PageResult<RestaurantOrderDO> getOrderPage(RestaurantOrderPageReqVO reqVO);

    /** 取消订单 */
    void cancelOrder(Long orderId, Long memberId);

    /** 用户各状态订单数量 */
    java.util.Map<String, Long> getOrderCount(Long memberId);

    /** 支付成功回调 */
    void onPaySuccess(String orderNo, Long payOrderId);

    /** 通用状态变更（含校验 + 日志） */
    void updateOrderStatus(Long orderId, Integer newStatus, Integer operatorType, Long operatorId, String remark);

    /** 系统自动取消订单（超时用） */
    void cancelOrderBySystem(Long orderId);

    /** 按订单号查询 */
    RestaurantOrderDO getOrderByOrderNo(String orderNo);
}
