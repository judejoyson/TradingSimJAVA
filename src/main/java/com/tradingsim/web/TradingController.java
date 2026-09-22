package com.tradingsim.web;

import com.tradingsim.account.AccountView;
import com.tradingsim.account.ChangeAccountModeRequest;
import com.tradingsim.persistence.PersistentTradingService;
import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockDetails;
import com.tradingsim.quote.StockDetailsService;
import com.tradingsim.quote.StockQuote;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy live paper-trading endpoints retained alongside ReplayLab.
 *
 * <p>The current two-page historical simulator uses
 * {@link com.tradingsim.replay.ReplayController}; these endpoints remain useful
 * if the live portfolio interface is restored later.</p>
 */
@RestController
@RequestMapping("/api")
public final class TradingController {
    private final QuoteService quoteService;
    private final StockDetailsService stockDetailsService;
    private final PersistentTradingService tradingService;

    public TradingController(
            QuoteService quoteService,
            StockDetailsService stockDetailsService,
            PersistentTradingService tradingService) {
        this.quoteService = quoteService;
        this.stockDetailsService = stockDetailsService;
        this.tradingService = tradingService;
    }

    @GetMapping("/quotes/{symbol}")
    public StockQuote quote(@PathVariable String symbol) {
        return quoteService.getQuote(symbol);
    }

    @GetMapping("/stocks/{symbol}")
    public StockDetails stockDetails(@PathVariable String symbol) {
        return stockDetailsService.getDetails(symbol);
    }

    @GetMapping("/account")
    public AccountView account(Authentication authentication) {
        return tradingService.account(authentication.getName());
    }

    @PostMapping("/account/mode")
    public AccountView changeAccountMode(
            @Valid @RequestBody ChangeAccountModeRequest request,
            Authentication authentication) {
        return tradingService.changeMode(authentication.getName(), request.mode());
    }

    @PostMapping("/account/deposit")
    public AccountView claimDeposit(Authentication authentication) {
        return tradingService.claimDeposit(authentication.getName());
    }
}
