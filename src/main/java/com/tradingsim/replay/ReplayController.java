package com.tradingsim.replay;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary used by the home and chart pages.
 *
 * <p>Validation and candle creation remain in services, leaving this class
 * responsible only for mapping URLs and query parameters.</p>
 */
@RestController
@RequestMapping("/api/replay")
public final class ReplayController {
    private final ReplayDataService replayDataService;

    public ReplayController(ReplayDataService replayDataService) {
        this.replayDataService = replayDataService;
    }

    @GetMapping("/options")
    public ReplayOptions options() {
        return replayDataService.options();
    }

    @GetMapping("/session")
    public ReplaySession session(
            @RequestParam String market,
            @RequestParam String symbol,
            @RequestParam String timeframe) {
        return replayDataService.createSession(market, symbol, timeframe);
    }
}
