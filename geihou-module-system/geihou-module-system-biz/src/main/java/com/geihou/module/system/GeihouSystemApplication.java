package com.geihou.module.system;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.geihou.module.system", markerInterface = BaseMapperX.class)
@SpringBootApplication
public class GeihouSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeihouSystemApplication.class, args);
    }
}
