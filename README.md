# MarketLab stock day-trading simulator

This is a beginner-friendly paper-trading website. The browser uses plain HTML,
CSS, and JavaScript. A Java 17 Spring Boot server keeps the Finnhub key private,
retrieves current stock quotes, validates simulated market orders, and tracks an
in-memory account that starts with $100,000.

> This project uses Finnhub's documented API rather than scraping a finance
> website. Scraping is brittle because page HTML changes, and it can violate a
> website's terms. Always review a data provider's license before publishing an app.

## 1. Understand the request flow

```text
Browser (HTML/CSS/JavaScript)
       |
       | GET /api/quotes/AAPL or POST /api/orders
       v
TradingController (HTTP endpoints)
       |
       +--> FinnhubQuoteService --> Finnhub REST API
       |
       +--> TradingService --> PaperAccountService
                              (cash, shares, trade history)
```

The frontend and backend are served by the same Spring Boot application. This
avoids Cross-Origin Resource Sharing (CORS) setup and makes deployment simpler.
The API key never reaches the browser.

## 2. Install the tools

Install:

1. [JDK 17 or newer](https://adoptium.net/)
2. [Maven 3.9 or newer](https://maven.apache.org/download.cgi)
3. An editor such as IntelliJ IDEA Community Edition or Visual Studio Code
4. A free [Finnhub account and API key](https://finnhub.io/)

Confirm Java and Maven in PowerShell:

```powershell
java -version
mvn -version
```

## 3. Configure the live stock-data key

Set the key in the terminal that will start the app:

```powershell
$env:FINNHUB_API_KEY = "paste-your-key-here"
```

Do not put a real key in Git or JavaScript. `application.properties` reads the
environment variable with:

```properties
tradingsim.finnhub.api-key=${FINNHUB_API_KEY:}
```

The empty value after the colon lets the application start without a key, but
quote requests clearly report that configuration is missing.

## 4. Start the application

```powershell
mvn spring-boot:run
```

Open <http://localhost:8080>. Run the tests with:

```powershell
mvn test
```

Spring Boot compiles Java, starts an embedded Tomcat web server, and serves files
from `src/main/resources/static`.

## 5. Learn the project one layer at a time

### Build configuration: `pom.xml`

Maven reads this file. The Spring Boot parent selects compatible dependency
versions. `spring-boot-starter-web` supplies REST controllers, JSON conversion,
and embedded Tomcat. `spring-boot-starter-validation` validates incoming orders.
`spring-boot-starter-test` supplies JUnit and Spring testing tools.

### Application entry point: `TradingSimulatorApp.java`

`main` calls `SpringApplication.run`. `@SpringBootApplication` tells Spring to
find classes annotated with `@Service` and `@RestController`, instantiate them,
and connect their constructor parameters.

### Configuration: `TradingSimulatorProperties.java`

This record maps every `tradingsim.*` property from `application.properties` to
typed Java values. Change `tradingsim.starting-cash` to alter the initial balance,
or `tradingsim.quote-cache-duration` to alter the cache time.

### Market data: `quote/`

`QuoteService` is an interface: the rest of the application asks it for a quote
without knowing which vendor supplies it. `FinnhubQuoteService` implements that
interface with Spring's `RestClient`.

Finnhub returns short JSON fields such as `c` (current price) and `dp` (percent
change). The private `FinnhubQuote` record maps those fields and converts them to
the clearer public `StockQuote` shape returned to the browser.

Quotes are cached for 10 seconds. Without this cache, every browser refresh could
consume another Finnhub request and quickly exceed a free account's rate limit.
This is near-real-time polling, not exchange-grade streaming data.

### Trading account: `account/`

`PaperAccountService` owns cash, positions, and recent trades. Its important rules:

1. A buy fails if the account does not have enough simulated cash.
2. A sell fails if the account does not own enough shares; short selling is off.
3. Market orders fill at the latest Finnhub current price.
4. Average cost is recalculated after buys.
5. Realized profit/loss is recorded after sells.
6. `synchronized` protects the in-memory account from simultaneous web requests.

`TradingService` coordinates a trade: it obtains a current quote first and then
passes the price to the account.

### Web API: `web/`

`TradingController` exposes:

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/quotes/{symbol}` | Get one current quote |
| `GET` | `/api/account` | Get cash, positions, and trades |
| `POST` | `/api/orders` | Execute a simulated market order |
| `POST` | `/api/account/reset` | Reset the paper account |

An order request looks like:

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "quantity": 10
}
```

`ApiExceptionHandler` turns validation, balance, position, and provider errors
into useful JSON instead of exposing Java stack traces.

### Browser interface: `static/`

`index.html` defines the semantic page structure. `styles.css` controls layout,
colors, responsiveness, and component states. `app.js`:

1. Calls the backend with `fetch`.
2. Refreshes watchlist prices every 15 seconds.
3. Stores watchlist symbols in browser `localStorage`.
4. Sends order JSON to `/api/orders`.
5. Re-renders account data from each server response.

The browser never calls Finnhub directly. If it did, anyone could inspect the
page and steal the API key.

## 6. Try the API without the website

With the app running:

```powershell
Invoke-RestMethod http://localhost:8080/api/quotes/AAPL

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/orders `
  -ContentType "application/json" `
  -Body '{"symbol":"AAPL","side":"BUY","quantity":10}'
```

Testing the API separately helps determine whether a bug is in Java or JavaScript.

## 7. Important limitations

- State is in memory. Restarting the server resets the account.
- There is one shared account for every browser user.
- Orders use a current quote, not a real exchange order book, bid/ask spread,
  slippage, fees, latency, halts, or partial fills.
- Finnhub's free plan can be delayed, rate-limited, or restricted by exchange.
- This is educational software, not financial advice or a brokerage.

## 8. Safe next milestones

Build these in order so each step teaches one new concept:

1. Add unrealized P&L by marking positions with current quotes.
2. Store users, accounts, and trades in PostgreSQL with Spring Data JPA.
3. Add Spring Security login so each user has a separate account.
4. Model limit and stop orders plus trading fees and slippage.
5. Add historical candles and a chart library.
6. Add Finnhub WebSocket streaming if your data plan permits it.
7. Deploy the server and set `FINNHUB_API_KEY` in the host's secret manager.

The original event-driven CSV simulator remains under the existing
`engine`, `market`, `order`, `portfolio`, and `strategy` packages. It can later
power historical backtesting while this Spring Boot layer handles interactive
paper trading.