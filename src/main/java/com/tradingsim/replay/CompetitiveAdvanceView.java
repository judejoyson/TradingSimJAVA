package com.tradingsim.replay;

import com.tradingsim.account.AccountView;

public record CompetitiveAdvanceView(
        ReplayCandle candle,
        int cursor,
        boolean finished,
        AccountView account) {
}
