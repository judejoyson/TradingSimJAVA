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

@Service
public final class FinnhubQuoteService implements QuoteService {
    private final RestClient restClient;
    private final TradingSimulatorProperties properties;
    private final Clock clock;
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();

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

        StockQuote quote = fetchQuote(symbol);
        cache.put(symbol, new CachedQuote(quote, now));
        return quote;
    }

    private StockQuote fetchQuote(String symbol) {
        String apiKey = properties.finnhub().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataException(
                    "FINNHUB_API_KEY is not configured. See the README setup instructions.");
        }

        try {
            FinnhubQuote response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quote")
                            .queryParam("symbol", symbol)
                            .queryParam("token", apiKey)
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

    private record CachedQuote(StockQuote quote, Instant fetchedAt) {
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
}
