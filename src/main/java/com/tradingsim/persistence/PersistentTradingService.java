package com.tradingsim.persistence;

import com.tradingsim.account.AccountMode;
import com.tradingsim.account.AccountView;
import com.tradingsim.account.ExecutedTrade;
import com.tradingsim.account.OrderResult;
import com.tradingsim.account.PlaceOrderRequest;
import com.tradingsim.account.PositionView;
import com.tradingsim.account.TradeSide;
import com.tradingsim.config.TradingSimulatorProperties;
import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockQuote;
import com.tradingsim.security.AppUser;
import com.tradingsim.security.UserService;
import com.tradingsim.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * Database-backed paper account used by authenticated web requests.
 */
@Service
public class PersistentTradingService {
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal COMPETITIVE_STARTING_CASH =
            new BigDecimal("250000.00");
    private final PaperAccountRepository accounts;
    private final PositionRepository positions;
    private final PersistedTradeRepository trades;
    private final UserService users;
    private final QuoteService quotes;
    private final ScheduledDepositService deposits;
    private final BigDecimal startingCash;

    public PersistentTradingService(
            PaperAccountRepository accounts,
            PositionRepository positions,
            PersistedTradeRepository trades,
            UserService users,
            QuoteService quotes,
            ScheduledDepositService deposits,
            TradingSimulatorProperties properties) {
        this.accounts = accounts;
        this.positions = positions;
        this.trades = trades;
        this.users = users;
        this.quotes = quotes;
        this.deposits = deposits;
        this.startingCash = money(properties.startingCash());
    }

    @Transactional
    public OrderResult placeMarketOrder(String email, PlaceOrderRequest request) {
        AppUser owner = users.require(email);
        PaperAccountEntity account = lockedAccount(owner);
        if (account.getMode() != AccountMode.COMPETITIVE) {
            throw new BadRequestException(
                    "Switch to Competitive mode before placing day-trading orders.");
        }
        StockQuote quote = quotes.getQuote(request.symbol());
        PlaceOrderRequest normalized = new PlaceOrderRequest(
                quote.symbol(), request.side(), request.quantity());
        return executeOrder(
                email, owner, account, normalized, quote.currentPrice(),
                Map.of(quote.symbol(), quote.currentPrice()));
    }

    @Transactional
    public OrderResult placeGeneratedMarketOrder(
            String email,
            PlaceOrderRequest request,
            BigDecimal price,
            Map<String, BigDecimal> currentPrices) {
        AppUser owner = users.require(email);
        PaperAccountEntity account = lockedAccount(owner);
        if (account.getMode() != AccountMode.COMPETITIVE) {
            throw new BadRequestException(
                    "Switch to Competitive mode before placing day-trading orders.");
        }
        return executeOrder(email, owner, account, request, price, currentPrices);
    }

    private OrderResult executeOrder(
            String email,
            AppUser owner,
            PaperAccountEntity account,
            PlaceOrderRequest request,
            BigDecimal price,
            Map<String, BigDecimal> currentPrices) {
        BigDecimal total = money(price.multiply(BigDecimal.valueOf(request.quantity())));
        PositionEntity position = positions.findByAccountAndSymbol(account, request.symbol())
                .orElseGet(() -> new PositionEntity(account, request.symbol()));

        if (request.side() == TradeSide.BUY) {
            if (account.getCash().compareTo(total) < 0) {
                throw new BadRequestException("Not enough cash for this order.");
            }
            BigDecimal currentCost = position.getAveragePrice()
                    .multiply(BigDecimal.valueOf(position.getQuantity()));
            int quantity = position.getQuantity() + request.quantity();
            position.setAveragePrice(currentCost.add(total)
                    .divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP));
            position.setQuantity(quantity);
            account.setCash(money(account.getCash().subtract(total)));
        } else {
            if (position.getQuantity() < request.quantity()) {
                throw new BadRequestException("The account does not own enough shares.");
            }
            BigDecimal profit = price.subtract(position.getAveragePrice())
                    .multiply(BigDecimal.valueOf(request.quantity()));
            position.setQuantity(position.getQuantity() - request.quantity());
            position.setRealizedProfitLoss(money(
                    position.getRealizedProfitLoss().add(profit)));
            account.setRealizedProfitLoss(money(
                    account.getRealizedProfitLoss().add(profit)));
            account.setCash(money(account.getCash().add(total)));
        }

