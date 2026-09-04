"use strict";

const SPEEDS = [1, 2, 5];
const BASE_DELAY_MS = 700;
const LEVEL_COLORS = {
    entry: "#3b82f6",
    stop: "#ef4444",
    target: "#22c55e"
};
const elements = {
    chart: document.querySelector("#competitive-chart"),
    play: document.querySelector("#competitive-play"),
    playIcon: document.querySelector("#competitive-play-icon"),
    playLabel: document.querySelector("#competitive-play-label"),
    speed: document.querySelector("#competitive-speed"),
    error: document.querySelector("#competitive-error"),
    claim: document.querySelector("#competitive-claim")
};

let chart;
let candleSeries;
let resizeObserver;
let session;
let replay;
let cursor = 0;
let latestCandle;
let replayTimer;
let playing = false;
let nextDepositAt;
let plan = emptyPlan();
let levelLines = {};
let activeTradeQuantity = 0;
let tradeClosed = false;

function csrfHeaders() {
    const cookie = document.cookie
        .split("; ")
        .find(value => value.startsWith("XSRF-TOKEN="));
    return cookie
        ? {"X-XSRF-TOKEN": decodeURIComponent(cookie.substring(cookie.indexOf("=") + 1))}
        : {};
}

async function jsonRequest(url, options = {}) {
    const response = await fetch(url, options);
    if (response.status === 401) {
        window.location.assign("/register.html?competitive=true");
        throw new Error("Sign in or create an account to use Competitive mode.");
    }
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "The request could not be completed.");
    }
    return body;
}

async function initialize() {
    if (typeof LightweightCharts === "undefined") {
        showError("The candlestick chart library could not load.");
        return;
    }
    try {
        const account = await jsonRequest("/api/competitive/account");
        if (account.mode !== "COMPETITIVE") {
            window.location.assign("/account.html");
            return;
        }
        renderAccount(account);
        createChart();
        const current = await jsonRequest("/api/competitive/session");
        if (current.active) {
            loadSession(current.session, true);
        } else {
            await startSession();
        }
    } catch (error) {
        showError(error.message);
    }
}

function createChart() {
    document.querySelector("#competitive-loading").remove();
    chart = LightweightCharts.createChart(elements.chart, {
        width: elements.chart.clientWidth,
        height: elements.chart.clientHeight,
        layout: {
            background: {type: "solid", color: "#0c1117"},
            textColor: "#8b98a5",
            fontFamily: "Inter, system-ui, sans-serif"
        },
        grid: {
            vertLines: {color: "#18212b"},
            horzLines: {color: "#18212b"}
        },
        crosshair: {mode: LightweightCharts.CrosshairMode.Normal},
        rightPriceScale: {borderColor: "#27313b"},
        timeScale: {
            borderColor: "#27313b",
            timeVisible: true,
            secondsVisible: false,
            rightOffset: 8,
            barSpacing: 9
        }
    });
    candleSeries = chart.addCandlestickSeries({
        upColor: "#22c55e",
        downColor: "#ef4444",
        borderVisible: false,
        wickUpColor: "#22c55e",
        wickDownColor: "#ef4444"
    });
    resizeObserver = new ResizeObserver(entries => {
        const bounds = entries[0].contentRect;
        chart.applyOptions({width: bounds.width, height: bounds.height});
    });
    resizeObserver.observe(elements.chart);
    elements.chart.addEventListener("dblclick", handleChartDoubleClick);
}

async function startSession() {
    if (activeTradeQuantity > 0) {
        showError("Finish the current trade before generating another simulation.");
        return;
    }
    pause();
    elements.error.hidden = true;
    elements.play.disabled = true;
    clearPlan();
    const symbol = document.querySelector("#competitive-instrument").value;
    const timeframe = document.querySelector("#competitive-timeframe").value;
    const query = new URLSearchParams({symbol, timeframe});
    try {
        const created = await jsonRequest(`/api/competitive/session?${query}`, {
            method: "POST",
            headers: csrfHeaders()
        });
        loadSession(created, false);
    } catch (error) {
        showError(error.message);
    }
}

function loadSession(loadedSession, resumed) {
    session = loadedSession;
    replay = session.replay;
    cursor = session.cursor;
    const visible = replay.candles.slice(0, cursor);
    latestCandle = visible.at(-1);
    candleSeries.applyOptions({
        priceFormat: {
            type: "price",
            precision: replay.pricePrecision,
            minMove: 10 ** -replay.pricePrecision
        }
    });
    candleSeries.setData(visible);
    chart.timeScale().fitContent();
    document.querySelector("#competitive-symbol").textContent = replay.symbol;
    document.querySelector("#competitive-name").textContent =
        `${replay.instrumentName} · ${replay.timeframe} · random session`;
    renderCandle(latestCandle);
    renderAccount(session.account);

    const position = session.account.positions.find(item => item.symbol === replay.symbol);
    if (session.completed) {
        tradeClosed = true;
        setInstruction("This Competitive trade is complete. Generate a new simulation.");
    } else if (position) {
        plan.entry = Number(position.averagePrice);
        activeTradeQuantity = session.entryQuantity;
        setLevel("entry", plan.entry, "ENTRY");
        renderPlan();
        setInstruction(
            "Open position restored. Set a stop below entry and a target above entry.");
    } else {
        setInstruction(resumed
            ? "Simulation restored. Double-click the chart to set an entry price."
            : "Double-click the chart to set an entry price.");
    }
    elements.play.disabled = session.completed || cursor >= session.totalCandles;
}

