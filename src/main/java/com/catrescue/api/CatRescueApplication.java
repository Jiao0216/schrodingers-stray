package com.catrescue.api;

import com.catrescue.api.config.DataAccessProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DataAccessProperties.class)
public class CatRescueApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatRescueApplication.class, args);
    }
}
