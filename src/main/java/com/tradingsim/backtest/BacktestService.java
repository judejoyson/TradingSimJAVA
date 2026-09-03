package com.tradingsim.backtest;

import com.tradingsim.replay.HistoricalInstrumentSpec;
import com.tradingsim.replay.ReplayCatalog;
import com.tradingsim.web.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a long-only backtest without peeking at future candle values.
 */
@Service
public final class BacktestService {
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final int MAX_RANGE_YEARS = 10;

    private final HistoricalDataService historicalData;
    private final ReplayCatalog catalog;

    public BacktestService(HistoricalDataService historicalData, ReplayCatalog catalog) {
        this.historicalData = historicalData;
        this.catalog = catalog;
    }

    public BacktestOptions options() {
        return new BacktestOptions(
                catalog.options().markets(),
                List.of(
                        new StrategyOption(
                                "BUY_AND_HOLD",
                                "Buy & Hold",
                                "Buy on the first open and hold through the selected period."),
                        new StrategyOption(
                                "MOVING_AVERAGE",
                                "Moving-average crossover",
                                "Own the asset while the fast average is above the slow average."),
                        new StrategyOption(
                                "RSI",
                                "RSI reversal",
                                "Buy when RSI is oversold and sell when it becomes overbought.")),
                HistoricalDataService.EARLIEST_DATE,
                HistoricalDataService.LATEST_DATE);
    }

    public BacktestResult run(BacktestRequest request) {
        Settings settings = validateAndApplyDefaults(request, true);
        HistoricalInstrumentSpec instrument =
                historicalData.instrument(request.market(), request.symbol());
        List<BacktestCandle> candles = historicalData.dailyCandles(
                instrument,
                request.startDate(),
                request.endDate());
        return result(request, settings, candles, instrument.name());
    }

    public BacktestResult runUploaded(
            BacktestRequest request,
            List<BacktestCandle> uploadedCandles,
            String filename) {
        Settings settings = validateAndApplyDefaults(request, false);
        List<BacktestCandle> selectedCandles = uploadedCandles.stream()
                .filter(candle -> !candle.date().isBefore(request.startDate())
                        && !candle.date().isAfter(request.endDate()))
                .toList();
        String sourceName = filename == null || filename.isBlank()
                ? "Uploaded CSV"
                : filename;
        return result(request, settings, selectedCandles, sourceName);
    }

    private BacktestResult result(
            BacktestRequest request,
            Settings settings,
            List<BacktestCandle> candles,
            String instrumentName) {
        if (candles.size() < settings.minimumCandles()) {
            throw new BadRequestException(
                    "The selected period needs at least " + settings.minimumCandles()
                            + " candle rows for this strategy.");
        }
        Simulation simulation = simulate(candles, request.startingCash(), settings);
        return new BacktestResult(
                request.market().trim().toUpperCase(Locale.US),
                request.symbol().trim().toUpperCase(Locale.US),
                instrumentName,
                settings.strategy().id,
                settings.strategy().description,
                candles.get(0).date(),
                candles.get(candles.size() - 1).date(),
                candles.size(),
                simulation.metrics(),
                simulation.equityCurve(),
                simulation.executions());
    }

