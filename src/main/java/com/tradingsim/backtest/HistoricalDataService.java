package com.tradingsim.backtest;

import com.tradingsim.replay.HistoricalInstrumentSpec;
import com.tradingsim.replay.ReplayCatalog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Produces deterministic daily OHLCV history for date-range backtests.
 *
 * <p>Generation begins from one fixed anchor, then filters the requested range.
 * This ensures a date has the same price even when users choose different start
 * dates.</p>
 */
@Service
public final class HistoricalDataService {
    public static final LocalDate EARLIEST_DATE = LocalDate.of(2015, 1, 1);
    public static final LocalDate LATEST_DATE = LocalDate.of(2026, 8, 31);

    private final ReplayCatalog catalog;

    public HistoricalDataService(ReplayCatalog catalog) {
        this.catalog = catalog;
    }

    public HistoricalInstrumentSpec instrument(String market, String symbol) {
        return catalog.historicalSpec(market, symbol);
    }

    public List<BacktestCandle> dailyCandles(
            HistoricalInstrumentSpec instrument,
            LocalDate startDate,
            LocalDate endDate) {
        Random random = new Random(instrument.symbol().hashCode());
        List<BacktestCandle> result = new ArrayList<>();
        BigDecimal previousClose = instrument.startingPrice();
        int marketDayIndex = 0;

        for (LocalDate date = EARLIEST_DATE;
                !date.isAfter(endDate);
                date = date.plusDays(1)) {
            if (!isTradingDay(instrument.market(), date)) {
                continue;
            }
            double open = previousClose.doubleValue()
                    * (1 + random.nextGaussian() * instrument.volatility() * 0.35);
            double cycle = Math.sin((marketDayIndex + Math.abs(instrument.symbol().hashCode() % 97))
                    / 53.0) * instrument.volatility() * 0.3;
            double drift = instrument.market().equals("CRYPTO") ? 0.00032 : 0.00018;
            double close = Math.max(
                    open * (1 + drift + cycle + random.nextGaussian() * instrument.volatility()),
                    Math.pow(10, -instrument.pricePrecision()));
            double wick = Math.abs(random.nextGaussian())
                    * instrument.volatility() * open * 0.75;
            double high = Math.max(open, close) + wick;
            double low = Math.max(
                    Math.min(open, close) - wick,
                    Math.pow(10, -instrument.pricePrecision()));
            long volume = 500_000L
                    + random.nextInt(900_000)
                    + Math.round(Math.abs(close - open) / open * 20_000_000);

            BacktestCandle candle = new BacktestCandle(
                    date,
                    decimal(open, instrument.pricePrecision()),
                    decimal(high, instrument.pricePrecision()),
                    decimal(low, instrument.pricePrecision()),
                    decimal(close, instrument.pricePrecision()),
                    volume);
            if (!date.isBefore(startDate)) {
                result.add(candle);
            }
            previousClose = candle.close();
            marketDayIndex++;
        }
        return List.copyOf(result);
    }

    private boolean isTradingDay(String market, LocalDate date) {
        if (market.equals("CRYPTO")) {
            return true;
        }
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private BigDecimal decimal(double value, int precision) {
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP);
    }
}
