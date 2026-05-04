package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrinterDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.printer.RestaurantPrintTaskDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.store.RestaurantStoreDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderItemMapper;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.table.RestaurantTableDO;
import cn.iocoder.yudao.module.restaurant.dal.mysql.order.RestaurantOrderMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.printer.RestaurantPrintTaskMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.store.RestaurantStoreMapper;
import cn.iocoder.yudao.module.restaurant.dal.mysql.table.RestaurantTableMapper;
import cn.iocoder.yudao.module.restaurant.service.printer.client.PrinterClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PrintTaskServiceImpl implements PrintTaskService {

    @Resource
    private RestaurantOrderMapper orderMapper;

    @Resource
    private RestaurantOrderItemMapper orderItemMapper;

    @Resource
    private RestaurantPrinterService printerService;

    @Resource
    private RestaurantStoreMapper storeMapper;

    @Resource
    private RestaurantTableMapper tableMapper;

    @Resource
    private RestaurantPrintTaskMapper printTaskMapper;

    @Resource
    private PrinterClientFactory printerClientFactory;

    @Override
    public void printOrderForKitchen(Long orderId) {
        // 1. 查订单
        RestaurantOrderDO order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[printOrderForKitchen][订单不存在 orderId({})]", orderId);
            return;
        }

        // 2. 查订单明细
        List<RestaurantOrderItemDO> items = orderItemMapper.selectListByOrderId(orderId);

        // 3. 找到门店的厨打打印机
        List<RestaurantPrinterDO> printers = printerService.listKitchenPrinters(order.getStoreId());
        if (printers.isEmpty()) {
            log.info("[printOrderForKitchen][订单({})门店({})没有厨打打印机，跳过]", order.getOrderNo(), order.getStoreId());
            return;
        }

        // 4. 获取门店名称和桌号
        String storeName = "阿堡厨房";
        if (order.getStoreId() != null) {
            RestaurantStoreDO store = storeMapper.selectById(order.getStoreId());
            if (store != null) {
                storeName = store.getName();
            }
        }
        String tableNo = null;
        if (order.getTableId() != null) {
            RestaurantTableDO table = tableMapper.selectById(order.getTableId());
            if (table != null) {
                tableNo = table.getTableNo();
            }
        }

        // 5. 生成小票内容
        String content = ReceiptTemplate.buildKitchenReceipt(order, items, storeName, tableNo);

        // 6. 对每台打印机执行打印
        for (RestaurantPrinterDO printer : printers) {
            RestaurantPrintTaskDO task = RestaurantPrintTaskDO.builder()
                    .printerId(printer.getId())
                    .orderId(orderId)
                    .content(content)
                    .status(0)
                    .retryCount(0)
                    .build();
            printTaskMapper.insert(task);

            try {
                boolean ok = printerClientFactory.getClient(printer.getProvider())
                        .print(printer.getDeviceNo(), printer.getDeviceKey(), content);
                if (ok) {
                    printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                            .id(task.getId())
                            .status(1)
                            .printTime(LocalDateTime.now())
                            .build());
                    log.info("[printOrderForKitchen][订单({})打印成功 printerId({})]", order.getOrderNo(), printer.getId());
                } else {
                    printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                            .id(task.getId())
                            .status(2)
                            .errorMsg("打印返回失败")
                            .build());
                    log.warn("[printOrderForKitchen][订单({})打印返回失败 printerId({})]", order.getOrderNo(), printer.getId());
                }
            } catch (Exception e) {
                printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                        .id(task.getId())
                        .status(2)
                        .errorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误")
                        .build());
                log.error("[printOrderForKitchen][订单({})打印异常 printerId({})]", order.getOrderNo(), printer.getId(), e);
            }
        }
    }

    @Override
    public void retryFailedTasks() {
        List<RestaurantPrintTaskDO> tasks = printTaskMapper.selectFailedRetryable();
        if (tasks.isEmpty()) {
            return;
        }
        log.info("[retryFailedTasks][发现 {} 个待重试的打印任务]", tasks.size());

        for (RestaurantPrintTaskDO task : tasks) {
            RestaurantPrinterDO printer = printerService.getPrinter(task.getPrinterId());
            if (printer == null) {
                log.warn("[retryFailedTasks][打印机不存在 printerId({})]", task.getPrinterId());
                int count = task.getRetryCount() != null ? task.getRetryCount() + 1 : 1;
                printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                        .id(task.getId())
                        .retryCount(count)
                        .errorMsg("打印机已删除")
                        .build());
                continue;
            }

            int newRetryCount = task.getRetryCount() != null ? task.getRetryCount() + 1 : 1;
            try {
                boolean ok = printerClientFactory.getClient(printer.getProvider())
                        .print(printer.getDeviceNo(), printer.getDeviceKey(), task.getContent());
                if (ok) {
                    printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                            .id(task.getId())
                            .status(1)
                            .retryCount(newRetryCount)
                            .errorMsg(null)
                            .printTime(LocalDateTime.now())
                            .build());
                    log.info("[retryFailedTasks][重试成功 taskId({})]", task.getId());
                } else {
                    printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                            .id(task.getId())
                            .retryCount(newRetryCount)
                            .errorMsg(task.getErrorMsg() + " (重试" + newRetryCount + "次仍失败)")
                            .build());
                }
            } catch (Exception e) {
                String oldMsg = task.getErrorMsg() != null ? task.getErrorMsg() + " " : "";
                String newMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                String truncated = newMsg.length() > 200 ? newMsg.substring(0, 200) : newMsg;
                printTaskMapper.updateById(RestaurantPrintTaskDO.builder()
                        .id(task.getId())
                        .retryCount(newRetryCount)
                        .errorMsg(oldMsg + "(重试" + newRetryCount + "次异常: " + truncated + ")")
                        .build());
                log.error("[retryFailedTasks][重试异常 taskId({})]", task.getId(), e);
            }
        }
    }

}
