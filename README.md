# ReplayLab trading simulator

ReplayLab is a historical market-replay trainer built with Java 17, Spring Boot,
plain HTML/CSS/JavaScript, and TradingView Lightweight Charts. It reveals
candles one at a time so a learner can practice defining an entry, stop-loss,
and take-profit without risking money.

The app intentionally uses deterministic generated OHLCV data. Every market and
timeframe works without an API key, rate limit, paid data plan, or network call
to a market-data vendor.

## What the application does

ReplayLab has three pages:

1. **Home page:** Select an asset class, instrument, and candle timeframe.
2. **Chart page:** Place a long or short trade plan directly on a candlestick
   chart, replay hidden candles, track P&L, and review the result.
3. **Backtest page:** Run Buy & Hold, moving-average crossover, or RSI rules
   over a chosen daily date range and compare the strategy with its benchmark.

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

Open <http://localhost:8080/backtest.html> to use the automated historical
backtester.

## How to use a replay

1. Choose a market, instrument, and timeframe on the home page.
2. Choose **Market**, **Limit**, or **Stop**, then choose **Long** or **Short**,
   position size, and order expiration.
3. For a limit or stop order, double-click the chart to set the entry price.
   Market orders show an estimated next-candle entry automatically.
4. Double-click above and below entry to set the stop-loss and take-profit:
   - Long: target above entry, stop below entry.
   - Short: stop above entry, target below entry.
5. Press **Play**. Limit and stop orders remain pending until their conditions
   are met. Market orders begin filling on the next candle.
6. Watch fill quantity, average cost, fees, and unrealized P&L update.
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

## Historical backtesting

The backtest page accepts a market, instrument, strategy, date range, starting
cash, and fee rate. Strategy-specific inputs let you change moving-average
periods or RSI thresholds. Users can choose ReplayLab's generated history or
upload up to 5 MB of their own daily candle data.

Uploaded CSV files must be UTF-8 and sorted oldest to newest:

```csv
date,open,high,low,close,volume
2025-01-02,100.00,103.20,99.40,102.50,1250000
2025-01-03,102.60,104.10,101.80,103.75,1175000
```

All six columns are required. Dates may use `YYYY-MM-DD` or `MM/DD/YYYY`.
Prices must be positive, volume cannot be negative, and every row must contain
a valid high/low range. An example file is downloadable from the backtest page.

Available strategies:

- **Buy & Hold:** Buys at the first candle's open and sells at the final close.
- **Moving-average crossover:** Holds the instrument while the fast closing
  average is above the slow average.
- **RSI reversal:** Enters below the oversold threshold and exits above the
  overbought threshold.

Moving-average and RSI signals are calculated after a daily candle closes. The
resulting order executes at the following daily open, so the strategy cannot
use a closing price before that price is known. The engine invests available
cash, supports fractional quantities, charges entry and exit fees, and closes
any remaining position at the end of the test.

The result includes ending equity, net profit, total and annualized return,
Buy & Hold return, maximum drawdown, fees, completed trades, win rate, an equity
curve, and every execution.

Backtest endpoints:

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/backtests/options` | Markets, strategies, and date limits |
| `POST` | `/api/backtests` | Validate and execute one historical backtest |
| `POST` | `/api/backtests/csv` | Upload OHLCV data and execute a backtest |

`HistoricalDataService` generates repeatable daily OHLCV candles from a fixed
2015 anchor. This makes comparisons reproducible and keeps the application
usable without an external data subscription. Replace that service with a
provider adapter when actual exchange history is required.

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
PLANNING -> READY -> PENDING -> PARTIAL/OPEN -> CLOSED
```

- **PLANNING:** The user is defining three price levels.
- **READY:** All levels exist.
- **PENDING:** The entry order is active but has not filled.
- **PARTIAL:** Candle liquidity filled only part of the requested quantity.
- **OPEN:** At least part of the position is open and unrealized P&L updates.
- **CLOSED:** Stop, target, or session end resolved the trade.

For an open long position, unrealized P&L uses the current executable bid. For
an open short position, it uses the current executable ask:

```text
long unrealized P&L  = (current quote - average cost) × filled quantity
short unrealized P&L = (average cost - current quote) × filled quantity
percentage return    = unrealized P&L / (average cost × filled quantity) × 100
```

### Execution realism

Every market has a Java-provided execution profile:

- **Bid/ask spread:** Buy orders use the ask and sell orders use the bid.
- **Slippage:** Market and triggered stop orders receive a small adverse price
  adjustment. Limit fills never execute beyond their limit.
- **Fees:** Each entry fill and final exit charges a percentage of notional.
- **Partial fills:** Each candle can fill only the configured percentage of its
  volume. The app maintains a volume-weighted average entry cost.
- **Expiration:** The unfilled entry remainder can expire after 20, 50, or 100
  bars, or remain active through the session.
- **Gap handling:** Stops can fill beyond their trigger after a gap. Limits can
  receive price improvement when a candle opens through their price.

The current quote is the current replay candle adjusted to the executable
bid/ask. Using today's live quote inside a historical session would mix two
different times and produce invalid P&L.

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
2. Add trailing stops and bracket-order modification.
3. Track a journal across multiple simulation sessions.
4. Calculate win rate, expectancy, drawdown, and average risk-to-reward.
5. Add chart volume and technical indicators.
6. Replace generated candles with a cached historical-data provider.
7. Store users and results in PostgreSQL with Spring Data JPA.
8. Add Spring Security so each learner has a private journal.

ReplayLab is educational software, not financial advice or a brokerage.
