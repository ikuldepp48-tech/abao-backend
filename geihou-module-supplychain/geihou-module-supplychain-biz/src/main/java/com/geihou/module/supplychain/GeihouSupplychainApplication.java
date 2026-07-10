package com.geihou.module.supplychain;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.geihou.module.supplychain", markerInterface = BaseMapperX.class)
@SpringBootApplication
public class GeihouSupplychainApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeihouSupplychainApplication.class, args);
    }
}
