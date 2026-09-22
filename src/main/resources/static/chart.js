/*
 * ReplayLab chart and execution controller.
 *
 * This file has four responsibilities:
 * 1. Draw visible candles with Lightweight Charts.
 * 2. Manage the entry/stop/target plan created by chart double-clicks.
 * 3. Reveal hidden candles and simulate realistic order execution.
 * 4. Render open-position P&L and the final result.
 *
 * Financial state is kept in plan, entryOrder, position, and trade so changing
 * UI text cannot accidentally change accounting.
 */
const SPEEDS = [1, 2, 5];
const BASE_DELAY_MS = 700;
const LEVEL_COLORS = {
    entry: "#3b82f6",
    stop: "#ef4444",
    target: "#22c55e"
};

const params = new URLSearchParams(window.location.search);
const request = {
    market: params.get("market") || "",
    symbol: params.get("symbol") || "",
    timeframe: params.get("timeframe") || ""
};

const elements = {
    container: document.querySelector("#chart-container"),
    loading: document.querySelector("#chart-loading"),
    playButton: document.querySelector("#play-button"),
    playIcon: document.querySelector("#play-icon"),
    playLabel: document.querySelector("#play-label"),
    restart: document.querySelector("#step-back"),
    speedSlider: document.querySelector("#speed-slider"),
    speedValue: document.querySelector("#speed-value"),
    orderType: document.querySelector("#order-type"),
    expiration: document.querySelector("#order-expiration"),
    positionSize: document.querySelector("#position-size"),
    instructionStep: document.querySelector("#instruction-step"),
    instructionText: document.querySelector("#instruction-text"),
    tradeStatus: document.querySelector("#trade-status"),
    currentPnl: document.querySelector("#current-pnl"),
    pnlPercent: document.querySelector("#pnl-percent"),
    toast: document.querySelector("#toast"),
    dialog: document.querySelector("#result-dialog")
};

let session;
let chart;
let candleSeries;
let resizeObserver;
let replayTimer;
let resultTimer;
let cursor;
let initialPrice;
let latestCandle;
let levelLines = {};
let plan = emptyPlan();
let entryOrder = emptyEntryOrder();
let position = emptyPosition();
let trade = emptyTrade();
let isPlaying = false;

// ---------------------------------------------------------------------------
// Session loading and chart creation
// ---------------------------------------------------------------------------

initialize();

async function initialize() {
    if (!request.market || !request.symbol || !request.timeframe) {
        showFatalError("This replay link is incomplete. Return home and start a new session.");
        return;
    }
    if (typeof LightweightCharts === "undefined") {
        showFatalError("The chart library could not load. Check your internet connection and refresh.");
        return;
    }

    try {
        const query = new URLSearchParams(request);
        const response = await fetch(`/api/replay/session?${query}`);
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || "The replay session could not be created.");
        }
        session = await response.json();
        configurePage();
        createChart();
        resetSimulation();
    } catch (error) {
        showFatalError(error.message);
    }
}

function configurePage() {
    document.title = `${session.symbol} Replay | ReplayLab`;
    setText("#header-symbol", session.symbol);
    setText("#header-name", `${session.instrumentName} · ${session.timeframe}`);
    setText("#market-label", session.marketLabel);
    setText("#spread-setting", `${formatNumber(session.executionProfile.spreadBps)} bps`);
    setText("#slippage-setting", `${formatNumber(session.executionProfile.slippageBps)} bps`);
    setText("#fee-setting", `${formatNumber(session.executionProfile.feeRateBps)} bps / fill`);
    setText(
        "#liquidity-setting",
        `${formatNumber(session.executionProfile.maxVolumeParticipationPercent)}%`
    );
    elements.playButton.disabled = false;
    elements.restart.disabled = false;
}

