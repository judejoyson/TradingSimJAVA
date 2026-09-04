package com.tradingsim.persistence;

import com.tradingsim.account.AccountMode;
import com.tradingsim.account.TradeSide;
import com.tradingsim.security.AppUser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompetitiveLeaderboardServiceTest {
    private final PaperAccountRepository accounts = mock(PaperAccountRepository.class);
    private final PersistedTradeRepository trades = mock(PersistedTradeRepository.class);
    private final CompetitiveLeaderboardService leaderboards =
            new CompetitiveLeaderboardService(accounts, trades);

    @Test
    void ranksCompetitivePlayersByFundedReturn() {
        AppUser alex = new AppUser("alex@example.com", "hash", "Alex");
        PaperAccountEntity alexAccount = competitiveAccount(alex, "10000.00");
        PersistedTrade buy = new PersistedTrade(
                alex, "AAPL", TradeSide.BUY, 10,
                new BigDecimal("100.00"), new BigDecimal("1000.00"));
        PersistedTrade sell = new PersistedTrade(
                alex, "AAPL", TradeSide.SELL, 10,
                new BigDecimal("110.00"), new BigDecimal("1100.00"));

        AppUser blair = new AppUser("blair@example.com", "hash", "Blair");
        PaperAccountEntity blairAccount = competitiveAccount(blair, "5000.00");

        when(accounts.findByMode(AccountMode.COMPETITIVE))
                .thenReturn(List.of(blairAccount, alexAccount));
        when(trades.findByOwnerOrderByExecutedAtAsc(alex))
                .thenReturn(List.of(buy, sell));
        when(trades.findByOwnerOrderByExecutedAtAsc(blair))
                .thenReturn(List.of());

        List<LeaderboardEntry> result = leaderboards.leaderboard(LeaderboardMetric.RETURN);

        assertEquals("Alex", result.get(0).displayName());
        assertEquals(new BigDecimal("4.00"), result.get(0).returnPercent());
        assertEquals(new BigDecimal("100.00"), result.get(0).consistencyPercent());
        assertEquals(1, result.get(0).completedTrades());
        assertEquals("Blair", result.get(1).displayName());
    }

    private PaperAccountEntity competitiveAccount(AppUser owner, String realizedProfitLoss) {
        PaperAccountEntity account = new PaperAccountEntity(
                owner,
                new BigDecimal("100000.00"));
        account.reset(new BigDecimal("250000.00"), AccountMode.COMPETITIVE, Instant.now());
        account.setRealizedProfitLoss(new BigDecimal(realizedProfitLoss));
        return account;
    }
}
