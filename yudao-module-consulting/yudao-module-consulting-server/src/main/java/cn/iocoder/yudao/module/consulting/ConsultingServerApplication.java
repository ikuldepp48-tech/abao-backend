package cn.iocoder.yudao.module.consulting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 咨询模块 - 启动入口
 */
@SpringBootApplication
@EnableScheduling
public class ConsultingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultingServerApplication.class, args);
    }
}