function createChart() {
    elements.loading.remove();
    chart = LightweightCharts.createChart(elements.container, {
        width: elements.container.clientWidth,
        height: elements.container.clientHeight,
        layout: {
            background: {type: "solid", color: "#0c1117"},
            textColor: "#8b98a5",
            fontFamily: "Inter, system-ui, sans-serif"
        },
        grid: {
            vertLines: {color: "#18212b"},
            horzLines: {color: "#18212b"}
        },
        crosshair: {
            mode: LightweightCharts.CrosshairMode.Normal,
            vertLine: {color: "#556270", labelBackgroundColor: "#25303b"},
            horzLine: {color: "#556270", labelBackgroundColor: "#25303b"}
        },
        rightPriceScale: {
            borderColor: "#27313b",
            scaleMargins: {top: 0.08, bottom: 0.12}
        },
        timeScale: {
            borderColor: "#27313b",
            timeVisible: true,
            secondsVisible: false,
            rightOffset: 8,
            barSpacing: 9
        },
        handleScale: true,
        handleScroll: true
    });
    candleSeries = chart.addCandlestickSeries({
        upColor: "#22c55e",
        downColor: "#ef4444",
        borderVisible: false,
        wickUpColor: "#22c55e",
        wickDownColor: "#ef4444",
        priceFormat: {
            type: "price",
            precision: session.pricePrecision,
            minMove: 10 ** -session.pricePrecision
        }
    });

    elements.container.addEventListener("dblclick", handleChartDoubleClick);
    resizeObserver = new ResizeObserver(entries => {
        const bounds = entries[0].contentRect;
        chart.applyOptions({width: bounds.width, height: bounds.height});
    });
    resizeObserver.observe(elements.container);
}

// ---------------------------------------------------------------------------
// Planning and chart level placement
// ---------------------------------------------------------------------------

function resetSimulation() {
    if (!session || !candleSeries) {
        return;
    }
    pauseReplay();
    window.clearTimeout(resultTimer);
    resultTimer = undefined;
    removeLevelLines();
    plan = emptyPlan();
    entryOrder = emptyEntryOrder();
    position = emptyPosition();
    trade = emptyTrade();
    cursor = session.initialBars;
    const visibleCandles = session.candles.slice(0, cursor);
    candleSeries.setData(visibleCandles);
    chart.timeScale().fitContent();
    initialPrice = Number(visibleCandles[0].open);
    latestCandle = visibleCandles.at(-1);

    enableOrderInputs(true);
    prepareEntryLevel();
    updatePriceDisplay(latestCandle);
    renderPlan();
    renderExecution();
    updateReplayProgress(latestCandle);
    setStatus("PLANNING", "Planning");
}

function prepareEntryLevel() {
    // Market orders do not need a trigger price. The estimated line gives the
    // user a reference for placing risk levels; the real average cost comes
    // from one or more future fills.
    if (orderType() === "MARKET") {
        plan.entry = estimatedMarketEntry(Number(latestCandle.close));
        setLevelLine("entry", plan.entry, "MKT EST.");
        setInstruction(
            1,
            direction() === "LONG"
                ? "Market entry is estimated. Set a stop below and target above."
                : "Market entry is estimated. Set a stop above and target below."
        );
    } else {
        setInstruction(
            1,
            `Double-click the chart to set the ${orderType().toLowerCase()} entry price.`
        );
    }
}

function handleChartDoubleClick(event) {
    if (cursor > session.initialBars || trade.status !== "PLANNING") {
        showToast("Restart the session before changing trade levels.");
        return;
    }
    const bounds = elements.container.getBoundingClientRect();
    const coordinate = event.clientY - bounds.top;
    const clickedPrice = candleSeries.coordinateToPrice(coordinate);
    if (clickedPrice == null || clickedPrice <= 0) {
        return;
    }
    const price = roundPrice(clickedPrice);

    if (plan.entry == null) {
        plan.entry = price;
        setLevelLine("entry", price, orderType());
        setInstruction(
            2,
            direction() === "LONG"
                ? "Set a stop below entry and a target above entry."
                : "Set a stop above entry and a target below entry."
        );
    } else {
        assignRiskLevel(price);
    }
    renderPlan();
}

