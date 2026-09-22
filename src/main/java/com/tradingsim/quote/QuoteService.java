package com.tradingsim.quote;

/**
 * Vendor-neutral current-quote lookup used by account valuation and trading.
 */
public interface QuoteService {
    StockQuote getQuote(String symbol);
}
