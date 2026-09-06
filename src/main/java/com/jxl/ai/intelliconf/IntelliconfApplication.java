package com.jxl.ai.intelliconf;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.jxl.ai.intelliconf.dao.mapper", "com.jxl.ai.intelliconf.author_discovery.repository"})
public class IntelliconfApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelliconfApplication.class, args);
    }

}
