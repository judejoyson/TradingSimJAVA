package com.tradingsim.persistence;

import com.tradingsim.config.TradingSimulatorProperties;
import com.tradingsim.security.AppUser;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;

/**
 * Creates the account row that later acts as the portfolio mutation lock.
 */
@Service
public class PortfolioProvisioningService {
    private final PaperAccountRepository accounts;
    private final TradingSimulatorProperties properties;

    public PortfolioProvisioningService(
            PaperAccountRepository accounts,
            TradingSimulatorProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    public void createFor(AppUser owner) {
        accounts.save(new PaperAccountEntity(
                owner,
                properties.startingCash().setScale(2, RoundingMode.HALF_UP)));
    }
}