        accounts.save(account);
        positions.save(position);
        PersistedTrade saved = trades.save(new PersistedTrade(
                owner,
                request.symbol(),
                request.side(),
                request.quantity(),
                price,
                total));
        return new OrderResult(toTrade(saved), accountAtPrices(email, currentPrices));
    }

    @Transactional
    public AccountView account(String email) {
        return accountView(email, Map.of(), true);
    }

    @Transactional
    public AccountView accountAtPrices(String email, Map<String, BigDecimal> currentPrices) {
        return accountView(email, currentPrices, false);
    }

    private AccountView accountView(
            String email,
            Map<String, BigDecimal> currentPrices,
            boolean useLiveQuotes) {
        AppUser owner = users.require(email);
        // The lock keeps cash, positions, and recent trades on one consistent
        // side of any concurrently executing order or reset transaction.
        PaperAccountEntity account = lockedAccount(owner);
        Instant now = Instant.now();
        List<PositionView> views = new ArrayList<>();
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        for (PositionEntity position : positions.findByAccountOrderBySymbol(account)) {
            if (position.getQuantity() <= 0) {
                continue;
            }
            BigDecimal currentPrice = useLiveQuotes
                    ? quotes.getQuote(position.getSymbol()).currentPrice()
                    : currentPrices.getOrDefault(
                            position.getSymbol(), position.getAveragePrice());
            BigDecimal costBasis = money(position.getAveragePrice()
                    .multiply(BigDecimal.valueOf(position.getQuantity())));
            BigDecimal currentValue = money(currentPrice
                    .multiply(BigDecimal.valueOf(position.getQuantity())));
            BigDecimal pnl = money(currentValue.subtract(costBasis));
            BigDecimal percent = costBasis.signum() == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : pnl.multiply(BigDecimal.valueOf(100))
                            .divide(costBasis, 2, RoundingMode.HALF_UP);
            views.add(new PositionView(
                    position.getSymbol(),
                    position.getQuantity(),
                    position.getAveragePrice(),
                    costBasis,
                    currentPrice,
                    currentValue,
                    pnl,
                    percent,
                    position.getRealizedProfitLoss()));
            marketValue = marketValue.add(currentValue);
            unrealized = unrealized.add(pnl);
        }
        List<ExecutedTrade> recentTrades = trades.findTop50ByOwnerOrderByExecutedAtDesc(owner)
                .stream()
                .map(this::toTrade)
                .toList();
        return new AccountView(
                account.getMode(),
                account.getStartingCash(),
                account.getCash(),
                money(marketValue),
                money(account.getCash().add(marketValue)),
                money(unrealized),
                account.getRealizedProfitLoss(),
                account.getMode() == AccountMode.COMPETITIVE
                        ? ScheduledDepositService.AMOUNT
                        : null,
                account.getMode() == AccountMode.COMPETITIVE
                        ? deposits.nextDepositAt(account)
                        : null,
                account.getMode() == AccountMode.COMPETITIVE
                        && deposits.isAvailable(account, now),
                account.getTotalDeposits(),
                List.copyOf(views),
                recentTrades);
    }

    @Transactional
    public AccountView changeMode(String email, AccountMode mode) {
        AppUser owner = users.require(email);
        PaperAccountEntity account = lockedAccount(owner);
        if (account.getMode() == mode) {
            return account(email);
        }
        if (account.getMode() == AccountMode.COMPETITIVE
                && (!trades.findByOwnerOrderByExecutedAtAsc(owner).isEmpty()
                || account.getTotalDeposits().signum() > 0)) {
            throw new BadRequestException(
                    "Competitive mode is locked after trading or claiming a deposit.");
        }
        positions.deleteByAccount(account);
        trades.deleteByOwner(owner);
        account.reset(startingCashFor(mode), mode, Instant.now());
        accounts.save(account);
        return account(email);
    }

    @Transactional
    public AccountView claimDeposit(String email) {
        return claimDeposit(email, Map.of(), true);
    }

    @Transactional
    public AccountView claimDepositAtPrices(
            String email,
            Map<String, BigDecimal> currentPrices) {
        return claimDeposit(email, currentPrices, false);
    }

    private AccountView claimDeposit(
            String email,
            Map<String, BigDecimal> currentPrices,
            boolean useLiveQuotes) {
        AppUser owner = users.require(email);
        PaperAccountEntity account = lockedAccount(owner);
        if (account.getMode() != AccountMode.COMPETITIVE) {
            throw new BadRequestException("Deposits are only available in Competitive mode.");
        }
        if (!deposits.claim(account, Instant.now())) {
            throw new BadRequestException("The next deposit is not available yet.");
        }
        accounts.save(account);
        return accountView(email, currentPrices, useLiveQuotes);
    }

    private PaperAccountEntity account(AppUser owner) {
        return accounts.findByOwner(owner)
                .orElseGet(() -> accounts.save(new PaperAccountEntity(owner, startingCash)));
    }

    private PaperAccountEntity lockedAccount(AppUser owner) {
        return accounts.findForUpdateByOwner(owner)
                .orElseGet(() -> accounts.save(new PaperAccountEntity(owner, startingCash)));
    }

    private BigDecimal startingCashFor(AccountMode mode) {
        return mode == AccountMode.COMPETITIVE ? COMPETITIVE_STARTING_CASH : startingCash;
    }

    private ExecutedTrade toTrade(PersistedTrade trade) {
        return new ExecutedTrade(
                trade.getId(),
                trade.getSymbol(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getTotal(),
                trade.getExecutedAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