function assignRiskLevel(price) {
    if (price === plan.entry) {
        showToast("Choose a price above or below the entry.");
        return;
    }

    const isAboveEntry = price > plan.entry;
    const levelType = direction() === "LONG"
        ? (isAboveEntry ? "target" : "stop")
        : (isAboveEntry ? "stop" : "target");
    plan[levelType] = price;
    setLevelLine(levelType, price, levelType === "stop" ? "STOP" : "TARGET");

    if (isPlanComplete()) {
        setInstruction(3, "Your plan is ready. Press Play to submit the entry order.");
        setStatus("READY", "Ready");
    } else {
        const remaining = plan.stop == null ? "stop-loss" : "take-profit";
        setInstruction(3, `Double-click to set the ${remaining}.`);
    }
}

// Price lines belong to the chart library. We keep each returned handle so a
// changed or reset level can remove the old line instead of drawing duplicates.
function setLevelLine(type, price, title) {
    if (levelLines[type]) {
        candleSeries.removePriceLine(levelLines[type]);
    }
    levelLines[type] = candleSeries.createPriceLine({
        price,
        color: LEVEL_COLORS[type],
        lineWidth: 2,
        lineStyle: type === "entry"
            ? LightweightCharts.LineStyle.Solid
            : LightweightCharts.LineStyle.Dashed,
        axisLabelVisible: true,
        title
    });
}

function renderPlan() {
    const entryLabel = plan.entry == null
        ? "Not set"
        : orderType() === "MARKET"
            ? `Market · est. ${formatPrice(plan.entry)}`
            : `${orderTypeTitle()} · ${formatPrice(plan.entry)}`;
    setText("#entry-price", entryLabel);
    setText("#stop-price", formatOptionalPrice(plan.stop));
    setText("#target-price", formatOptionalPrice(plan.target));

    if (isPlanComplete()) {
        const riskPerUnit = Math.abs(plan.entry - plan.stop);
        const rewardPerUnit = Math.abs(plan.target - plan.entry);
        const quantity = requestedQuantity() || 0;
        setText("#risk-reward", `1 : ${(rewardPerUnit / riskPerUnit).toFixed(2)}`);
        setText("#risk-amount", formatMoney(riskPerUnit * quantity));
        setText("#reward-amount", formatMoney(rewardPerUnit * quantity));
    } else {
        setText("#risk-reward", "—");
        setText("#risk-amount", "—");
        setText("#reward-amount", "—");
    }
}

function renderExecution() {
    const requested = entryOrder.requestedQuantity || requestedQuantity() || 0;
    setText("#filled-quantity", `${position.quantity} / ${requested}`);
    setText(
        "#average-cost",
        position.quantity > 0 ? formatPrice(position.averageCost) : "—"
    );
    setText("#fees-paid", formatMoney(position.entryFees + trade.exitFees));
    updateUnrealizedPnl();
}

// ---------------------------------------------------------------------------
// Replay timer and order submission
// ---------------------------------------------------------------------------

function toggleReplay() {
    if (isPlaying) {
        pauseReplay();
        return;
    }
    if (!isPlanComplete()) {
        showToast("Set the entry, stop-loss, and take-profit before playing.");
        return;
    }
    const setupError = window.TradePlanValidation.error(direction(), plan);
    if (setupError) {
        showToast(setupError);
        return;
    }
    if (!requestedQuantity()) {
        showToast("Enter a whole-number position size greater than zero.");
        return;
    }
    if (trade.status === "CLOSED") {
        showToast("Restart the session to place another trade.");
        return;
    }
    startReplay();
}

