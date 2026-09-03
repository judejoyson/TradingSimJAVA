package com.tradingsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Maps {@code tradingsim.*} settings from {@code application.properties}.
 *
 * <p>Using a record makes configuration immutable after startup and gives
 * services typed values such as {@link Duration} and {@link BigDecimal}.</p>
 */
@ConfigurationProperties(prefix = "tradingsim")
public record TradingSimulatorProperties(
        Finnhub finnhub,
        Duration quoteCacheDuration,
        BigDecimal startingCash) {

    public record Finnhub(String baseUrl, String apiKey) {
    }
}