async function advance() {
    if (!session || cursor >= session.totalCandles) {
        pause();
        return;
    }
    try {
        const result = await jsonRequest(
            `/api/competitive/session/${session.sessionId}/advance`,
            {method: "POST", headers: csrfHeaders()});
        latestCandle = result.candle;
        cursor = result.cursor;
        candleSeries.update(latestCandle);
        renderCandle(latestCandle);
        renderAccount(result.account);
        await evaluateTradePlan(latestCandle);
        if (result.finished) {
            if (activeTradeQuantity > 0) {
                await closePlannedTrade("Session ended");
            } else if (!tradeClosed) {
                tradeClosed = true;
                setInstruction("Session ended before the entry price was reached.");
            }
            pause();
            elements.play.disabled = true;
        }
    } catch (error) {
        pause();
        showError(error.message);
    }
}

function play() {
    if (playing) {
        pause();
        return;
    }
    if (!isPlanComplete()) {
        showError("Double-click to set an entry, stop-loss, and take-profit first.");
        return;
    }
    const setupError = window.TradePlanValidation.error("LONG", plan);
    if (setupError) {
        showError(setupError);
        return;
    }
    if (tradeClosed) {
        showError("Generate a new simulation before placing another trade plan.");
        return;
    }
    elements.error.hidden = true;
    playing = true;
    elements.playIcon.textContent = "Ⅱ";
    elements.playLabel.textContent = "Pause";
    scheduleAdvance();
}

function pause() {
    playing = false;
    window.clearTimeout(replayTimer);
    elements.playIcon.textContent = "▶";
    elements.playLabel.textContent = "Play";
}

function scheduleAdvance() {
    if (!playing) {
        return;
    }
    replayTimer = window.setTimeout(async () => {
        await advance();
        scheduleAdvance();
    }, BASE_DELAY_MS / SPEEDS[Number(elements.speed.value)]);
}

function renderCandle(candle) {
    const close = Number(candle.close);
    const open = Number(candle.open);
    document.querySelector("#competitive-price").textContent = price(close);
    const change = close - open;
    const changeLabel = document.querySelector("#competitive-change");
    changeLabel.textContent = `${change >= 0 ? "+" : ""}${price(change)} this candle`;
    changeLabel.className = `price-change ${change >= 0 ? "positive" : "negative"}`;
    document.querySelector("#competitive-progress").textContent =
        `${cursor} / ${session.totalCandles} candles`;
    document.querySelector("#competitive-time").textContent =
        new Date(candle.time * 1000).toLocaleString();
}

function renderAccount(account) {
    nextDepositAt = account.nextDepositAt
        ? new Date(account.nextDepositAt).getTime()
        : undefined;
    elements.claim.disabled = !account.depositAvailable;
    document.querySelector("#competitive-mode-status").textContent = account.mode;
    document.querySelector("#competitive-equity").textContent = money(account.totalEquity);
    document.querySelector("#competitive-pnl").textContent =
        `Realized P&L ${signedMoney(account.realizedProfitLoss)}`;
    document.querySelector("#competitive-cash").textContent = money(account.cash);
    const position = account.positions.find(item => item.symbol === replay?.symbol);
    document.querySelector("#competitive-position").textContent =
        `${position?.quantity || 0} shares`;
    document.querySelector("#competitive-average").textContent =
        position ? money(position.averagePrice) : "--";
    document.querySelector("#competitive-unrealized").textContent =
        position ? signedMoney(position.unrealizedProfitLoss) : "$0.00";
    updateDepositCountdown();
}

function handleChartDoubleClick(event) {
    if (!session || playing || tradeClosed) {
        showError("Pause or start a new simulation before changing trade levels.");
        return;
    }
    const bounds = elements.chart.getBoundingClientRect();
    const clickedPrice = candleSeries.coordinateToPrice(event.clientY - bounds.top);
    if (clickedPrice == null || clickedPrice <= 0) {
        return;
    }
    const value = Number(clickedPrice.toFixed(replay.pricePrecision));
    if (plan.entry == null) {
        plan.entry = value;
        setLevel("entry", value, "ENTRY");
        setInstruction("Set a stop below entry and a target above entry.");
    } else if (value < plan.entry) {
        plan.stop = value;
        setLevel("stop", value, "STOP");
    } else if (value > plan.entry) {
        plan.target = value;
        setLevel("target", value, "TARGET");
    }
    renderPlan();
    if (isPlanComplete()) {
        setInstruction("Trade plan ready. Press Play to submit it.");
    }
}