function startReplay() {
    if (cursor >= session.candles.length) {
        finishAtSessionEnd();
        return;
    }
    if (trade.status === "PLANNING") {
        submitEntryOrder();
    }
    isPlaying = true;
    elements.playIcon.textContent = "Ⅱ";
    elements.playLabel.textContent = "Pause";
    enableOrderInputs(false);
    scheduleNextCandle();
}

function submitEntryOrder() {
    entryOrder = {
        active: true,
        stopTriggered: false,
        submittedAt: cursor,
        requestedQuantity: requestedQuantity(),
        filledQuantity: 0
    };
    trade.status = "PENDING";
    setStatus("PENDING", `${orderTypeTitle()} pending`);
    setInstruction("•", "Entry order submitted. Waiting for the market to fill it.");
    renderExecution();
}

function pauseReplay() {
    isPlaying = false;
    window.clearTimeout(replayTimer);
    replayTimer = undefined;
    elements.playIcon.textContent = "▶";
    elements.playLabel.textContent = "Play";
}

function scheduleNextCandle() {
    // A chained timeout avoids overlapping ticks when speed changes or the
    // user pauses, which can happen with a fixed setInterval.
    if (!isPlaying) {
        return;
    }
    replayTimer = window.setTimeout(revealNextCandle, BASE_DELAY_MS / replaySpeed());
}

function revealNextCandle() {
    if (cursor >= session.candles.length) {
        finishAtSessionEnd();
        return;
    }

    const candle = session.candles[cursor];
    candleSeries.update(candle);
    cursor += 1;
    latestCandle = candle;
    updatePriceDisplay(candle);
    updateReplayProgress(candle);
    evaluateCandle(candle);

    if (trade.status !== "CLOSED" && cursor >= session.candles.length) {
        finishAtSessionEnd();
    } else if (trade.status !== "CLOSED") {
        scheduleNextCandle();
    }
}

function evaluateCandle(candle) {
    if (entryOrder.active) {
        processEntryFill(candle);
        expireEntryOrderIfNeeded();
    }
    if (position.quantity > 0 && trade.status !== "CLOSED") {
        evaluateExit(candle);
    }
    renderExecution();
}

// ---------------------------------------------------------------------------
// Entry execution, liquidity, and expiration
// ---------------------------------------------------------------------------

function processEntryFill(candle) {
    const fillPrice = entryFillPrice(candle);
    if (fillPrice == null) {
        return;
    }

    const remaining = entryOrder.requestedQuantity - entryOrder.filledQuantity;
    const fillQuantity = Math.min(remaining, candleLiquidity(candle));
    if (fillQuantity <= 0) {
        return;
    }

    // Preserve the unrounded weighted average internally. Rounding each partial
    // fill would compound cost-basis error; formatting rounds only for display.
    const previousCost = position.averageCost * position.quantity;
    const newQuantity = position.quantity + fillQuantity;
    position.averageCost =
        (previousCost + fillPrice * fillQuantity) / newQuantity;
    position.quantity = newQuantity;
    position.entryFees += transactionFee(fillPrice, fillQuantity);
    entryOrder.filledQuantity += fillQuantity;

    if (position.openedAt == null) {
        position.openedAt = cursor - 1;
    }
    trade.status = "OPEN";

    if (entryOrder.filledQuantity === entryOrder.requestedQuantity) {
        entryOrder.active = false;
        setStatus("OPEN", "Open · fully filled");
        setInstruction("•", "Position filled. Monitoring the stop-loss and target.");
    } else {
        setStatus("PARTIAL", "Open · partial fill");
        setInstruction(
            "•",
            `Filled ${entryOrder.filledQuantity} of ${entryOrder.requestedQuantity} units.`
        );
    }
}

