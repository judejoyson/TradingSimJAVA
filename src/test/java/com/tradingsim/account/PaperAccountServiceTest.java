package com.tradingsim.account;

import com.tradingsim.web.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperAccountServiceTest {
    private final PaperAccountService account = new PaperAccountService(
            new BigDecimal("10000"),
            Clock.fixed(Instant.parse("2026-01-02T15:30:00Z"), ZoneOffset.UTC));

    @Test
    void buysAndSellsSharesAndCalculatesRealizedProfit() {
        account.execute("aapl", TradeSide.BUY, 10, new BigDecimal("100"));
        OrderResult sale = account.execute("AAPL", TradeSide.SELL, 4, new BigDecimal("110"));

        assertEquals(new BigDecimal("9440.00"), sale.account().cash());
        assertEquals(new BigDecimal("40.00"), sale.account().realizedProfitLoss());
        assertEquals(6, sale.account().positions().get(0).quantity());
        assertEquals(2, sale.account().recentTrades().size());
    }

    @Test
    void rejectsOrdersThatExceedCashOrOwnedShares() {
        assertThrows(
                BadRequestException.class,
                () -> account.execute("AAPL", TradeSide.BUY, 101, new BigDecimal("100")));
        assertThrows(
                BadRequestException.class,
                () -> account.execute("AAPL", TradeSide.SELL, 1, new BigDecimal("100")));
    }

    @Test
    void calculatesNotionalBeforeRoundingToCents() {
        OrderResult result = account.execute(
                "AAPL",
                TradeSide.BUY,
                100,
                new BigDecimal("10.005"));

        assertEquals(new BigDecimal("8999.50"), result.account().cash());
        assertEquals(new BigDecimal("1000.50"), result.trade().total());
    }
}
