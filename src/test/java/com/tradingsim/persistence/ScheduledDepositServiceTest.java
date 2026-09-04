package com.tradingsim.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledDepositServiceTest {
    private final ScheduledDepositService deposits = new ScheduledDepositService();

    @Test
    void creditsOnlyOneDepositAndRestartsTimerWhenClaimed() {
        PaperAccountEntity account = new PaperAccountEntity(
                null,
                new BigDecimal("250000.00"));
        Instant startedAt = Instant.parse("2026-01-01T10:00:00Z");
        account.setLastDepositAt(startedAt);

        boolean credited = deposits.claim(
                account,
                startedAt.plusSeconds(3 * 90 * 60 + 30));

        assertTrue(credited);
        assertEquals(new BigDecimal("325000.00"), account.getCash());
        assertEquals(new BigDecimal("75000.00"), account.getTotalDeposits());
        assertEquals(
                Instant.parse("2026-01-01T14:30:30Z"),
                account.getLastDepositAt());
        assertEquals(
                Instant.parse("2026-01-01T16:00:30Z"),
                deposits.nextDepositAt(account));
    }

    @Test
    void doesNotCreditBeforeIntervalCompletes() {
        PaperAccountEntity account = new PaperAccountEntity(
                null,
                new BigDecimal("250000.00"));
        Instant startedAt = Instant.parse("2026-01-01T10:00:00Z");
        account.setLastDepositAt(startedAt);

        boolean credited = deposits.claim(
                account,
                startedAt.plusSeconds(90 * 60 - 1));

        assertFalse(credited);
        assertEquals(new BigDecimal("250000.00"), account.getCash());
    }
}
