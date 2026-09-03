package com.tradingsim.account;

import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockQuote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates live quotes with the in-memory paper account.
 *
 * <p>Keeping quote lookup here prevents {@link PaperAccountService} from
 * depending on an external data provider.</p>
 */
@Service
public final class TradingService {
    private static final int MONEY_SCALE = 2;

    private final QuoteService quoteService;
    private final PaperAccountService accountService;

    @Autowired
    public TradingService(QuoteService quoteService, PaperAccountService accountService) {
        this.quoteService = quoteService;
        this.accountService = accountService;
    }

    public OrderResult placeMarketOrder(PlaceOrderRequest request) {
        StockQuote quote = quoteService.getQuote(request.symbol());
        OrderResult result = accountService.execute(
                quote.symbol(),
                request.side(),
                request.quantity(),
                quote.currentPrice());
        return new OrderResult(result.trade(), account());
    }

    /**
     * Values every open position at its latest quote and calculates account
     * equity without mutating the underlying accounting records.
     */
    public AccountView account() {
        AccountView account = accountService.snapshot();
        List<PositionView> valuedPositions = new ArrayList<>();
        BigDecimal positionsMarketValue = BigDecimal.ZERO;
        BigDecimal unrealizedProfitLoss = BigDecimal.ZERO;

        for (PositionView position : account.positions()) {
            StockQuote quote = quoteService.getQuote(position.symbol());
            BigDecimal marketValue = money(quote.currentPrice()
                    .multiply(BigDecimal.valueOf(position.quantity())));
            BigDecimal positionProfitLoss = money(marketValue.subtract(position.costBasis()));
            // Percentage return is measured against the money originally
            // invested in this position, not total account cash.
            BigDecimal positionProfitLossPercent = position.costBasis().signum() == 0
                    ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                    : positionProfitLoss
                            .multiply(BigDecimal.valueOf(100))
                            .divide(position.costBasis(), MONEY_SCALE, RoundingMode.HALF_UP);

            valuedPositions.add(new PositionView(
                    position.symbol(),
                    position.quantity(),
                    position.averagePrice(),
                    position.costBasis(),
                    quote.currentPrice(),
                    marketValue,
                    positionProfitLoss,
                    positionProfitLossPercent,
                    position.realizedProfitLoss()));
            positionsMarketValue = positionsMarketValue.add(marketValue);
            unrealizedProfitLoss = unrealizedProfitLoss.add(positionProfitLoss);
        }

        return new AccountView(
                account.startingCash(),
                account.cash(),
                money(positionsMarketValue),
                money(account.cash().add(positionsMarketValue)),
                money(unrealizedProfitLoss),
                account.realizedProfitLoss(),
                List.copyOf(valuedPositions),
                account.recentTrades());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
