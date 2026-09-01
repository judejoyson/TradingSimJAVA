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
let cursor;
let initialPrice;
let latestPrice;
let levelLines = {};
let plan = emptyPlan();
let trade = emptyTrade();
let isPlaying = false;

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

function resetSimulation() {
    pauseReplay();
    removeLevelLines();
    plan = emptyPlan();
    trade = emptyTrade();
    cursor = session.initialBars;
    const visibleCandles = session.candles.slice(0, cursor);
    candleSeries.setData(visibleCandles);
    chart.timeScale().fitContent();
    initialPrice = Number(visibleCandles[0].open);
    latestPrice = Number(visibleCandles.at(-1).close);
    updatePriceDisplay(visibleCandles.at(-1));
    renderPlan();
    updateReplayProgress(visibleCandles.at(-1));
    setInstruction(1, "Double-click the chart to set your entry price.");
    setStatus("PLANNING", "Planning");
    elements.positionSize.disabled = false;
    document.querySelectorAll("[name=direction]").forEach(input => {
        input.disabled = false;
    });
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
        setLevelLine("entry", price, "ENTRY");
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

    if (plan.stop != null && plan.target != null) {
        setInstruction(3, "Your plan is ready. Press Play to reveal the next candle.");
        setStatus("READY", "Ready");
    } else {
        const remaining = plan.stop == null ? "stop-loss" : "take-profit";
        setInstruction(3, `Double-click to set the ${remaining}.`);
    }
}

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
    setText("#entry-price", formatOptionalPrice(plan.entry));
    setText("#stop-price", formatOptionalPrice(plan.stop));
    setText("#target-price", formatOptionalPrice(plan.target));

    if (isPlanComplete()) {
        const riskPerUnit = Math.abs(plan.entry - plan.stop);
        const rewardPerUnit = Math.abs(plan.target - plan.entry);
        const quantity = positionSize();
        setText("#risk-reward", `1 : ${(rewardPerUnit / riskPerUnit).toFixed(2)}`);
        setText("#risk-amount", formatMoney(riskPerUnit * quantity));
        setText("#reward-amount", formatMoney(rewardPerUnit * quantity));
    } else {
        setText("#risk-reward", "—");
        setText("#risk-amount", "—");
        setText("#reward-amount", "—");
    }
    updatePnl();
}

function toggleReplay() {
    if (isPlaying) {
        pauseReplay();
        return;
    }
    if (!isPlanComplete()) {
        showToast("Set the entry, stop-loss, and take-profit before playing.");
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
        showToast("The replay has reached the end. Restart to try again.");
        return;
    }
    isPlaying = true;
    elements.playIcon.textContent = "Ⅱ";
    elements.playLabel.textContent = "Pause";
    elements.positionSize.disabled = true;
    document.querySelectorAll("[name=direction]").forEach(input => {
        input.disabled = true;
    });
    scheduleNextCandle();
}

function pauseReplay() {
    isPlaying = false;
    window.clearTimeout(replayTimer);
    replayTimer = undefined;
    elements.playIcon.textContent = "▶";
    elements.playLabel.textContent = "Play";
}

function scheduleNextCandle() {
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
    latestPrice = Number(candle.close);
    updatePriceDisplay(candle);
    updateReplayProgress(candle);
    evaluateTrade(candle);

    if (trade.status !== "CLOSED") {
        scheduleNextCandle();
    }
}

function evaluateTrade(candle) {
    const low = Number(candle.low);
    const high = Number(candle.high);

    if (trade.status === "PLANNING" || trade.status === "READY") {
        trade.status = "WAITING";
        setStatus("WAITING", "Waiting for entry");
    }

    if (trade.status === "WAITING" && low <= plan.entry && high >= plan.entry) {
        trade.status = "OPEN";
        trade.openedAt = cursor - 1;
        setStatus("OPEN", "Open");
        setInstruction("•", "Trade is open. Replay will stop at your stop or target.");
        updatePnl();
    }

    if (trade.status !== "OPEN") {
        return;
    }

    const stopHit = direction() === "LONG"
        ? low <= plan.stop
        : high >= plan.stop;
    const targetHit = direction() === "LONG"
        ? high >= plan.target
        : low <= plan.target;

    // OHLC data does not reveal which level traded first inside one candle,
    // so simultaneous hits use the conservative stop-loss outcome.
    if (stopHit) {
        closeTrade("STOP", plan.stop);
    } else if (targetHit) {
        closeTrade("TARGET", plan.target);
    } else {
        updatePnl();
    }
}

function closeTrade(reason, exitPrice) {
    trade.status = "CLOSED";
    trade.reason = reason;
    trade.exitPrice = exitPrice;
    trade.closedAt = cursor - 1;
    pauseReplay();
    updatePnl(exitPrice);
    setStatus(reason, reason === "TARGET" ? "Target hit" : "Stop hit");
    window.setTimeout(showResult, 250);
}

