package com.tradingsim.replay;

import com.tradingsim.account.AccountView;
import com.tradingsim.account.OrderResult;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/competitive")
public class CompetitiveReplayController {
    private final CompetitiveReplayService replay;

    public CompetitiveReplayController(CompetitiveReplayService replay) {
        this.replay = replay;
    }

    @PostMapping("/session")
    public CompetitiveSessionView start(
            @RequestParam(defaultValue = "AAPL") String symbol,
            @RequestParam(defaultValue = "5m") String timeframe,
            Authentication authentication) {
        return replay.start(authentication.getName(), symbol, timeframe);
    }

    @GetMapping("/session")
    public CompetitiveSessionStatus current(Authentication authentication) {
        return replay.current(authentication.getName());
    }

    @PostMapping("/session/{sessionId}/advance")
    public CompetitiveAdvanceView advance(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        return replay.advance(authentication.getName(), sessionId);
    }

    @PostMapping("/orders")
    public OrderResult order(
            @Valid @RequestBody CompetitiveOrderRequest request,
            Authentication authentication) {
        return replay.placeOrder(authentication.getName(), request);
    }

    @GetMapping("/account")
    public AccountView account(Authentication authentication) {
        return replay.account(authentication.getName());
    }

    @PostMapping("/deposit")
    public AccountView claimDeposit(Authentication authentication) {
        return replay.claimDeposit(authentication.getName());
    }
}