    private Simulation simulate(
            List<BacktestCandle> candles,
            BigDecimal startingCash,
            Settings settings) {
        BigDecimal cash = startingCash;
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal openTradeCost = BigDecimal.ZERO;
        int completedTrades = 0;
        int winningTrades = 0;
        int losingTrades = 0;
        boolean desiredPosition = settings.strategy() == Strategy.BUY_AND_HOLD;
        List<BacktestExecution> executions = new ArrayList<>();
        List<EquityPoint> equityCurve = new ArrayList<>();
        BigDecimal peakEquity = startingCash;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        BigDecimal benchmarkStart = candles.get(0).open();

        for (int index = 0; index < candles.size(); index++) {
            BacktestCandle candle = candles.get(index);

            // A desired position was calculated using only the previous close.
            if (desiredPosition && quantity.signum() == 0) {
                BigDecimal feeMultiplier = BigDecimal.ONE.add(settings.feeRate(), MC);
                BigDecimal bought = cash.divide(
                        candle.open().multiply(feeMultiplier, MC),
                        8,
                        RoundingMode.DOWN);
                if (bought.signum() > 0) {
                    BigDecimal value = bought.multiply(candle.open(), MC);
                    BigDecimal fee = value.multiply(settings.feeRate(), MC);
                    cash = cash.subtract(value, MC).subtract(fee, MC);
                    quantity = bought;
                    totalFees = totalFees.add(fee, MC);
                    openTradeCost = value.add(fee, MC);
                    executions.add(execution(candle, "BUY", bought, fee, "Strategy entry"));
                }
            } else if (!desiredPosition && quantity.signum() > 0) {
                BigDecimal value = quantity.multiply(candle.open(), MC);
                BigDecimal fee = value.multiply(settings.feeRate(), MC);
                BigDecimal proceeds = value.subtract(fee, MC);
                cash = cash.add(proceeds, MC);
                totalFees = totalFees.add(fee, MC);
                executions.add(execution(
                        candle, "SELL", quantity, fee, "Strategy exit"));
                completedTrades++;
                if (proceeds.compareTo(openTradeCost) > 0) {
                    winningTrades++;
                } else {
                    losingTrades++;
                }
                quantity = BigDecimal.ZERO;
                openTradeCost = BigDecimal.ZERO;
            }

            BigDecimal equity = cash.add(quantity.multiply(candle.close(), MC), MC);
            peakEquity = peakEquity.max(equity);
            BigDecimal drawdown = percentage(
                    peakEquity.subtract(equity, MC),
                    peakEquity);
            maximumDrawdown = maximumDrawdown.max(drawdown);
            BigDecimal benchmarkEquity = startingCash.multiply(
                    candle.close().divide(benchmarkStart, MC),
                    MC);
            equityCurve.add(new EquityPoint(
                    candle.date(),
                    money(equity),
                    money(benchmarkEquity),
                    metric(drawdown)));

            if (index < candles.size() - 1 && settings.strategy() != Strategy.BUY_AND_HOLD) {
                desiredPosition = desiredPositionAtClose(
                        candles,
                        index,
                        settings,
                        quantity.signum() > 0);
            }
        }

        // Close any remaining position at the final close so metrics represent cash.
        BacktestCandle finalCandle = candles.get(candles.size() - 1);
        if (quantity.signum() > 0) {
            BigDecimal value = quantity.multiply(finalCandle.close(), MC);
            BigDecimal fee = value.multiply(settings.feeRate(), MC);
            BigDecimal proceeds = value.subtract(fee, MC);
            cash = cash.add(proceeds, MC);
            totalFees = totalFees.add(fee, MC);
            executions.add(new BacktestExecution(
                    finalCandle.date(),
                    "SELL",
                    finalCandle.close(),
                    quantity,
                    money(fee),
                    "End of backtest"));
            completedTrades++;
            if (proceeds.compareTo(openTradeCost) > 0) {
                winningTrades++;
            } else {
                losingTrades++;
            }
            EquityPoint previous = equityCurve.get(equityCurve.size() - 1);
            BigDecimal finalDrawdown = percentage(peakEquity.subtract(cash, MC), peakEquity);
            maximumDrawdown = maximumDrawdown.max(finalDrawdown);
            equityCurve.set(
                    equityCurve.size() - 1,
                    new EquityPoint(
                            previous.date(),
                            money(cash),
                            previous.benchmarkEquity(),
                            metric(finalDrawdown)));
        }

        BigDecimal netProfit = cash.subtract(startingCash, MC);
        BigDecimal totalReturn = percentage(netProfit, startingCash);
        BigDecimal benchmarkReturn = percentage(
                finalCandle.close().subtract(benchmarkStart, MC),
                benchmarkStart);
        long days = Math.max(
                1,
                ChronoUnit.DAYS.between(candles.get(0).date(), finalCandle.date()));
        double annualized = (Math.pow(
                cash.divide(startingCash, MC).doubleValue(),
                365.0 / days) - 1) * 100;
        BigDecimal winRate = completedTrades == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(winningTrades)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(completedTrades), MC);

