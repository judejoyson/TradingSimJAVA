package com.tradingsim.replay;

import java.util.List;

public record ReplayOptions(
        List<MarketOption> markets,
        List<TimeframeOption> timeframes) {
}
