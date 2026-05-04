package cn.iocoder.yudao.module.restaurant.service.printer.client;

/**
 * 云打印机客户端接口 —— 屏蔽厂商差异
 */
public interface CloudPrinterClient {

    /**
     * 发送打印指令
     * @param deviceNo 设备号
     * @param deviceKey 设备密钥
     * @param content 打印内容（纯文本）
     * @return true=成功
     */
    boolean print(String deviceNo, String deviceKey, String content);

    /**
     * 查询打印机在线状态
     * @param deviceNo 设备号
     * @param deviceKey 设备密钥
     * @return true=在线
     */
    boolean isOnline(String deviceNo, String deviceKey);

}