function finishAtSessionEnd() {
    pauseReplay();
    if (trade.status === "OPEN") {
        trade.status = "CLOSED";
        trade.reason = "SESSION_END";
        trade.exitPrice = latestPrice;
        trade.closedAt = cursor - 1;
        updatePnl(latestPrice);
        setStatus("SESSION_END", "Session ended");
        showResult();
    } else {
        trade.status = "CLOSED";
        trade.reason = "NO_FILL";
        setStatus("NO_FILL", "Entry not reached");
        showResult();
    }
}

function updatePnl(price = latestPrice) {
    let pnl = 0;
    let percentage = 0;
    if (trade.status === "OPEN" || trade.status === "CLOSED") {
        const directionMultiplier = direction() === "LONG" ? 1 : -1;
        pnl = (price - plan.entry) * positionSize() * directionMultiplier;
        percentage = ((price - plan.entry) / plan.entry) * 100 * directionMultiplier;
    }
    elements.currentPnl.textContent = formatSignedMoney(pnl);
    elements.pnlPercent.textContent = `${formatSignedNumber(percentage)}%`;
    const className = pnl < 0 ? "loss" : pnl > 0 ? "profit" : "";
    elements.currentPnl.className = className;
    elements.pnlPercent.className = className;
}

function showResult() {
    const hasFill = trade.reason !== "NO_FILL";
    const isWin = trade.reason === "TARGET";
    const title = {
        TARGET: "Take-profit reached",
        STOP: "Stop-loss reached",
        SESSION_END: "Session complete",
        NO_FILL: "Entry was not reached"
    }[trade.reason];
    const summary = {
        TARGET: "Price moved through your target and closed the simulated trade.",
        STOP: "Price moved through your stop-loss and closed the simulated trade.",
        SESSION_END: "The available candles ended, so the trade closed at the final price.",
        NO_FILL: "The replay ended before price traded through your entry level."
    }[trade.reason];
    const finalPnl = hasFill
        ? calculatePnl(trade.exitPrice)
        : 0;

    setText("#result-title", title);
    setText("#result-summary", summary);
    setText("#result-pnl", formatSignedMoney(finalPnl));
    setText("#result-direction", directionTitle());
    setText("#result-entry", formatPrice(plan.entry));
    setText("#result-exit", hasFill ? formatPrice(trade.exitPrice) : "Not filled");
    setText(
        "#result-bars",
        hasFill ? Math.max(1, trade.closedAt - trade.openedAt + 1) : "—"
    );

    const resultPnl = document.querySelector("#result-pnl");
    resultPnl.className = finalPnl < 0 ? "loss" : finalPnl > 0 ? "profit" : "";
    const icon = document.querySelector("#result-icon");
    icon.textContent = isWin ? "✓" : trade.reason === "STOP" ? "×" : "■";
    icon.className = `result-icon ${isWin ? "win" : trade.reason === "STOP" ? "loss" : ""}`;
    elements.dialog.showModal();
}

function updatePriceDisplay(candle) {
    const price = Number(candle.close);
    const change = price - initialPrice;
    const percent = (change / initialPrice) * 100;
    setText("#current-price", formatPrice(price));
    setText("#sidebar-price", formatPrice(price));
    const changeElement = document.querySelector("#current-change");
    changeElement.textContent = `${formatSignedPrice(change)} · ${formatSignedNumber(percent)}%`;
    changeElement.className = `price-change ${change < 0 ? "loss" : change > 0 ? "profit" : ""}`;
}

function updateReplayProgress(candle) {
    setText("#replay-progress", `${cursor} / ${session.candles.length} candles`);
    setText("#session-time", formatCandleTime(candle.time));
}

function setStatus(status, label) {
    elements.tradeStatus.textContent = label;
    elements.tradeStatus.className = `status-badge status-${status.toLowerCase().replace("_", "-")}`;
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

function positionSize() {
    const value = Number(elements.positionSize.value);
    return Number.isInteger(value) && value > 0 ? value : 1;
}

function replaySpeed() {
    return SPEEDS[Number(elements.speedSlider.value)];
}

function calculatePnl(exitPrice) {
    const multiplier = direction() === "LONG" ? 1 : -1;
    return (exitPrice - plan.entry) * positionSize() * multiplier;
}

function emptyPlan() {
    return {entry: null, stop: null, target: null};
}

function emptyTrade() {
    return {
        status: "PLANNING",
        openedAt: null,
        closedAt: null,
        exitPrice: null,
        reason: null
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
elements.positionSize.addEventListener("input", renderPlan);
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
