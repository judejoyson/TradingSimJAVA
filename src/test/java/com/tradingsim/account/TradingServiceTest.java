package com.tradingsim.account;

import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TradingServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-02T15:30:00Z");

    @Test
    void marksPositionsToMarketAndCalculatesUnrealizedProfit() {
        PaperAccountService accountService = new PaperAccountService(
                new BigDecimal("10000"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        accountService.execute("AAPL", TradeSide.BUY, 10, new BigDecimal("100"));
        QuoteService quotes = symbol -> quote(symbol, "120");
        TradingService tradingService = new TradingService(quotes, accountService);

        AccountView account = tradingService.account();
        PositionView position = account.positions().get(0);

        assertEquals(new BigDecimal("1200.00"), account.positionsMarketValue());
        assertEquals(new BigDecimal("10200.00"), account.totalEquity());
        assertEquals(new BigDecimal("200.00"), account.unrealizedProfitLoss());
        assertEquals(new BigDecimal("120"), position.currentPrice());
        assertEquals(new BigDecimal("20.00"), position.unrealizedProfitLossPercent());
    }

    private StockQuote quote(String symbol, String price) {
        BigDecimal value = new BigDecimal(price);
        return new StockQuote(
                symbol,
                value,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                value,
                value,
                value,
                value,
                NOW);
    }
}
