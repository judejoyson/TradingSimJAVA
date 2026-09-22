package com.tradingsim.replay;

import java.util.List;

public record MarketOption(
        String id,
        String label,
        String description,
        List<InstrumentOption> instruments) {
}
