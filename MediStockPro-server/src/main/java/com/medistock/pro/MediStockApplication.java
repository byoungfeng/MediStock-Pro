package com.medistock.pro;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MediStock Pro 医院进销存系统
 *
 * @author MediStock
 */
@SpringBootApplication
@MapperScan("com.medistock.pro.modules.**.mapper")
public class MediStockApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediStockApplication.class, args);
    }
}
