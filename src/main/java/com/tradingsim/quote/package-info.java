/**
 * Live Finnhub quote and company-profile integration.
 *
 * <p>API keys stay on the Java server. The historical ReplayLab workflow does
 * not use today's live quotes because mixing present prices with historical
 * candles would make simulated P&amp;L invalid.</p>
 */
package com.tradingsim.quote;
