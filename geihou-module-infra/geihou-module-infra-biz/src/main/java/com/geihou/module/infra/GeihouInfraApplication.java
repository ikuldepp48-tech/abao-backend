package com.geihou.module.infra;

import com.geihou.framework.mybatis.core.mapper.BaseMapperX;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.geihou.module.infra", markerInterface = BaseMapperX.class)
@SpringBootApplication
public class GeihouInfraApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeihouInfraApplication.class, args);
    }
}
