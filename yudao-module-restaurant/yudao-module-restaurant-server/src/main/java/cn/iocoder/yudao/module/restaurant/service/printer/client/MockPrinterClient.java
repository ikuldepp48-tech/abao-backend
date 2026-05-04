package cn.iocoder.yudao.module.restaurant.service.printer.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 打印机 —— 开发期使用，打印内容输出到日志
 */
@Slf4j
@Component
public class MockPrinterClient implements CloudPrinterClient {

    @Override
    public boolean print(String deviceNo, String deviceKey, String content) {
        log.info("[MockPrinter][模拟打印 deviceNo({})]\n{}", deviceNo, content);
        return true;
    }

    @Override
    public boolean isOnline(String deviceNo, String deviceKey) {
        return true;
    }

}
