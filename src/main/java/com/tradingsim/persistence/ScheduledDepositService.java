package com.tradingsim.persistence;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Handles the single allowance that becomes claimable after each interval.
 */
@Service
public class ScheduledDepositService {
    public static final Duration INTERVAL = Duration.ofMinutes(90);
    public static final BigDecimal AMOUNT = new BigDecimal("75000.00");

    public boolean claim(PaperAccountEntity account, Instant now) {
        if (!isAvailable(account, now)) {
            return false;
        }
        account.setCash(money(account.getCash().add(AMOUNT)));
        account.setTotalDeposits(money(account.getTotalDeposits().add(AMOUNT)));
        // Claim time starts the next interval. Old, unclaimed intervals do not
        // create extra deposits.
        account.setLastDepositAt(now);
        return true;
    }

    public boolean isAvailable(PaperAccountEntity account, Instant now) {
        return !now.isBefore(nextDepositAt(account));
    }

    public Instant nextDepositAt(PaperAccountEntity account) {
        return account.getLastDepositAt().plus(INTERVAL);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
