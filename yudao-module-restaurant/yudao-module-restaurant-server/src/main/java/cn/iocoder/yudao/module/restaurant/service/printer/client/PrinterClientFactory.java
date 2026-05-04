package cn.iocoder.yudao.module.restaurant.service.printer.client;

import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 打印机客户端工厂 —— 根据 provider 返回对应的 CloudPrinterClient 实现
 */
@Component
public class PrinterClientFactory {

    @Resource
    private Map<String, CloudPrinterClient> clientMap;

    /**
     * 获取对应厂商的打印机客户端，未找到时返回 mock
     */
    public CloudPrinterClient getClient(String provider) {
        if (provider == null) {
            return clientMap.get("mockPrinterClient");
        }
        // provider 可能是 "mock"、"feieyun" 等，匹配 Bean 名
        String beanName = provider + "PrinterClient";
        CloudPrinterClient client = clientMap.get(beanName);
        if (client == null) {
            // 未知厂商，fallback 到 mock
            return clientMap.get("mockPrinterClient");
        }
        return client;
    }

}