function entryFillPrice(candle) {
    const open = Number(candle.open);
    const high = Number(candle.high);
    const low = Number(candle.low);
    const isLong = direction() === "LONG";

    if (orderType() === "MARKET" || entryOrder.stopTriggered) {
        return marketEntryPrice(open);
    }

    if (orderType() === "LIMIT") {
        // A limit order may receive a better price after a gap, but slippage is
        // capped so it can never execute beyond the requested limit.
        if (isLong && askPrice(low) <= plan.entry) {
            return roundPrice(Math.min(plan.entry, slippedPrice(askPrice(open), "BUY")));
        }
        if (!isLong && bidPrice(high) >= plan.entry) {
            return roundPrice(Math.max(plan.entry, slippedPrice(bidPrice(open), "SELL")));
        }
        return null;
    }

    // Once touched, a stop entry converts to a market order. Any unfilled
    // remainder continues as market quantity on following candles.
    const stopTouched = isLong
        ? askPrice(high) >= plan.entry
        : bidPrice(low) <= plan.entry;
    if (!stopTouched) {
        return null;
    }
    entryOrder.stopTriggered = true;
    setStatus("TRIGGERED", "Stop triggered");
    const gapAdjustedPrice = isLong
        ? Math.max(plan.entry, askPrice(open))
        : Math.min(plan.entry, bidPrice(open));
    return roundPrice(slippedPrice(gapAdjustedPrice, isLong ? "BUY" : "SELL"));
}

function expireEntryOrderIfNeeded() {
    if (!entryOrder.active || expirationBars() == null) {
        return;
    }
    const elapsedBars = cursor - entryOrder.submittedAt;
    if (elapsedBars < expirationBars()) {
        return;
    }

    entryOrder.active = false;
    if (position.quantity === 0) {
        trade.status = "CLOSED";
        trade.reason = "EXPIRED";
        pauseReplay();
        setStatus("EXPIRED", "Order expired");
        showResultSoon();
    } else {
        setStatus("PARTIAL", "Open · remainder expired");
        showToast(
            `${entryOrder.requestedQuantity - entryOrder.filledQuantity} unfilled units expired.`
        );
    }
}

// ---------------------------------------------------------------------------
// Protective exits and session completion
// ---------------------------------------------------------------------------

function evaluateExit(candle) {
    const stopTouched = direction() === "LONG"
        ? bidPrice(Number(candle.low)) <= plan.stop
        : askPrice(Number(candle.high)) >= plan.stop;
    const targetTouched = direction() === "LONG"
        ? bidPrice(Number(candle.high)) >= plan.target
        : askPrice(Number(candle.low)) <= plan.target;

    // Candles do not reveal intrabar ordering, so simultaneous hits use the
    // conservative stop-loss outcome.
    if (stopTouched) {
        closeTrade("STOP", stopExitPrice(candle));
    } else if (targetTouched) {
        closeTrade("TARGET", targetExitPrice(candle));
    }
}

function stopExitPrice(candle) {
    // A stop can fill beyond its trigger when the candle opens through it.
    const isLong = direction() === "LONG";
    const openQuote = isLong
        ? bidPrice(Number(candle.open))
        : askPrice(Number(candle.open));
    const gapAdjusted = isLong
        ? Math.min(plan.stop, openQuote)
        : Math.max(plan.stop, openQuote);
    return roundPrice(slippedPrice(gapAdjusted, isLong ? "SELL" : "BUY"));
}

function targetExitPrice(candle) {
    // A target behaves like a limit order: opening beyond it gives price
    // improvement, while slippage is not allowed to make the fill worse.
    const isLong = direction() === "LONG";
    const openQuote = isLong
        ? bidPrice(Number(candle.open))
        : askPrice(Number(candle.open));
    const improved = isLong
        ? Math.max(plan.target, openQuote)
        : Math.min(plan.target, openQuote);
    const slipped = slippedPrice(improved, isLong ? "SELL" : "BUY");
    return roundPrice(isLong
        ? Math.max(plan.target, slipped)
        : Math.min(plan.target, slipped));
}

