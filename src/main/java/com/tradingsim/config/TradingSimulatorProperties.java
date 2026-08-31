package com.tradingsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "tradingsim")
public record TradingSimulatorProperties(
        Finnhub finnhub,
        Duration quoteCacheDuration,
        BigDecimal startingCash) {

    public record Finnhub(String baseUrl, String apiKey) {
    }
}
