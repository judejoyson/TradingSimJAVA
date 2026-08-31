package com.tradingsim;

import com.tradingsim.config.TradingSimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TradingSimulatorProperties.class)
public class TradingSimulatorApp {
    public static void main(String[] args) {
        SpringApplication.run(TradingSimulatorApp.class, args);
    }
}
