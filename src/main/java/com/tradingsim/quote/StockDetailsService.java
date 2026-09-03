package com.tradingsim.quote;

/**
 * Vendor-neutral company profile and current quote lookup.
 */
public interface StockDetailsService {
    StockDetails getDetails(String symbol);
}
