package com.tradingsim.replay;

import com.tradingsim.replay.ReplayCatalog.InstrumentDefinition;
import com.tradingsim.replay.ReplayCatalog.MarketDefinition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public final class ReplayDataService {
    static final int CANDLE_COUNT = 360;
    static final int INITIAL_BARS = 70;
    private static final Instant SESSION_ANCHOR =
            Instant.parse("2026-01-05T14:30:00Z");

    private final ReplayCatalog catalog;

    public ReplayDataService(ReplayCatalog catalog) {
        this.catalog = catalog;
    }

    public ReplayOptions options() {
        return catalog.options();
    }

    public ReplaySession createSession(
            String requestedMarket,
            String requestedSymbol,
            String requestedTimeframe) {
        MarketDefinition market = catalog.market(requestedMarket);
        InstrumentDefinition instrument = catalog.instrument(market, requestedSymbol);
        TimeframeOption timeframe = catalog.timeframe(requestedTimeframe);

        return new ReplaySession(
                market.id(),
                market.label(),
                instrument.symbol(),
                instrument.name(),
                timeframe.id(),
                timeframe.minutes(),
                instrument.pricePrecision(),
                INITIAL_BARS,
                generateCandles(market, instrument, timeframe));
    }

    private List<ReplayCandle> generateCandles(
            MarketDefinition market,
            InstrumentDefinition instrument,
            TimeframeOption timeframe) {
        long seed = (market.id() + ":" + instrument.symbol() + ":" + timeframe.id()).hashCode();
        Random random = new Random(seed);
        List<ReplayCandle> candles = new ArrayList<>(CANDLE_COUNT);
        BigDecimal previousClose = instrument.startingPriceValue();
        Instant candleTime = SESSION_ANCHOR;

        for (int index = 0; index < CANDLE_COUNT; index++) {
            double open = previousClose.doubleValue();
            double cyclicalMove = Math.sin((index + Math.abs(seed % 31)) / 19.0)
                    * instrument.volatilityValue() * 0.28;
            double regime = index < 120 ? 0.00010 : index < 240 ? -0.00006 : 0.00014;
            double returnRate = regime + cyclicalMove
                    + random.nextGaussian() * instrument.volatilityValue();
            double close = Math.max(open * (1.0 + returnRate), minimumPrice(instrument));
            double wickSize = Math.abs(random.nextGaussian())
                    * instrument.volatilityValue() * open * 0.7;
            double high = Math.max(open, close) + wickSize;
            double low = Math.max(
                    Math.min(open, close) - wickSize * (0.75 + random.nextDouble() * 0.5),
                    minimumPrice(instrument));
            long volume = baseVolume(market.id())
                    + Math.round(Math.abs(returnRate) * baseVolume(market.id()) * 45)
                    + random.nextInt((int) Math.max(1, baseVolume(market.id()) / 3));

            ReplayCandle candle = new ReplayCandle(
                    candleTime.getEpochSecond(),
                    decimal(open, instrument.pricePrecision()),
                    decimal(high, instrument.pricePrecision()),
                    decimal(low, instrument.pricePrecision()),
                    decimal(close, instrument.pricePrecision()),
                    volume);
            candles.add(candle);
            previousClose = candle.close();
            candleTime = candleTime.plus(timeframe.minutes(), ChronoUnit.MINUTES);
        }
        return List.copyOf(candles);
    }

    private BigDecimal decimal(double value, int precision) {
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP);
    }

    private double minimumPrice(InstrumentDefinition instrument) {
        return Math.pow(10, -instrument.pricePrecision());
    }

    private long baseVolume(String marketId) {
        return switch (marketId) {
            case "STOCKS" -> 180_000L;
            case "FOREX" -> 75_000L;
            case "CRYPTO" -> 28_000L;
            default -> 50_000L;
        };
    }
}
