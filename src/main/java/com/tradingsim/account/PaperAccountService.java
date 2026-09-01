package com.tradingsim.account;

import com.tradingsim.config.TradingSimulatorProperties;
import com.tradingsim.quote.Symbols;
import com.tradingsim.web.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public final class PaperAccountService {
    private static final int MONEY_SCALE = 2;
    private static final int MAX_RECENT_TRADES = 50;

    private final BigDecimal startingCash;
    private final Clock clock;
    private final Map<String, Position> positions = new HashMap<>();
    private final List<ExecutedTrade> trades = new ArrayList<>();
    private BigDecimal cash;
    private BigDecimal realizedProfitLoss = BigDecimal.ZERO.setScale(MONEY_SCALE);
    private long nextTradeId = 1;

    @Autowired
    public PaperAccountService(TradingSimulatorProperties properties) {
        this(properties.startingCash(), Clock.systemUTC());
    }

    PaperAccountService(BigDecimal startingCash, Clock clock) {
        if (startingCash == null || startingCash.signum() <= 0) {
            throw new IllegalArgumentException("Starting cash must be positive");
        }
        this.startingCash = money(startingCash);
        this.cash = this.startingCash;
        this.clock = clock;
    }

    public synchronized OrderResult execute(
            String requestedSymbol,
            TradeSide side,
            int quantity,
            BigDecimal currentPrice) {
        String symbol = Symbols.normalize(requestedSymbol);
        if (side == null) {
            throw new BadRequestException("Order side is required.");
        }
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be positive.");
        }
        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new BadRequestException("A positive execution price is required.");
        }

        BigDecimal executionPrice = currentPrice;
        BigDecimal total = money(currentPrice.multiply(BigDecimal.valueOf(quantity)));
        if (total.signum() <= 0) {
            throw new BadRequestException("The order total is too small to execute.");
        }
        if (side == TradeSide.BUY) {
            buy(symbol, quantity, total);
        } else {
            sell(symbol, quantity, executionPrice, total);
        }

        ExecutedTrade trade = new ExecutedTrade(
                nextTradeId++,
                symbol,
                side,
                quantity,
                executionPrice,
                total,
                clock.instant());
        trades.add(0, trade);
        if (trades.size() > MAX_RECENT_TRADES) {
            trades.remove(trades.size() - 1);
        }
        return new OrderResult(trade, snapshot());
    }

    public synchronized AccountView snapshot() {
        List<PositionView> positionViews = positions.entrySet().stream()
                .filter(entry -> entry.getValue().quantity > 0)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toView(entry.getKey()))
                .toList();
        BigDecimal positionsCost = positionViews.stream()
                .map(PositionView::costBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountView(
                startingCash,
                cash,
                money(positionsCost),
                money(cash.add(positionsCost)),
                money(BigDecimal.ZERO),
                realizedProfitLoss,
                positionViews,
                List.copyOf(trades));
    }

    public synchronized AccountView reset() {
        cash = startingCash;
        realizedProfitLoss = BigDecimal.ZERO.setScale(MONEY_SCALE);
        positions.clear();
        trades.clear();
        nextTradeId = 1;
        return snapshot();
    }

    private void buy(String symbol, int quantity, BigDecimal total) {
        if (cash.compareTo(total) < 0) {
            throw new BadRequestException(
                    "Not enough cash. This order costs " + total + " but only " + cash + " is available.");
        }
        Position position = positions.computeIfAbsent(symbol, ignored -> new Position());
        BigDecimal existingCost = position.averagePrice
                .multiply(BigDecimal.valueOf(position.quantity));
        int newQuantity = position.quantity + quantity;
        position.averagePrice = existingCost
                .add(total)
                .divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP);
        position.quantity = newQuantity;
        cash = money(cash.subtract(total));
    }

    private void sell(String symbol, int quantity, BigDecimal price, BigDecimal total) {
        Position position = positions.get(symbol);
        int ownedQuantity = position == null ? 0 : position.quantity;
        if (ownedQuantity < quantity) {
            throw new BadRequestException(
                    "Cannot sell " + quantity + " " + symbol + " shares; the account owns "
                            + ownedQuantity + ".");
        }
        BigDecimal profitLoss = price
                .subtract(position.averagePrice)
                .multiply(BigDecimal.valueOf(quantity));
        realizedProfitLoss = money(realizedProfitLoss.add(profitLoss));
        position.realizedProfitLoss = money(position.realizedProfitLoss.add(profitLoss));
        position.quantity -= quantity;
        cash = money(cash.add(total));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static final class Position {
        private int quantity;
        private BigDecimal averagePrice = BigDecimal.ZERO;
        private BigDecimal realizedProfitLoss = BigDecimal.ZERO.setScale(MONEY_SCALE);

        private PositionView toView(String symbol) {
            return new PositionView(
                    symbol,
                    quantity,
                    averagePrice,
                    money(averagePrice.multiply(BigDecimal.valueOf(quantity))),
                    averagePrice,
                    money(averagePrice.multiply(BigDecimal.valueOf(quantity))),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    realizedProfitLoss);
        }
    }
}
