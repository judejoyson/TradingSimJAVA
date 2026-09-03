package com.tradingsim.replay;

import com.tradingsim.web.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry of every selectable market, instrument, and timeframe.
 *
 * <p>Add or tune demo instruments here. Keeping this information on the server
 * ensures the home page and session validation use identical choices.</p>
 */
@Component
public final class ReplayCatalog {
    private final Map<String, MarketDefinition> markets = new LinkedHashMap<>();
    private final Map<String, TimeframeOption> timeframes = new LinkedHashMap<>();

    public ReplayCatalog() {
        registerMarket(
                "STOCKS",
                "U.S. Stocks",
                "Large-cap shares and broad-market ETFs",
                new ExecutionProfile(2.0, 1.0, 1.0, 0.05),
                new InstrumentDefinition("SPY", "S&P 500 ETF", 2, "540", "0.0014"),
                new InstrumentDefinition("QQQ", "Nasdaq 100 ETF", 2, "470", "0.0018"),
                new InstrumentDefinition("AAPL", "Apple", 2, "220", "0.0020"));
        registerMarket(
                "FOREX",
                "Forex",
                "Major currency pairs with five-decimal pricing",
                new ExecutionProfile(1.0, 0.5, 0.2, 0.10),
                new InstrumentDefinition("EURUSD", "Euro / U.S. Dollar", 5, "1.08500", "0.00045"),
                new InstrumentDefinition("GBPUSD", "British Pound / U.S. Dollar", 5, "1.27000", "0.00055"),
                new InstrumentDefinition("USDJPY", "U.S. Dollar / Japanese Yen", 3, "149.000", "0.00050"));
        registerMarket(
                "CRYPTO",
                "Crypto",
                "High-volatility digital asset markets",
                new ExecutionProfile(8.0, 3.0, 5.0, 0.10),
                new InstrumentDefinition("BTCUSD", "Bitcoin / U.S. Dollar", 2, "65000", "0.0055"),
                new InstrumentDefinition("ETHUSD", "Ethereum / U.S. Dollar", 2, "3500", "0.0070"),
                new InstrumentDefinition("SOLUSD", "Solana / U.S. Dollar", 2, "145", "0.0090"));

        registerTimeframe("1m", "1 minute", 1);
        registerTimeframe("5m", "5 minutes", 5);
        registerTimeframe("15m", "15 minutes", 15);
        registerTimeframe("1h", "1 hour", 60);
    }

    public ReplayOptions options() {
        List<MarketOption> marketOptions = markets.values().stream()
                .map(MarketDefinition::toOption)
                .toList();
        return new ReplayOptions(marketOptions, List.copyOf(timeframes.values()));
    }

    /**
     * Resolves validated instrument metadata for longer historical backtests.
     */
    public HistoricalInstrumentSpec historicalSpec(
            String requestedMarket,
            String requestedSymbol) {
        MarketDefinition market = market(requestedMarket);
        InstrumentDefinition instrument = instrument(market, requestedSymbol);
        return new HistoricalInstrumentSpec(
                market.id(),
                market.label(),
                instrument.symbol(),
                instrument.name(),
                instrument.pricePrecision(),
                instrument.startingPriceValue(),
                instrument.volatilityValue());
    }

    MarketDefinition market(String requestedMarket) {
        String id = normalize(requestedMarket);
        MarketDefinition market = markets.get(id);
        if (market == null) {
            throw new BadRequestException("Unknown market. Choose STOCKS, FOREX, or CRYPTO.");
        }
        return market;
    }

    InstrumentDefinition instrument(MarketDefinition market, String requestedSymbol) {
        String symbol = normalize(requestedSymbol);
        return market.instruments().stream()
                .filter(instrument -> instrument.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        symbol + " is not available in the " + market.label() + " demo market."));
    }

    TimeframeOption timeframe(String requestedTimeframe) {
        if (requestedTimeframe == null) {
            throw new BadRequestException("A timeframe is required.");
        }
        TimeframeOption timeframe = timeframes.get(requestedTimeframe.toLowerCase(Locale.US));
        if (timeframe == null) {
            throw new BadRequestException("Unknown timeframe. Choose 1m, 5m, 15m, or 1h.");
        }
        return timeframe;
    }

    private void registerMarket(
            String id,
            String label,
            String description,
            ExecutionProfile executionProfile,
            InstrumentDefinition... instruments) {
        markets.put(
                id,
                new MarketDefinition(id, label, description, executionProfile, List.of(instruments)));
    }

    private void registerTimeframe(String id, String label, int minutes) {
        timeframes.put(id, new TimeframeOption(id, label, minutes));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Market and symbol are required.");
        }
        return value.trim().toUpperCase(Locale.US);
    }

    record MarketDefinition(
            String id,
            String label,
            String description,
            ExecutionProfile executionProfile,
            List<InstrumentDefinition> instruments) {

        private MarketOption toOption() {
            return new MarketOption(
                    id,
                    label,
                    description,
                    instruments.stream().map(InstrumentDefinition::toOption).toList());
        }
    }

    record InstrumentDefinition(
            String symbol,
            String name,
            int pricePrecision,
            String startingPrice,
            String volatility) {

        private InstrumentOption toOption() {
            return new InstrumentOption(symbol, name, pricePrecision);
        }

        BigDecimal startingPriceValue() {
            return new BigDecimal(startingPrice);
        }

        double volatilityValue() {
            return Double.parseDouble(volatility);
        }
    }
}