        BacktestMetrics metrics = new BacktestMetrics(
                money(cash),
                money(netProfit),
                metric(totalReturn),
                metric(benchmarkReturn),
                metric(maximumDrawdown),
                metric(BigDecimal.valueOf(annualized)),
                money(totalFees),
                completedTrades,
                winningTrades,
                losingTrades,
                metric(winRate));
        return new Simulation(metrics, List.copyOf(equityCurve), List.copyOf(executions));
    }

    private boolean desiredPositionAtClose(
            List<BacktestCandle> candles,
            int index,
            Settings settings,
            boolean currentlyInvested) {
        if (settings.strategy() == Strategy.MOVING_AVERAGE) {
            if (index + 1 < settings.slowPeriod()) {
                return false;
            }
            BigDecimal fast = averageClose(candles, index, settings.fastPeriod());
            BigDecimal slow = averageClose(candles, index, settings.slowPeriod());
            return fast.compareTo(slow) > 0;
        }
        if (index < settings.rsiPeriod()) {
            return false;
        }
        double rsi = rsi(candles, index, settings.rsiPeriod());
        if (!currentlyInvested && rsi <= settings.oversold()) {
            return true;
        }
        if (currentlyInvested && rsi >= settings.overbought()) {
            return false;
        }
        return currentlyInvested;
    }

    private BigDecimal averageClose(List<BacktestCandle> candles, int end, int period) {
        BigDecimal total = BigDecimal.ZERO;
        for (int index = end - period + 1; index <= end; index++) {
            total = total.add(candles.get(index).close(), MC);
        }
        return total.divide(BigDecimal.valueOf(period), MC);
    }

    private double rsi(List<BacktestCandle> candles, int end, int period) {
        double gains = 0;
        double losses = 0;
        for (int index = end - period + 1; index <= end; index++) {
            double change = candles.get(index).close()
                    .subtract(candles.get(index - 1).close())
                    .doubleValue();
            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }
        if (losses == 0) {
            return 100;
        }
        return 100 - (100 / (1 + gains / losses));
    }

    private Settings validateAndApplyDefaults(
            BacktestRequest request,
            boolean generatedHistory) {
        if (generatedHistory
                && (request.startDate().isBefore(HistoricalDataService.EARLIEST_DATE)
                || request.endDate().isAfter(HistoricalDataService.LATEST_DATE))) {
            throw new BadRequestException("Choose dates within the available historical range.");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("The start date must be before the end date.");
        }
        if (request.startDate().plusYears(MAX_RANGE_YEARS).isBefore(request.endDate())) {
            throw new BadRequestException("A backtest can cover at most 10 years.");
        }
        Strategy strategy = Strategy.from(request.strategy());
        int fast = request.fastPeriod() == null ? 20 : request.fastPeriod();
        int slow = request.slowPeriod() == null ? 50 : request.slowPeriod();
        if (strategy == Strategy.MOVING_AVERAGE && fast >= slow) {
            throw new BadRequestException("The fast average must be shorter than the slow average.");
        }
        int oversold = request.oversold() == null ? 30 : request.oversold();
        int overbought = request.overbought() == null ? 70 : request.overbought();
        if (strategy == Strategy.RSI && oversold >= overbought) {
            throw new BadRequestException("The RSI oversold level must be below overbought.");
        }
        BigDecimal feeBps =
                request.feeRateBps() == null ? new BigDecimal("1.0") : request.feeRateBps();
        return new Settings(
                strategy,
                feeBps.divide(TEN_THOUSAND, MC),
                fast,
                slow,
                request.rsiPeriod() == null ? 14 : request.rsiPeriod(),
                oversold,
                overbought);
    }

    private BacktestExecution execution(
            BacktestCandle candle,
            String side,
            BigDecimal quantity,
            BigDecimal fee,
            String reason) {
        return new BacktestExecution(
                candle.date(),
                side,
                candle.open(),
                quantity,
                money(fee),
                reason);
    }

    private BigDecimal percentage(BigDecimal amount, BigDecimal basis) {
        if (basis.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(ONE_HUNDRED, MC).divide(basis, MC);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal metric(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private enum Strategy {
        BUY_AND_HOLD(
                "BUY_AND_HOLD",
                "Buy on the first open and hold through the selected period."),
        MOVING_AVERAGE(
                "MOVING_AVERAGE",
                "Fast/slow moving-average crossover using closing prices."),
        RSI(
                "RSI",
                "RSI reversal entries and exits using closing prices.");

        private final String id;
        private final String description;

        Strategy(String id, String description) {
            this.id = id;
            this.description = description;
        }

        private static Strategy from(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new BadRequestException(
                        "Unknown strategy. Choose BUY_AND_HOLD, MOVING_AVERAGE, or RSI.");
            }
        }
    }

    private record Settings(
            Strategy strategy,
            BigDecimal feeRate,
            int fastPeriod,
            int slowPeriod,
            int rsiPeriod,
            int oversold,
            int overbought) {

        private int minimumCandles() {
            return switch (strategy) {
                case BUY_AND_HOLD -> 2;
                case MOVING_AVERAGE -> slowPeriod + 1;
                case RSI -> rsiPeriod + 2;
            };
        }
    }

    private record Simulation(
            BacktestMetrics metrics,
            List<EquityPoint> equityCurve,
            List<BacktestExecution> executions) {
    }
}
