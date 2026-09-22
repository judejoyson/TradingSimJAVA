package com.tradingsim.quote;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradingsim.config.TradingSimulatorProperties;
import com.tradingsim.web.MarketDataException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finnhub-backed implementation of current quotes and company details.
 *
 * <p>Short-lived caches reduce external requests and protect free-plan rate
 * limits. A concurrent map is used because many web requests may arrive at
 * once. The API key is read server-side and never sent to browser JavaScript.</p>
 */
@Service
public final class FinnhubQuoteService implements QuoteService, StockDetailsService {
    private final RestClient restClient;
    private final TradingSimulatorProperties properties;
    private final Clock clock;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedDetails> detailsCache = new ConcurrentHashMap<>();

    @Autowired
    public FinnhubQuoteService(
            RestClient.Builder restClientBuilder,
            TradingSimulatorProperties properties) {
        this(restClientBuilder, properties, Clock.systemUTC());
    }

    FinnhubQuoteService(
            RestClient.Builder restClientBuilder,
            TradingSimulatorProperties properties,
            Clock clock) {
        this.restClient = restClientBuilder
                .baseUrl(properties.finnhub().baseUrl())
                .build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public StockQuote getQuote(String requestedSymbol) {
        String symbol = Symbols.normalize(requestedSymbol);
        CachedQuote cached = cache.get(symbol);
        Instant now = clock.instant();
        if (cached != null
                && cached.fetchedAt().plus(properties.quoteCacheDuration()).isAfter(now)) {
            return cached.quote();
        }

        // Cache only successful responses so provider errors remain visible
        // instead of looking like valid market data.
        StockQuote quote = fetchQuote(symbol);
        cache.put(symbol, new CachedQuote(quote, now));
        return quote;
    }

    @Override
    public StockDetails getDetails(String requestedSymbol) {
        String symbol = Symbols.normalize(requestedSymbol);
        Instant now = clock.instant();
        CachedDetails cached = detailsCache.get(symbol);
        if (cached != null && cached.fetchedAt().plusSeconds(60).isAfter(now)) {
            return cached.details();
        }

        StockDetails details = new StockDetails(
                getQuote(symbol),
                fetchProfile(symbol));
        detailsCache.put(symbol, new CachedDetails(details, now));
        return details;
    }

    private StockQuote fetchQuote(String symbol) {
        try {
            FinnhubQuote response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey())
                            .build())
                    .retrieve()
                    .body(FinnhubQuote.class);
            if (response == null || response.currentPrice() == null
                    || response.currentPrice().signum() <= 0) {
                throw new MarketDataException(
                        "Finnhub returned no current price for symbol " + symbol + ".");
            }
            return response.toStockQuote(symbol);
        } catch (RestClientException exception) {
            throw new MarketDataException(
                    "Finnhub could not provide a quote for " + symbol + ".", exception);
        }
    }

    private CompanyProfile fetchProfile(String symbol) {
        try {
            FinnhubProfile response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock/profile2")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey())
                            .build())
                    .retrieve()
                    .body(FinnhubProfile.class);
            if (response == null || response.name() == null || response.name().isBlank()) {
                throw new MarketDataException(
                        "Finnhub returned no company profile for symbol " + symbol + ".");
            }
            return response.toCompanyProfile();
        } catch (RestClientException exception) {
            throw new MarketDataException(
                    "Finnhub could not provide company details for " + symbol + ".", exception);
        }
    }

    private String apiKey() {
        String apiKey = properties.finnhub().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataException(
                    "FINNHUB_API_KEY is not configured. See the README setup instructions.");
        }
        return apiKey;
    }

    private record CachedQuote(StockQuote quote, Instant fetchedAt) {
    }

    private record CachedDetails(StockDetails details, Instant fetchedAt) {
    }

    private record FinnhubQuote(
            @JsonProperty("c") BigDecimal currentPrice,
            @JsonProperty("d") BigDecimal change,
            @JsonProperty("dp") BigDecimal percentChange,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("pc") BigDecimal previousClose,
            @JsonProperty("t") long timestamp) {

        private StockQuote toStockQuote(String symbol) {
            return new StockQuote(
                    symbol,
                    currentPrice,
                    change,
                    percentChange,
                    high,
                    low,
                    open,
                    previousClose,
                    Instant.ofEpochSecond(timestamp));
        }
    }

    private record FinnhubProfile(
            String country,
            String currency,
            String exchange,
            String finnhubIndustry,
            String ipo,
            String logo,
            BigDecimal marketCapitalization,
            String name,
            BigDecimal shareOutstanding,
            String ticker,
            String weburl) {

        private CompanyProfile toCompanyProfile() {
            return new CompanyProfile(
                    name,
                    ticker,
                    exchange,
                    finnhubIndustry,
                    country,
                    currency,
                    ipo,
                    logo,
                    weburl,
                    marketCapitalization,
                    shareOutstanding);
        }
    }

}
