package com.tradingsim.persistence;

import com.tradingsim.account.AccountMode;
import com.tradingsim.account.TradeSide;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompetitiveLeaderboardService {
    private final PaperAccountRepository accounts;
    private final PersistedTradeRepository trades;

    public CompetitiveLeaderboardService(
            PaperAccountRepository accounts,
            PersistedTradeRepository trades) {
        this.accounts = accounts;
        this.trades = trades;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(LeaderboardMetric metric) {
        Comparator<LeaderboardEntry> ranking = switch (metric) {
            case SHARPE -> Comparator.comparing(LeaderboardEntry::sharpeRatio);
            case CONSISTENCY -> Comparator.comparing(LeaderboardEntry::consistencyPercent);
            case RETURN -> Comparator.comparing(LeaderboardEntry::returnPercent);
        };
        List<LeaderboardEntry> entries = accounts.findByMode(AccountMode.COMPETITIVE)
                .stream()
                .map(this::score)
                .sorted(ranking.reversed()
                        .thenComparing(LeaderboardEntry::displayName))
                .toList();

        List<LeaderboardEntry> ranked = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            LeaderboardEntry entry = entries.get(index);
            ranked.add(new LeaderboardEntry(
                    index + 1,
                    entry.displayName(),
                    entry.returnPercent(),
                    entry.sharpeRatio(),
                    entry.consistencyPercent(),
                    entry.completedTrades()));
        }
        return List.copyOf(ranked);
    }

    private LeaderboardEntry score(PaperAccountEntity account) {
        List<BigDecimal> returns = closedTradeReturns(
                trades.findByOwnerOrderByExecutedAtAsc(account.getOwner()));
        BigDecimal fundedCash = account.getStartingCash().add(account.getTotalDeposits());
        BigDecimal returnPercent = percentage(account.getRealizedProfitLoss(), fundedCash);
        long profitableTrades = returns.stream()
                .filter(value -> value.signum() > 0)
                .count();
        BigDecimal consistency = returns.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(profitableTrades * 100.0 / returns.size())
                        .setScale(2, RoundingMode.HALF_UP);
        return new LeaderboardEntry(
                0,
                account.getOwner().getDisplayName(),
                returnPercent,
                sharpeRatio(returns),
                consistency,
                returns.size());
    }

    private List<BigDecimal> closedTradeReturns(List<PersistedTrade> history) {
        Map<String, Holding> holdings = new HashMap<>();
        List<BigDecimal> returns = new ArrayList<>();
        for (PersistedTrade trade : history) {
            Holding holding = holdings.computeIfAbsent(trade.getSymbol(), ignored -> new Holding());
            if (trade.getSide() == TradeSide.BUY) {
                BigDecimal existingCost = holding.averagePrice
                        .multiply(BigDecimal.valueOf(holding.quantity));
                int newQuantity = holding.quantity + trade.getQuantity();
                holding.averagePrice = existingCost.add(trade.getTotal())
                        .divide(BigDecimal.valueOf(newQuantity), 6, RoundingMode.HALF_UP);
                holding.quantity = newQuantity;
            } else if (holding.quantity >= trade.getQuantity()) {
                returns.add(percentage(trade.getPrice().subtract(holding.averagePrice),
                        holding.averagePrice));
                holding.quantity -= trade.getQuantity();
                if (holding.quantity == 0) {
                    holding.averagePrice = BigDecimal.ZERO;
                }
            }
        }
        return returns;
    }

    private BigDecimal sharpeRatio(List<BigDecimal> returns) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO.setScale(2);
        }
        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value.doubleValue() - mean, 2))
                .sum() / (returns.size() - 1);
        double deviation = Math.sqrt(variance);
        if (deviation == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(mean / deviation * Math.sqrt(returns.size()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal amount, BigDecimal basis) {
        if (basis.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(basis, 2, RoundingMode.HALF_UP);
    }

    private static final class Holding {
        private int quantity;
        private BigDecimal averagePrice = BigDecimal.ZERO;
    }
}
