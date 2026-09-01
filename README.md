# ReplayLab trading simulator

ReplayLab is a historical market-replay trainer built with Java 17, Spring Boot,
plain HTML/CSS/JavaScript, and TradingView Lightweight Charts. It reveals
candles one at a time so a learner can practice defining an entry, stop-loss,
and take-profit without risking money.

The app intentionally uses deterministic generated OHLCV data. Every market and
timeframe works without an API key, rate limit, paid data plan, or network call
to a market-data vendor.

## What the application does

ReplayLab has two pages:

1. **Home page:** Select an asset class, instrument, and candle timeframe.
2. **Chart page:** Place a long or short trade plan directly on a candlestick
   chart, replay hidden candles, track P&L, and review the result.

Available demo markets:

| Market | Instruments |
|---|---|
| U.S. Stocks | SPY, QQQ, AAPL |
| Forex | EUR/USD, GBP/USD, USD/JPY |
| Crypto | BTC/USD, ETH/USD, SOL/USD |

Every instrument supports 1-minute, 5-minute, 15-minute, and 1-hour candles.

## Run the project

Requirements:

- JDK 17 or newer
- Maven 3.9 or newer
- Internet access for the Lightweight Charts JavaScript file

Start Spring Boot:

```powershell
mvn spring-boot:run
```

Open <http://localhost:8080>.

Run all tests:

```powershell
mvn test
```

The replay portion does not require `FINNHUB_API_KEY`. Older live-quote API code
remains in the project for future paper-trading features, but the two-page replay
experience does not call it.

## How to use a replay

1. Choose a market, instrument, and timeframe on the home page.
2. Choose **Long** or **Short** and enter a position size.
3. Double-click the chart to set the entry price.
4. Double-click above and below entry to set the stop-loss and take-profit:
   - Long: target above entry, stop below entry.
   - Short: stop above entry, target below entry.
5. Press **Play**. The trade remains pending until a candle touches the entry.
6. Watch P&L update while the trade is open.
7. The replay stops when price hits the stop, target, or end of the session.
8. Review the result and replay the same data or select another market.

If one candle touches both the stop and target, ReplayLab chooses the stop-loss.
OHLCV candles do not reveal the order of price movement inside the candle, so
the conservative outcome prevents unrealistically favorable results.

## Architecture

```text
Browser
  index.html + home.js
       |
       | GET /api/replay/options
       | navigate with market, symbol, timeframe
       v
  chart.html + chart.js + Lightweight Charts
       |
       | GET /api/replay/session
       v
Spring Boot
  ReplayController
       |
  ReplayDataService
       |
  ReplayCatalog + deterministic OHLCV generator
```

Spring Boot serves both the static frontend and JSON API from one application.
No CORS configuration or separate frontend development server is required.

## Backend walkthrough

### `ReplayCatalog`

`ReplayCatalog` is the single source of truth for markets, instruments, price
precision, starting prices, volatility, and supported timeframes. Add a new
instrument by registering another `InstrumentDefinition`.

### `ReplayDataService`

The service validates selections through `ReplayCatalog` and creates 360 candles.
The random-number seed comes from market, symbol, and timeframe, so the same
selection always produces the same candles.

Each candle follows the required OHLC relationship:

```text
high >= open and close
low  <= open and close
```

The generator also adds changing trend regimes, cyclical movement, random
volatility, wicks, and volume. It is useful for application development and
repeatable practice, but it is not a model of a real exchange.

### `ReplayController`

The controller exposes two endpoints:

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/replay/options` | Markets, instruments, and timeframes |
| `GET` | `/api/replay/session?market=STOCKS&symbol=SPY&timeframe=5m` | One replay dataset |

Invalid market/instrument combinations return an HTTP 400 response through the
existing `ApiExceptionHandler`.

## Frontend walkthrough

### `index.html` and `home.js`

The home page requests available selections from Java instead of duplicating
them in JavaScript. It creates the market and timeframe controls, updates the
instrument list, and navigates to:

```text
/chart.html?market=STOCKS&symbol=SPY&timeframe=5m
```

### `chart.html` and `chart.js`

The chart page loads Lightweight Charts from a pinned CDN version. It initially
shows 70 candles and keeps the remaining candles hidden.

`chart.js` manages the simulation using these states:

```text
PLANNING -> READY -> WAITING -> OPEN -> CLOSED
```

- **PLANNING:** The user is defining three price levels.
- **READY:** All levels exist.
- **WAITING:** Replay started, but price has not touched entry.
- **OPEN:** Entry was touched and current P&L updates each candle.
- **CLOSED:** Stop, target, or session end resolved the trade.

P&L uses:

```text
long P&L  = (current price - entry price) × quantity
short P&L = (entry price - current price) × quantity
```

The replay uses `setTimeout` rather than `setInterval`. After each candle, it
schedules exactly one next update. This prevents overlapping callbacks when the
user changes speed or pauses the simulation.

### `simulator.css`

One responsive stylesheet supports both pages. On narrow screens, the chart and
trade panel stack vertically so the simulator remains usable.

## Replacing generated data later

Keep the `ReplaySession` JSON shape and replace only the data implementation.
A real provider adapter should:

1. Fetch historical OHLCV candles server-side.
2. Map provider values to `ReplayCandle`.
3. Sort candles from oldest to newest.
4. Validate OHLC relationships and remove duplicates.
5. Cache results to respect provider rate limits.
6. Keep API credentials in environment variables, never browser JavaScript.

Twelve Data or Alpha Vantage can supply data, but confirm that the chosen plan
allows the asset classes, intraday intervals, history depth, and usage required
by your deployment.

## Suggested next improvements

1. Drag chart lines after placing them.
2. Add market and limit entry modes.
3. Track a journal across multiple simulation sessions.
4. Calculate win rate, expectancy, drawdown, and average risk-to-reward.
5. Add chart volume and technical indicators.
6. Replace generated candles with a cached historical-data provider.
7. Store users and results in PostgreSQL with Spring Data JPA.
8. Add Spring Security so each learner has a private journal.

ReplayLab is educational software, not financial advice or a brokerage.
