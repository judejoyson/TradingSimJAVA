package com.tradingsim.replay;

import com.tradingsim.account.AccountMode;
import com.tradingsim.account.AccountView;
import com.tradingsim.account.OrderResult;
import com.tradingsim.account.PlaceOrderRequest;
import com.tradingsim.account.TradeSide;
import com.tradingsim.persistence.PersistentTradingService;
import com.tradingsim.security.AppUser;
import com.tradingsim.security.UserService;
import com.tradingsim.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class CompetitiveReplayService {
    private final ReplayDataService replayData;
    private final PersistentTradingService trading;
    private final UserService users;
    private final CompetitiveSessionRepository sessions;

    public CompetitiveReplayService(
            ReplayDataService replayData,
            PersistentTradingService trading,
            UserService users,
            CompetitiveSessionRepository sessions) {
        this.replayData = replayData;
        this.trading = trading;
        this.users = users;
        this.sessions = sessions;
    }

    @Transactional
    public CompetitiveSessionView start(
            String email,
            String symbol,
            String timeframe) {
        AppUser owner = users.requireForUpdate(email);
        SeededReplaySession generated =
                replayData.createSeededRandomSession(symbol, timeframe);
        ReplaySession replay = generated.replay();
        UUID sessionId = UUID.randomUUID();
        CompetitiveSessionEntity state = sessions.findForUpdateByOwner(owner)
                .orElseGet(() -> new CompetitiveSessionEntity(
                        owner,
                        sessionId,
                        replay.symbol(),
                        replay.timeframe(),
                        generated.seed(),
                        replay.initialBars()));
        AccountView account = trading.accountAtPrices(email, Map.of());
        if (account.mode() != AccountMode.COMPETITIVE) {
            throw new BadRequestException(
                    "Switch to Competitive mode before starting a session.");
        }
        if (!account.positions().isEmpty()) {
            throw new BadRequestException(
                    "Close your open position before generating a new simulation.");
        }
        state.replace(
                sessionId,
                replay.symbol(),
                replay.timeframe(),
                generated.seed(),
                replay.initialBars());
        sessions.save(state);

        return new CompetitiveSessionView(
                state.getSessionId(),
                visibleReplay(replay),
                state.getCursorPosition(),
                replay.candles().size(),
                state.getEntryQuantity(),
                state.isCompleted(),
                trading.accountAtPrices(email, currentPrices(state, replay)));
    }

    @Transactional
    public CompetitiveSessionStatus current(String email) {
        AppUser owner = users.require(email);
        CompetitiveSessionEntity state = sessions.findByOwner(owner).orElse(null);
        if (state == null) {
            return new CompetitiveSessionStatus(false, null);
        }
        ReplaySession replay = restore(state);
        return new CompetitiveSessionStatus(
                true,
                view(email, state, replay, state.getCursorPosition()));
    }

    @Transactional
    public CompetitiveAdvanceView advance(String email, UUID sessionId) {
        CompetitiveSessionEntity state = require(email, sessionId);
        ReplaySession replay = restore(state);
        if (state.getCursorPosition() >= replay.candles().size()) {
            return new CompetitiveAdvanceView(
                    currentCandle(state, replay),
                    state.getCursorPosition(),
                    true,
                    trading.accountAtPrices(email, currentPrices(state, replay)));
        }

        ReplayCandle candle = replay.candles().get(state.getCursorPosition());
        state.advance();
        return new CompetitiveAdvanceView(
                candle,
                state.getCursorPosition(),
                state.getCursorPosition() >= replay.candles().size(),
                trading.accountAtPrices(email, currentPrices(state, replay)));
    }

    @Transactional
    public OrderResult placeOrder(String email, CompetitiveOrderRequest request) {
        CompetitiveSessionEntity state = require(email, request.sessionId());
        ReplaySession replay = restore(state);
        validateOrder(state, request);
        ReplayCandle candle = currentCandle(state, replay);
        PlaceOrderRequest order = new PlaceOrderRequest(
                replay.symbol(),
                request.side(),
                request.quantity());
        OrderResult result = trading.placeGeneratedMarketOrder(
                email,
                order,
                candle.close(),
                currentPrices(state, replay));
        if (request.side() == TradeSide.BUY) {
            state.recordEntry(request.quantity());
        } else {
            state.complete();
        }
        return result;
    }

    @Transactional
    public AccountView account(String email) {
        AppUser owner = users.require(email);
        CompetitiveSessionEntity state = sessions.findByOwner(owner).orElse(null);
        ReplaySession replay = state == null ? null : restore(state);
        return trading.accountAtPrices(
                email,
                state == null ? Map.of() : currentPrices(state, replay));
    }

    @Transactional
    public AccountView claimDeposit(String email) {
        AppUser owner = users.require(email);
        CompetitiveSessionEntity state = sessions.findByOwner(owner).orElse(null);
        ReplaySession replay = state == null ? null : restore(state);
        return trading.claimDepositAtPrices(
                email,
                state == null ? Map.of() : currentPrices(state, replay));
    }

    private CompetitiveSessionEntity require(String email, UUID sessionId) {
        AppUser owner = users.requireForUpdate(email);
        CompetitiveSessionEntity state = sessions.findForUpdateByOwner(owner).orElse(null);
        if (state == null || !state.getSessionId().equals(sessionId)) {
            throw new BadRequestException(
                    "This Competitive session has expired. Start a new simulation.");
        }
        return state;
    }

    private ReplaySession restore(CompetitiveSessionEntity state) {
        return replayData.createRandomSession(
                state.getSymbol(),
                state.getTimeframe(),
                state.getRandomSeed());
    }

    private void validateOrder(
            CompetitiveSessionEntity state,
            CompetitiveOrderRequest request) {
        if (state.isCompleted()) {
            throw new BadRequestException(
                    "This Competitive trade is complete. Start a new simulation.");
        }
        if (request.side() == TradeSide.BUY && state.getEntryQuantity() != 0) {
            throw new BadRequestException(
                    "This simulation already has an open Competitive trade.");
        }
        if (request.side() == TradeSide.SELL
                && request.quantity() != state.getEntryQuantity()) {
            throw new BadRequestException(
                    "The Competitive exit must close the entire planned position.");
        }
    }

    private ReplayCandle currentCandle(
            CompetitiveSessionEntity state,
            ReplaySession replay) {
        return replay.candles().get(Math.max(0, state.getCursorPosition() - 1));
    }

    private Map<String, BigDecimal> currentPrices(
            CompetitiveSessionEntity state,
            ReplaySession replay) {
        return Map.of(replay.symbol(), currentCandle(state, replay).close());
    }

    private ReplaySession visibleReplay(ReplaySession replay) {
        return visibleReplay(replay, replay.initialBars());
    }

    private ReplaySession visibleReplay(ReplaySession replay, int cursor) {
        return new ReplaySession(
                replay.market(),
                replay.marketLabel(),
                replay.symbol(),
                replay.instrumentName(),
                replay.timeframe(),
                replay.timeframeMinutes(),
                replay.pricePrecision(),
                replay.initialBars(),
                replay.executionProfile(),
                replay.candles().subList(0, Math.min(cursor, replay.candles().size())));
    }

    private CompetitiveSessionView view(
            String email,
            CompetitiveSessionEntity state,
            ReplaySession replay,
            int cursor) {
        return new CompetitiveSessionView(
                state.getSessionId(),
                visibleReplay(replay, cursor),
                cursor,
                replay.candles().size(),
                state.getEntryQuantity(),
                state.isCompleted(),
                trading.accountAtPrices(email, currentPrices(state, replay)));
    }
}
