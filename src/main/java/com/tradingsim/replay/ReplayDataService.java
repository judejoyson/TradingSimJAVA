package com.tradingsim.replay;

import com.tradingsim.replay.ReplayCatalog.InstrumentDefinition;
import com.tradingsim.replay.ReplayCatalog.MarketDefinition;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;

/**
 * Builds repeatable replay sessions from catalog selections.
 *
 * <p>The generator is intentionally deterministic: the same market, symbol,
 * and timeframe produce the same seed and therefore the same candles.</p>
 */
@Service
public final class ReplayDataService {
    static final int CANDLE_COUNT = 360;
    static final int INITIAL_BARS = 70;
    private static final Instant SESSION_ANCHOR =
            Instant.parse("2026-01-05T14:30:00Z");

    private final ReplayCatalog catalog;
    private final SecureRandom sessionRandom = new SecureRandom();

    public ReplayDataService(ReplayCatalog catalog) {
        this.catalog = catalog;
    }

    public ReplayOptions options() {
        return catalog.options();
    }

    /**
     * Validates browser selections and returns all metadata and candles needed
     * for a self-contained replay tab.
     */
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
                market.executionProfile(),
                generateCandles(market, instrument, timeframe,
                        (market.id() + ":" + instrument.symbol() + ":" + timeframe.id()).hashCode()));
    }

    public ReplaySession createRandomSession(
            String requestedSymbol,
            String requestedTimeframe) {
        return createSeededRandomSession(requestedSymbol, requestedTimeframe).replay();
    }

    public SeededReplaySession createSeededRandomSession(
            String requestedSymbol,
            String requestedTimeframe) {
        long seed = sessionRandom.nextLong();
        return new SeededReplaySession(
                seed,
                createRandomSession(requestedSymbol, requestedTimeframe, seed));
    }

    public ReplaySession createRandomSession(
            String requestedSymbol,
            String requestedTimeframe,
            long seed) {
        MarketDefinition market = catalog.market("STOCKS");
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
                market.executionProfile(),
                generateCandles(market, instrument, timeframe, seed));
    }

    private List<ReplayCandle> generateCandles(
            MarketDefinition market,
            InstrumentDefinition instrument,
            TimeframeOption timeframe,
            long seed) {
        DeterministicSecureRandom random = new DeterministicSecureRandom(seed);
        List<ReplayCandle> candles = new ArrayList<>(CANDLE_COUNT);
        BigDecimal previousClose = instrument.startingPriceValue();
        Instant candleTime = SESSION_ANCHOR;

        for (int index = 0; index < CANDLE_COUNT; index++) {
            double open = previousClose.doubleValue();
            // Combine a smooth cycle, three broad trend regimes, and seeded
            // random noise so sessions contain both trends and reversals.
            double cyclicalMove = Math.sin((index + Math.abs(seed % 31)) / 19.0)
                    * instrument.volatilityValue() * 0.28;
            double regime = index < 120 ? 0.00010 : index < 240 ? -0.00006 : 0.00014;
            double returnRate = regime + cyclicalMove
                    + random.nextGaussian() * instrument.volatilityValue();
            double close = Math.max(open * (1.0 + returnRate), minimumPrice(instrument));
            double wickSize = Math.abs(random.nextGaussian())
                    * instrument.volatilityValue() * open * 0.7;
            // High and low are derived from the candle body, guaranteeing valid
            // OHLC relationships even after generated returns are negative.
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

    /**
     * Reconstructs a session from its stored seed without exposing the
     * recoverable 48-bit state used by {@link java.util.Random}.
     */
    private static final class DeterministicSecureRandom {
        private final Mac mac;
        private long counter;
        private Double nextGaussian;

        private DeterministicSecureRandom(long seed) {
            try {
                byte[] key = MessageDigest.getInstance("SHA-256")
                        .digest(ByteBuffer.allocate(Long.BYTES).putLong(seed).array());
                mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException(
                        "The secure replay generator is unavailable.", exception);
            }
        }

        private double nextDouble() {
            return (nextLong() >>> 11) * 0x1.0p-53;
        }

        private int nextInt(int bound) {
            return (int) Math.floor(nextDouble() * bound);
        }

        private double nextGaussian() {
            if (nextGaussian != null) {
                double value = nextGaussian;
                nextGaussian = null;
                return value;
            }
            double first = Math.max(nextDouble(), Double.MIN_VALUE);
            double second = nextDouble();
            double magnitude = Math.sqrt(-2.0 * Math.log(first));
            double angle = 2.0 * Math.PI * second;
            nextGaussian = magnitude * Math.sin(angle);
            return magnitude * Math.cos(angle);
        }

        private long nextLong() {
            byte[] digest = mac.doFinal(
                    ByteBuffer.allocate(Long.BYTES).putLong(counter++).array());
            return ByteBuffer.wrap(digest).getLong();
        }
    }
}
