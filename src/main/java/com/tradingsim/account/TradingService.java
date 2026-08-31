package com.tradingsim.account;

import com.tradingsim.quote.QuoteService;
import com.tradingsim.quote.StockQuote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class TradingService {
    private final QuoteService quoteService;
    private final PaperAccountService accountService;

    @Autowired
    public TradingService(QuoteService quoteService, PaperAccountService accountService) {
        this.quoteService = quoteService;
        this.accountService = accountService;
    }

    public OrderResult placeMarketOrder(PlaceOrderRequest request) {
        StockQuote quote = quoteService.getQuote(request.symbol());
        return accountService.execute(
                quote.symbol(),
                request.side(),
                request.quantity(),
                quote.currentPrice());
    }
}
