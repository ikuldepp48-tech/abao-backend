package com.geihou.module.finance;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.geihou.module.finance", markerInterface = BaseMapperX.class)
@SpringBootApplication
public class GeihouFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeihouFinanceApplication.class, args);
    }
}