function closeTrade(reason, exitPrice) {
    trade.status = "CLOSED";
    trade.reason = reason;
    trade.exitPrice = exitPrice;
    trade.closedAt = cursor - 1;
    trade.exitFees = transactionFee(exitPrice, position.quantity);
    entryOrder.active = false;
    pauseReplay();
    setStatus(reason, reason === "TARGET" ? "Target hit" : "Stop hit");
    renderExecution();
    showResultSoon();
}

function finishAtSessionEnd() {
    pauseReplay();
    if (position.quantity > 0) {
        const exitPrice = roundPrice(slippedPrice(
            markPrice(Number(latestCandle.close)),
            direction() === "LONG" ? "SELL" : "BUY"
        ));
        trade.status = "CLOSED";
        trade.reason = "SESSION_END";
        trade.exitPrice = exitPrice;
        trade.closedAt = cursor - 1;
        trade.exitFees = transactionFee(exitPrice, position.quantity);
        entryOrder.active = false;
        setStatus("SESSION_END", "Session ended");
    } else {
        trade.status = "CLOSED";
        trade.reason = "NO_FILL";
        entryOrder.active = false;
        setStatus("NO_FILL", "Entry not filled");
    }
    renderExecution();
    showResult();
}

// ---------------------------------------------------------------------------
// Position valuation and execution assumptions
// ---------------------------------------------------------------------------

function updateUnrealizedPnl() {
    let pnl = 0;
    let percentage = 0;
    if (position.quantity > 0 && trade.status !== "CLOSED") {
        const currentQuote = markPrice(Number(latestCandle.close));
        // Long:  (current bid - average cost) * quantity
        // Short: (average cost - current ask) * quantity
        const multiplier = direction() === "LONG" ? 1 : -1;
        pnl = (currentQuote - position.averageCost) * position.quantity * multiplier;
        const costBasis = position.averageCost * position.quantity;
        percentage = costBasis === 0 ? 0 : (pnl / costBasis) * 100;
    }
    elements.currentPnl.textContent = formatSignedMoney(pnl);
    elements.pnlPercent.textContent = `${formatSignedNumber(percentage)}%`;
    const className = pnl < 0 ? "loss" : pnl > 0 ? "profit" : "";
    elements.currentPnl.className = className;
    elements.pnlPercent.className = className;
}

function showResultSoon() {
    window.clearTimeout(resultTimer);
    resultTimer = window.setTimeout(() => {
        resultTimer = undefined;
        showResult();
    }, 250);
}

function showResult() {
    const hasPosition = position.quantity > 0;
    const title = {
        TARGET: "Take-profit reached",
        STOP: "Stop-loss reached",
        SESSION_END: "Session complete",
        EXPIRED: "Entry order expired",
        NO_FILL: "Entry was not filled"
    }[trade.reason];
    const summary = {
        TARGET: "The target closed the position using a spread-adjusted limit fill.",
        STOP: "The stop closed the position with simulated spread and slippage.",
        SESSION_END: "The remaining position closed at the final executable replay quote.",
        EXPIRED: "No units filled before the selected order expiration.",
        NO_FILL: "The replay ended before the entry order could fill."
    }[trade.reason];
    const finalPnl = hasPosition ? netRealizedPnl() : 0;

    setText("#result-title", title);
    setText("#result-summary", summary);
    setText("#result-pnl", formatSignedMoney(finalPnl));
    setText("#result-direction", directionTitle());
    setText(
        "#result-entry",
        hasPosition ? formatPrice(position.averageCost) : "Not filled"
    );
    setText(
        "#result-exit",
        trade.exitPrice == null ? "Not filled" : formatPrice(trade.exitPrice)
    );
    setText(
        "#result-bars",
        hasPosition ? Math.max(1, trade.closedAt - position.openedAt + 1) : "—"
    );
    setText("#result-quantity", `${position.quantity} units`);
    setText("#result-fees", formatMoney(position.entryFees + trade.exitFees));

    const resultPnl = document.querySelector("#result-pnl");
    resultPnl.className = finalPnl < 0 ? "loss" : finalPnl > 0 ? "profit" : "";
    const isWin = finalPnl > 0;
    const icon = document.querySelector("#result-icon");
    icon.textContent = isWin ? "✓" : finalPnl < 0 ? "×" : "■";
    icon.className = `result-icon ${isWin ? "win" : finalPnl < 0 ? "loss" : ""}`;
    elements.dialog.showModal();
}

