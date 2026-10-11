package com.racetracker.rfidreaderimpinj;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ImpinjReaderProperties.class)
public class ImpinjReaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImpinjReaderApplication.class, args);
    }
}
