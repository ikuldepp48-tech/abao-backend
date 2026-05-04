package cn.iocoder.yudao.module.restaurant.service.printer;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderDO;
import cn.iocoder.yudao.module.restaurant.dal.dataobject.order.RestaurantOrderItemDO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 58mm 热敏小票模板 —— 生成纯文本打印内容
 */
public class ReceiptTemplate {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 构建厨打小票内容
     *
     * @param order     订单
     * @param items     订单明细
     * @param storeName 门店名称
     * @param tableNo   桌号
     * @return 打印内容（纯文本）
     */
    public static String buildKitchenReceipt(RestaurantOrderDO order, List<RestaurantOrderItemDO> items, String storeName, String tableNo) {
        StringBuilder sb = new StringBuilder();
        String line = "================================";

        // 标题
        sb.append(line).append("\n");
        sb.append("   ").append(storeName).append("  ·  厨房联\n");
        sb.append(line).append("\n\n");

        // 订单信息
        sb.append("订单号：").append(order.getOrderNo()).append("\n");
        if (tableNo != null && !tableNo.isEmpty()) {
            sb.append("桌号：").append(tableNo).append("   ");
        }
        sb.append("人数：").append(order.getDinerCount() != null ? order.getDinerCount() : "-").append("\n");
        sb.append("时间：").append(formatTime(order.getCreateTime())).append("\n");
        sb.append(line).append("\n\n");

        // 菜品明细
        for (int i = 0; i < items.size(); i++) {
            RestaurantOrderItemDO item = items.get(i);
            sb.append("  ").append(item.getQuantity()).append("× ").append(item.getSpuName()).append("\n");
            if (item.getSkuName() != null && !item.getSkuName().isEmpty()) {
                sb.append("     ").append(item.getSkuName()).append("\n");
            }
            // 加料
            appendAddons(sb, item.getAddonsJson());
            // 备注
            if (item.getCustomerRemark() != null && !item.getCustomerRemark().isEmpty()) {
                sb.append("     备注：").append(item.getCustomerRemark()).append("\n");
            }
            if (i < items.size() - 1) {
                sb.append("\n");
            }
        }

        // 尾部
        sb.append("\n").append(line).append("\n");
        sb.append("   阿堡 - 用心做好每一顿\n");
        sb.append(line).append("\n");

        return sb.toString();
    }

    private static void appendAddons(StringBuilder sb, String addonsJson) {
        if (addonsJson == null || addonsJson.isEmpty() || "[]".equals(addonsJson)) {
            return;
        }
        try {
            JSONArray arr = new JSONArray(addonsJson);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.getStr("name", "");
                Object price = obj.get("price");
                sb.append("     【").append(name);
                if (price != null && !"0".equals(price.toString()) && !"0.00".equals(price.toString())) {
                    sb.append(" +").append(price);
                }
                sb.append("】\n");
            }
        } catch (Exception e) {
            // JSON 解析失败忽略，不影响主流程
        }
    }

    private static String formatTime(LocalDateTime time) {
        if (time == null) {
            return "-";
        }
        return time.format(TIME_FMT);
    }

}