function updatePriceDisplay(candle) {
    const midpoint = Number(candle.close);
    const change = midpoint - initialPrice;
    const percent = (change / initialPrice) * 100;
    setText("#current-price", formatPrice(midpoint));
    setText(
        "#sidebar-price",
        `${formatPrice(bidPrice(midpoint))} / ${formatPrice(askPrice(midpoint))}`
    );
    const changeElement = document.querySelector("#current-change");
    changeElement.textContent = `${formatSignedPrice(change)} · ${formatSignedNumber(percent)}%`;
    changeElement.className = `price-change ${change < 0 ? "loss" : change > 0 ? "profit" : ""}`;
}

function updateReplayProgress(candle) {
    setText("#replay-progress", `${cursor} / ${session.candles.length} candles`);
    setText("#session-time", formatCandleTime(candle.time));
}

function estimatedMarketEntry(midpoint) {
    return roundPrice(slippedPrice(
        direction() === "LONG" ? askPrice(midpoint) : bidPrice(midpoint),
        direction() === "LONG" ? "BUY" : "SELL"
    ));
}

function marketEntryPrice(midpoint) {
    return estimatedMarketEntry(midpoint);
}

function markPrice(midpoint) {
    return roundPrice(direction() === "LONG" ? bidPrice(midpoint) : askPrice(midpoint));
}

function bidPrice(midpoint) {
    // spreadBps is the full spread, so each side is half that distance from
    // the generated candle's midpoint.
    return midpoint * (1 - session.executionProfile.spreadBps / 20_000);
}

function askPrice(midpoint) {
    return midpoint * (1 + session.executionProfile.spreadBps / 20_000);
}

function slippedPrice(price, side) {
    const rate = session.executionProfile.slippageBps / 10_000;
    return side === "BUY" ? price * (1 + rate) : price * (1 - rate);
}

function transactionFee(price, quantity) {
    return price * quantity * session.executionProfile.feeRateBps / 10_000;
}

function candleLiquidity(candle) {
    // Restricting participation creates partial fills without inventing a full
    // level-two order book. At least one unit may fill on an eligible candle.
    const participation =
        session.executionProfile.maxVolumeParticipationPercent / 100;
    return Math.max(1, Math.floor(Number(candle.volume) * participation));
}

function netRealizedPnl() {
    // Final results include both entry and exit transaction fees. Unrealized
    // P&L above follows the requested price-only mark-to-market formula.
    const multiplier = direction() === "LONG" ? 1 : -1;
    const grossPnl =
        (trade.exitPrice - position.averageCost) * position.quantity * multiplier;
    return grossPnl - position.entryFees - trade.exitFees;
}

function expirationBars() {
    return elements.expiration.value === "SESSION"
        ? null
        : Number(elements.expiration.value);
}

// ---------------------------------------------------------------------------
// Small state factories, formatting, and DOM event wiring
// ---------------------------------------------------------------------------

function enableOrderInputs(enabled) {
    elements.orderType.disabled = !enabled;
    elements.expiration.disabled = !enabled;
    elements.positionSize.disabled = !enabled;
    document.querySelectorAll("[name=direction]").forEach(input => {
        input.disabled = !enabled;
    });
}

function setStatus(status, label) {
    elements.tradeStatus.textContent = label;
    elements.tradeStatus.className =
        `status-badge status-${status.toLowerCase().replace("_", "-")}`;
}

