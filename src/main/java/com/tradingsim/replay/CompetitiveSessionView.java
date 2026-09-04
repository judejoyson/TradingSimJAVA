package com.tradingsim.replay;

import com.tradingsim.account.AccountView;

import java.util.UUID;

public record CompetitiveSessionView(
        UUID sessionId,
        ReplaySession replay,
        int cursor,
        int totalCandles,
        int entryQuantity,
        boolean completed,
        AccountView account) {
}
