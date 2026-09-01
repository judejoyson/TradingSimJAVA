package com.tradingsim.web;

import com.tradingsim.account.AccountView;
import com.tradingsim.account.OrderResult;
import com.tradingsim.account.PaperAccountService;
import com.tradingsim.account.PlaceOrderRequest;
import com.tradingsim.account.TradingService;
import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockDetails;
import com.tradingsim.quote.StockDetailsService;
import com.tradingsim.quote.StockQuote;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public final class TradingController {
    private final QuoteService quoteService;
    private final StockDetailsService stockDetailsService;
    private final PaperAccountService accountService;
    private final TradingService tradingService;

    public TradingController(
            QuoteService quoteService,
            StockDetailsService stockDetailsService,
            PaperAccountService accountService,
            TradingService tradingService) {
        this.quoteService = quoteService;
        this.stockDetailsService = stockDetailsService;
        this.accountService = accountService;
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
    public AccountView account() {
        return tradingService.account();
    }

    @PostMapping("/orders")
    public OrderResult placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return tradingService.placeMarketOrder(request);
    }

    @PostMapping("/account/reset")
    public AccountView resetAccount() {
        return accountService.reset();
    }
}