function setInstruction(step, message) {
    elements.instructionStep.textContent = step;
    elements.instructionText.textContent = message;
}

function removeLevelLines() {
    if (!candleSeries) {
        return;
    }
    Object.values(levelLines).forEach(line => candleSeries.removePriceLine(line));
    levelLines = {};
}

function isPlanComplete() {
    return plan.entry != null && plan.stop != null && plan.target != null;
}

function direction() {
    return document.querySelector("[name=direction]:checked").value;
}

function directionTitle() {
    return direction() === "LONG" ? "Long" : "Short";
}

function orderType() {
    return elements.orderType.value;
}

function orderTypeTitle() {
    return orderType().charAt(0) + orderType().slice(1).toLowerCase();
}

function requestedQuantity() {
    const value = Number(elements.positionSize.value);
    return Number.isInteger(value) && value > 0 && value <= 100_000
        ? value
        : null;
}

function replaySpeed() {
    return SPEEDS[Number(elements.speedSlider.value)];
}

function emptyPlan() {
    return {entry: null, stop: null, target: null};
}

function emptyEntryOrder() {
    return {
        active: false,
        stopTriggered: false,
        submittedAt: null,
        requestedQuantity: 0,
        filledQuantity: 0
    };
}

function emptyPosition() {
    return {
        quantity: 0,
        averageCost: 0,
        entryFees: 0,
        openedAt: null
    };
}

function emptyTrade() {
    return {
        status: "PLANNING",
        reason: null,
        exitPrice: null,
        exitFees: 0,
        closedAt: null
    };
}

function roundPrice(price) {
    return Number(price.toFixed(session.pricePrecision));
}

function formatPrice(value) {
    return Number(value).toLocaleString("en-US", {
        minimumFractionDigits: session.pricePrecision,
        maximumFractionDigits: session.pricePrecision
    });
}

function formatOptionalPrice(value) {
    return value == null ? "Not set" : formatPrice(value);
}

function formatMoney(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(value);
}

function formatSignedMoney(value) {
    return `${value >= 0 ? "+" : "−"}${formatMoney(Math.abs(value))}`;
}

function formatSignedPrice(value) {
    return `${value >= 0 ? "+" : "−"}${Math.abs(value).toFixed(session.pricePrecision)}`;
}

function formatSignedNumber(value) {
    return `${value >= 0 ? "+" : "−"}${Math.abs(value).toFixed(2)}`;
}

function formatNumber(value) {
    return Number(value).toLocaleString("en-US", {maximumFractionDigits: 3});
}

function formatCandleTime(epochSeconds) {
    const date = new Date(epochSeconds * 1000);
    return date.toLocaleString([], {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit"
    });
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
        elements.toast.hidden = true;
    }, 3500);
}

function showFatalError(message) {
    elements.loading.textContent = message;
    elements.loading.classList.add("error");
}

function setText(selector, value) {
    document.querySelector(selector).textContent = value;
}

elements.playButton.addEventListener("click", toggleReplay);
elements.restart.addEventListener("click", resetSimulation);
document.querySelector("#clear-levels").addEventListener("click", resetSimulation);
elements.speedSlider.addEventListener("input", () => {
    elements.speedValue.textContent = `${replaySpeed()}×`;
    if (isPlaying) {
        window.clearTimeout(replayTimer);
        scheduleNextCandle();
    }
});
elements.positionSize.addEventListener("input", () => {
    renderPlan();
    renderExecution();
});
elements.orderType.addEventListener("change", resetSimulation);
elements.expiration.addEventListener("change", resetSimulation);
document.querySelectorAll("[name=direction]").forEach(input => {
    input.addEventListener("change", resetSimulation);
});
document.querySelector("#close-result").addEventListener("click", () => {
    elements.dialog.close();
});
document.querySelector("#replay-again").addEventListener("click", () => {
    elements.dialog.close();
    resetSimulation();
});
