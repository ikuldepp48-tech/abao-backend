package cn.iocoder.yudao.module.restaurant.service.printer;

/**
 * 打印任务 Service —— 触发打印 + 失败重试
 */
public interface PrintTaskService {

    /**
     * 为厨房订单触发打印
     *
     * @param orderId 订单ID
     */
    void printOrderForKitchen(Long orderId);

    /**
     * 重试失败的打印任务
     */
    void retryFailedTasks();

}
