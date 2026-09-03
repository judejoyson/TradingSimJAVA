package com.tradingsim;

import com.tradingsim.config.TradingSimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TradingSimulatorProperties.class)
/**
 * Starts the embedded web server and asks Spring to discover this project's
 * controllers, services, and configuration classes.
 */
public class TradingSimulatorApp {
    public static void main(String[] args) {
        SpringApplication.run(TradingSimulatorApp.class, args);
    }
}
