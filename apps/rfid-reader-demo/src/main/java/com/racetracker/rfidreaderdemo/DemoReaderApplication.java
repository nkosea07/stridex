package com.racetracker.rfidreaderdemo;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(DemoReaderProperties.class)
public class DemoReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoReaderApplication.class, args);
    }
}