function setLevel(type, value, title) {
    if (levelLines[type]) {
        candleSeries.removePriceLine(levelLines[type]);
    }
    levelLines[type] = candleSeries.createPriceLine({
        price: value,
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
    document.querySelector("#competitive-entry").textContent =
        plan.entry == null ? "Not set" : price(plan.entry);
    document.querySelector("#competitive-stop").textContent =
        plan.stop == null ? "Not set" : price(plan.stop);
    document.querySelector("#competitive-target").textContent =
        plan.target == null ? "Not set" : price(plan.target);
    document.querySelector("#competitive-risk-reward").textContent = isPlanComplete()
        ? `1 : ${((plan.target - plan.entry) / (plan.entry - plan.stop)).toFixed(2)}`
        : "--";
}

function clearPlan() {
    Object.values(levelLines).forEach(line => candleSeries?.removePriceLine(line));
    levelLines = {};
    plan = emptyPlan();
    activeTradeQuantity = 0;
    tradeClosed = false;
    renderPlan();
}

function emptyPlan() {
    return {entry: null, stop: null, target: null};
}

function isPlanComplete() {
    return plan.entry != null && plan.stop != null && plan.target != null;
}

async function evaluateTradePlan(candle) {
    if (tradeClosed || !isPlanComplete()) {
        return;
    }
    if (activeTradeQuantity === 0
            && Number(candle.low) <= plan.entry
            && Number(candle.high) >= plan.entry) {
        const requestedQuantity =
            Number(document.querySelector("#competitive-quantity").value);
        if (!Number.isInteger(requestedQuantity) || requestedQuantity <= 0) {
            pause();
            showError("Enter a whole-number position size greater than zero.");
            return;
        }
        const result = await executeOrder("BUY", requestedQuantity);
        activeTradeQuantity = requestedQuantity;
        renderAccount(result.account);
        setInstruction(`Bought ${activeTradeQuantity} shares. Monitoring stop and target.`);
    }
    if (activeTradeQuantity === 0) {
        return;
    }
    if (Number(candle.low) <= plan.stop) {
        await closePlannedTrade("Stop-loss reached");
    } else if (Number(candle.high) >= plan.target) {
        await closePlannedTrade("Take-profit reached");
    }
}

async function closePlannedTrade(reason) {
    const quantity = activeTradeQuantity;
    const result = await executeOrder("SELL", quantity);
    activeTradeQuantity = 0;
    tradeClosed = true;
    renderAccount(result.account);
    setInstruction(`${reason}. Realized P&L is now ${signedMoney(result.account.realizedProfitLoss)}.`);
    pause();
}

async function executeOrder(side, quantity) {
    elements.error.hidden = true;
    try {
        return await jsonRequest("/api/competitive/orders", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify({
                sessionId: session.sessionId,
                side,
                quantity
            })
        });
    } catch (error) {
        showError(error.message);
        pause();
        throw error;
    }
}

async function claimDeposit() {
    elements.claim.disabled = true;
    try {
        renderAccount(await jsonRequest("/api/competitive/deposit", {
            method: "POST",
            headers: csrfHeaders()
        }));
    } catch (error) {
        showError(error.message);
    }
}

function updateDepositCountdown() {
    if (!nextDepositAt) {
        return;
    }
    const remaining = nextDepositAt - Date.now();
    if (remaining <= 0) {
        document.querySelector("#competitive-deposit-time").textContent = "Available now";
        elements.claim.disabled = false;
        return;
    }
    const total = Math.ceil(remaining / 1000);
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor(total % 3600 / 60);
    const seconds = total % 60;
    document.querySelector("#competitive-deposit-time").textContent =
        `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function price(value) {
    return Number(value).toFixed(replay?.pricePrecision || 2);
}

function money(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(Number(value));
}

function signedMoney(value) {
    return `${Number(value) >= 0 ? "+" : ""}${money(value)}`;
}

function showError(message) {
    elements.error.textContent = message;
    elements.error.hidden = false;
}

function setInstruction(message) {
    document.querySelector("#competitive-message p").textContent = message;
}

elements.play.addEventListener("click", play);
elements.speed.addEventListener("input", () => {
    document.querySelector("#competitive-speed-label").textContent =
        `${SPEEDS[Number(elements.speed.value)]}×`;
});
document.querySelector("#new-session-button").addEventListener("click", startSession);
document.querySelector("#generate-session-button").addEventListener("click", startSession);
document.querySelector("#competitive-clear-levels").addEventListener("click", () => {
    if (!playing && activeTradeQuantity === 0) {
        clearPlan();
        setInstruction("Double-click the chart to set an entry price.");
    }
});
elements.claim.addEventListener("click", claimDeposit);

initialize();
window.setInterval(updateDepositCountdown, 1000);
